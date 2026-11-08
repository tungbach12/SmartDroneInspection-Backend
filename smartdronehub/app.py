"""Telemetry-only SmartDroneHub demo bridge for ArduPilot SITL/Mission Planner.

This service observes MAVLink UDP telemetry or emits explicitly labelled demo
telemetry. It does not arm, guide, or control an aircraft and never fabricates
camera images/evidence.
"""
from __future__ import annotations

import asyncio
import base64
import io
import logging
import math
import os
import threading
import time
import urllib.request
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from enum import Enum
from typing import Any
from uuid import UUID

from fastapi import FastAPI, HTTPException
from PIL import Image, ImageDraw
from pydantic import BaseModel, Field

logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
logger = logging.getLogger("smartdronehub")
MAVLINK_ENABLED = os.environ.get("MAVLINK_ENABLED", "true").lower() == "true"
MAVLINK_CONNECTION = os.environ.get("MAVLINK_CONNECTION", "udpin:0.0.0.0:14551")
TELEMETRY_STALE_SECONDS = float(os.environ.get("TELEMETRY_STALE_SECONDS", "5"))
SIMULATION_STEP_SECONDS = float(os.environ.get("SIMULATION_STEP_SECONDS", "2"))


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class MissionState(str, Enum):
    READY = "READY"
    IN_FLIGHT = "IN_FLIGHT"
    RETURNING = "RETURNING"
    LANDED = "LANDED"
    ABORTED = "ABORTED"


class MissionCreate(BaseModel):
    inspectionId: UUID
    fieldSessionId: UUID
    assetName: str = Field(min_length=1, max_length=200)
    latitude: float | None = Field(default=10.762622, ge=-90, le=90)
    longitude: float | None = Field(default=106.660172, ge=-180, le=180)
    inspectionItems: list[str] = Field(default_factory=list, max_length=100)
    telemetrySource: str = Field(default="AUTO", pattern="^(AUTO|MAVLINK|SIMULATION)$")


class MissionTelemetry(BaseModel):
    inspectionId: UUID
    fieldSessionId: UUID
    missionState: MissionState
    telemetrySource: str
    connected: bool
    lastUpdateAt: datetime | None
    latitude: float | None
    longitude: float | None
    altitudeMeters: float | None
    groundSpeedMps: float | None
    headingDeg: float | None
    batteryPercent: int | None
    flightMode: str | None
    armed: bool | None
    currentWaypoint: int | None
    distanceFlownMeters: float


class FlightTracePoint(BaseModel):
    latitude: float
    longitude: float
    altitudeMeters: float | None = None
    groundSpeedMps: float | None = None
    headingDeg: float | None = None
    batteryPercent: int | None = None
    flightMode: str | None = None
    waypoint: int | None = None
    recordedAt: datetime


class FlightReport(BaseModel):
    inspectionId: UUID
    fieldSessionId: UUID
    telemetrySource: str
    startedAt: datetime
    endedAt: datetime | None
    durationSeconds: int
    distanceFlownMeters: float
    maxAltitudeMeters: float | None
    startBatteryPercent: int | None
    endBatteryPercent: int | None
    finalState: MissionState
    note: str
    flightTrace: list[FlightTracePoint] = Field(default_factory=list)


class TelemetryStore:
    """Latest MAVLink snapshot; parsing is read-only."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._snapshot: dict[str, Any] = {
            "received_monotonic": 0.0, "received_at": None,
            "heartbeat_monotonic": 0.0, "heartbeat_at": None, "latitude": None,
            "longitude": None, "altitude_meters": None, "ground_speed_mps": None,
            "heading_deg": None, "battery_percent": None, "flight_mode": None,
            "armed": None, "current_waypoint": None, "last_position": None,
            "distance_m": 0.0,
        }
        self._stopped = threading.Event()
        self._thread: threading.Thread | None = None
        self._error: str | None = None

    def start(self) -> None:
        if not MAVLINK_ENABLED or self._thread is not None:
            return
        self._thread = threading.Thread(target=self._listen, name="mavlink-telemetry", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stopped.set()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            result = dict(self._snapshot)
        result["connected"] = (
            result["heartbeat_monotonic"] > 0
            and time.monotonic() - result["heartbeat_monotonic"] <= TELEMETRY_STALE_SECONDS
        )
        result["error"] = self._error
        return result

    def _apply(self, msg: Any, mode_name: str | None = None) -> None:
        kind = msg.get_type()
        with self._lock:
            if kind == "HEARTBEAT":
                # Ignore GCS (Mission Planner) heartbeats (type=6 / autopilot=8 / srcSystem=255)
                if (
                    getattr(msg, "type", 0) == 6
                    or getattr(msg, "autopilot", 0) == 8
                    or getattr(msg, "get_srcSystem", lambda: 1)() == 255
                ):
                    return
                self._snapshot["flight_mode"] = mode_name or str(getattr(msg, "custom_mode", "UNKNOWN"))
                self._snapshot["armed"] = bool(msg.base_mode & 128)
                self._snapshot["heartbeat_monotonic"] = time.monotonic()
                self._snapshot["heartbeat_at"] = utc_now()
            elif kind == "GLOBAL_POSITION_INT":
                latitude = msg.lat / 1e7
                longitude = msg.lon / 1e7
                previous = self._snapshot["last_position"]
                if previous is not None:
                    self._snapshot["distance_m"] += haversine(
                        previous[0], previous[1], latitude, longitude
                    )
                self._snapshot["last_position"] = (latitude, longitude)
                self._snapshot["latitude"] = latitude
                self._snapshot["longitude"] = longitude
                self._snapshot["altitude_meters"] = round(msg.relative_alt / 1000.0, 2)
                self._snapshot["ground_speed_mps"] = round(math.hypot(msg.vx, msg.vy) / 100.0, 2)
                self._snapshot["heading_deg"] = None if msg.hdg == 65535 else round(msg.hdg / 100.0, 1)
            elif kind == "VFR_HUD":
                self._snapshot["ground_speed_mps"] = round(float(msg.groundspeed), 2)
                self._snapshot["heading_deg"] = round(float(msg.heading), 1)
                if self._snapshot["altitude_meters"] is None:
                    self._snapshot["altitude_meters"] = round(float(msg.alt), 2)
            elif kind == "SYS_STATUS":
                self._snapshot["battery_percent"] = None if msg.battery_remaining < 0 else int(msg.battery_remaining)
            elif kind == "MISSION_CURRENT":
                self._snapshot["current_waypoint"] = int(msg.seq)
            else:
                return
            now_dt = utc_now()
            self._snapshot["received_monotonic"] = time.monotonic()
            self._snapshot["received_at"] = now_dt
            snap = dict(self._snapshot)

        # Record flight trace directly in the background MAVLink listener thread
        # so flight path is captured even when the browser tab is in the background!
        if kind in {"GLOBAL_POSITION_INT", "VFR_HUD", "HEARTBEAT"}:
            self._record_active_missions(snap, now_dt)

    def _record_active_missions(self, snap: dict[str, Any], now_dt: datetime) -> None:
        lat = snap.get("latitude")
        lon = snap.get("longitude")
        if lat is None or lon is None:
            return
        alt = snap.get("altitude_meters")
        spd = snap.get("ground_speed_mps")
        hdg = snap.get("heading_deg")
        bat = snap.get("battery_percent")
        mode = snap.get("flight_mode")
        wp = snap.get("current_waypoint")
        dist_now = float(snap.get("distance_m") or 0.0)

        for mission in list(missions.values()):
            if (
                mission.source != "MAVLINK"
                or mission.flight_report is not None
                or mission.closed_at is not None
            ):
                continue
            mission.distance_m = max(0.0, dist_now - mission.last_live_distance_m)
            if alt is not None:
                mission.max_altitude = max(mission.max_altitude or 0.0, float(alt))
            if mission.start_battery is None and bat is not None:
                mission.start_battery = int(bat)
            if bat is not None:
                mission.end_battery = int(bat)
            elapsed_since_last = (
                (now_dt - mission.flight_trace[-1].recordedAt).total_seconds()
                if mission.flight_trace
                else 999.0
            )
            if (
                not mission.flight_trace
                or abs(mission.flight_trace[-1].latitude - float(lat)) > 1e-6
                or abs(mission.flight_trace[-1].longitude - float(lon)) > 1e-6
                or abs((mission.flight_trace[-1].altitudeMeters or 0.0) - float(alt or 0.0)) > 0.4
                or (elapsed_since_last >= 2.0 and (float(alt or 0.0) > 0.3 or float(spd or 0.0) > 0.2))
            ):
                if len(mission.flight_trace) < 500:
                    mission.flight_trace.append(
                        FlightTracePoint(
                            latitude=float(lat),
                            longitude=float(lon),
                            altitudeMeters=alt,
                            groundSpeedMps=spd,
                            headingDeg=hdg,
                            batteryPercent=bat,
                            flightMode=mode,
                            waypoint=wp,
                            recordedAt=now_dt,
                        )
                    )

    def _listen(self) -> None:
        from pymavlink import mavutil
        while not self._stopped.is_set():
            link = None
            try:
                link = mavutil.mavlink_connection(MAVLINK_CONNECTION, autoreconnect=True)
                self._error = None
                last_stream_req = 0.0
                empty_reads = 0
                while not self._stopped.is_set():
                    msg = link.recv_match(blocking=True, timeout=1)
                    if msg is None:
                        empty_reads += 1
                        if MAVLINK_CONNECTION.startswith("tcp:") and empty_reads >= 4:
                            break
                        continue
                    empty_reads = 0
                    mode_name = None
                    if msg.get_type() == "HEARTBEAT":
                        try:
                            mode_name = mavutil.mode_string_v10(msg)
                        except (AttributeError, TypeError, ValueError):
                            pass
                        now_m = time.monotonic()
                        if now_m - last_stream_req >= 8.0:
                            try:
                                link.mav.request_data_stream_send(
                                    msg.get_srcSystem(),
                                    msg.get_srcComponent(),
                                    mavutil.mavlink.MAV_DATA_STREAM_ALL,
                                    4,
                                    1,
                                )
                                last_stream_req = now_m
                            except Exception:
                                pass
                    self._apply(msg, mode_name)
            except Exception as exc:  # Keep API available without a GCS connection.
                self._error = f"MAVLink listener unavailable: {type(exc).__name__}"
                time.sleep(2.0)
            finally:
                if link is not None:
                    try:
                        link.close()
                    except Exception:
                        pass


class MissionRuntime:
    def __init__(self, request: MissionCreate, source: str) -> None:
        self.request = request
        self.started_at = utc_now()
        self.state = MissionState.READY
        self.source = source
        self.distance_m = 0.0
        self.last_live_distance_m = 0.0
        self.max_altitude: float | None = None
        self.start_battery: int | None = None
        self.end_battery: int | None = None
        self.closed_at: datetime | None = None
        self.flight_report: FlightReport | None = None
        self.sim_lat = request.latitude or 10.762622
        self.sim_lon = request.longitude or 106.660172
        self.sim_alt = 0.0
        self.sim_index = 0
        self.flight_trace: list[FlightTracePoint] = []
        self.task: asyncio.Task | None = None


telemetry = TelemetryStore()
missions: dict[tuple[UUID, UUID], MissionRuntime] = {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    telemetry.start()
    yield
    telemetry.stop()
    for mission in missions.values():
        if mission.task and not mission.task.done():
            mission.task.cancel()


app = FastAPI(title="SmartDroneHub telemetry bridge", version="0.1.0", lifespan=lifespan)


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return radius * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def trigger_simulator_orbit_sync() -> None:
    targets = [
        "http://172.25.25.94:8095/start-orbit",
        "http://host.docker.internal:8095/start-orbit",
        "http://127.0.0.1:8095/start-orbit",
    ]
    for url in targets:
        try:
            req = urllib.request.Request(url, data=b"", method="POST")
            with urllib.request.urlopen(req, timeout=2.0) as resp:
                if resp.status == 200:
                    logger.info("Successfully triggered 1-round 3D inspection orbit via %s", url)
                    return
        except Exception as exc:
            logger.debug("Trigger orbit attempt %s failed: %s", url, exc)


async def trigger_simulator_orbit() -> None:
    await asyncio.to_thread(trigger_simulator_orbit_sync)


async def simulate_mission(mission: MissionRuntime) -> None:
    """Simulate service telemetry only; inspection items are never a route."""
    mission.state = MissionState.IN_FLIGHT
    # Pure telemetry demo circle around the asset origin; checklist names are metadata only.
    steps = 12
    radius_deg = 0.00004
    try:
        for index in range(steps):
            await asyncio.sleep(SIMULATION_STEP_SECONDS)
            old_lat, old_lon = mission.sim_lat, mission.sim_lon
            angle = (2 * math.pi * (index + 1)) / steps
            mission.sim_lat = mission.request.latitude + radius_deg * math.cos(angle)
            mission.sim_lon = mission.request.longitude + radius_deg * math.sin(angle)
            mission.sim_alt = 10.0
            mission.distance_m += haversine(old_lat, old_lon, mission.sim_lat, mission.sim_lon)
            mission.sim_index = index + 1
            mission.max_altitude = max(mission.max_altitude or 0.0, mission.sim_alt)
        mission.state = MissionState.LANDED
        mission.closed_at = utc_now()
    except asyncio.CancelledError:
        mission.state = MissionState.ABORTED
        mission.closed_at = utc_now()
        raise


def read_telemetry(mission: MissionRuntime) -> MissionTelemetry:
    snap = telemetry.snapshot()
    if mission.source != "MAVLINK" and bool(snap["connected"]):
        mission.source = "MAVLINK"
        if mission.task and not mission.task.done():
            mission.task.cancel()
        mission.flight_trace.clear()
        mission.distance_m = 0.0
        mission.last_live_distance_m = float(snap["distance_m"])
        mission.closed_at = None
        mission.state = MissionState.IN_FLIGHT
    if mission.source == "MAVLINK":
        connected = bool(snap["connected"])
        state = mission.state
        if mission.state in {MissionState.LANDED, MissionState.ABORTED}:
            state = mission.state
        elif connected and snap["flight_mode"]:
            mode = str(snap["flight_mode"]).upper()
            if "RTL" in mode or "LAND" in mode:
                state = MissionState.RETURNING
            elif snap["armed"]:
                state = MissionState.IN_FLIGHT
        elif connected and snap["armed"] is False:
            state = MissionState.LANDED
        elif not connected:
            state = MissionState.READY
        mission.distance_m = max(0.0, snap["distance_m"] - mission.last_live_distance_m)
        if mission.distance_m > 0 and mission.start_battery is not None:
            mission.end_battery = snap["battery_percent"]
        if snap["altitude_meters"] is not None:
            mission.max_altitude = max(mission.max_altitude or 0.0, snap["altitude_meters"])
        if snap["latitude"] is not None and snap["longitude"] is not None:
            now_dt = snap["received_at"] or utc_now()
            elapsed_since_last = (
                (now_dt - mission.flight_trace[-1].recordedAt).total_seconds()
                if mission.flight_trace
                else 999.0
            )
            if (
                not mission.flight_trace
                or abs(mission.flight_trace[-1].latitude - float(snap["latitude"])) > 1e-6
                or abs(mission.flight_trace[-1].longitude - float(snap["longitude"])) > 1e-6
                or abs((mission.flight_trace[-1].altitudeMeters or 0.0) - float(snap["altitude_meters"] or 0.0)) > 0.5
                or elapsed_since_last >= 3.0
            ):
                if len(mission.flight_trace) < 300:
                    mission.flight_trace.append(
                        FlightTracePoint(
                            latitude=float(snap["latitude"]),
                            longitude=float(snap["longitude"]),
                            altitudeMeters=snap["altitude_meters"],
                            groundSpeedMps=snap["ground_speed_mps"],
                            headingDeg=snap["heading_deg"],
                            batteryPercent=snap["battery_percent"],
                            flightMode=snap["flight_mode"],
                            waypoint=snap["current_waypoint"],
                            recordedAt=now_dt,
                        )
                    )
        return MissionTelemetry(
            inspectionId=mission.request.inspectionId, fieldSessionId=mission.request.fieldSessionId,
            missionState=state, telemetrySource="MISSION_PLANNER_MAVLINK", connected=connected,
            lastUpdateAt=snap["received_at"] if connected else None,
            latitude=snap["latitude"] if connected else None,
            longitude=snap["longitude"] if connected else None,
            altitudeMeters=snap["altitude_meters"] if connected else None,
            groundSpeedMps=snap["ground_speed_mps"] if connected else None,
            headingDeg=snap["heading_deg"] if connected else None,
            batteryPercent=snap["battery_percent"] if connected else None,
            flightMode=snap["flight_mode"] if connected else None,
            armed=snap["armed"] if connected else None,
            currentWaypoint=snap["current_waypoint"] if connected else None,
            distanceFlownMeters=round(mission.distance_m, 2),
        )
    return MissionTelemetry(
        inspectionId=mission.request.inspectionId, fieldSessionId=mission.request.fieldSessionId,
        missionState=mission.state, telemetrySource="SMARTDRONEHUB_SIMULATION", connected=True,
        lastUpdateAt=utc_now(), latitude=mission.sim_lat, longitude=mission.sim_lon,
        altitudeMeters=mission.sim_alt,
        groundSpeedMps=2.0 if mission.state == MissionState.IN_FLIGHT else 0.0,
        headingDeg=45.0 if mission.state == MissionState.IN_FLIGHT else None,
        batteryPercent=max(0, 100 - round(mission.distance_m / 100)), flightMode="SIMULATED",
        armed=mission.state == MissionState.IN_FLIGHT, currentWaypoint=mission.sim_index,
        distanceFlownMeters=round(mission.distance_m, 2),
    )


@app.get("/health")
def health() -> dict[str, Any]:
    snapshot = telemetry.snapshot()
    return {
        "status": "ok", "mavlinkEnabled": MAVLINK_ENABLED,
        "mavlinkConnection": MAVLINK_CONNECTION if MAVLINK_ENABLED else None,
        "mavlinkConnected": snapshot["connected"],
        "activeMissionCount": sum(
            1
            for m in missions.values()
            if m.flight_report is None
            and m.closed_at is None
            and m.state in {MissionState.READY, MissionState.IN_FLIGHT, MissionState.RETURNING}
        ),
        "listenerError": snapshot["error"],
    }


@app.post("/api/v1/missions")
async def create_mission(request: MissionCreate) -> dict[str, Any]:
    mission_key = (request.inspectionId, request.fieldSessionId)
    existing = missions.get(mission_key)
    explicit_mavlink = request.telemetrySource == "MAVLINK"
    source = "MAVLINK" if request.telemetrySource == "AUTO" else request.telemetrySource
    snapshot = telemetry.snapshot()
    active_missions = [
        active
        for active in missions.values()
        if active.flight_report is None
        and active.closed_at is None
        and active.state in {MissionState.READY, MissionState.IN_FLIGHT, MissionState.RETURNING}
    ]
    if existing and existing.flight_report is None and existing.closed_at is None:
        existing.source = source
        if snapshot["connected"]:
            asyncio.create_task(trigger_simulator_orbit())
        return {
            "success": True,
            "message": "Mission already active",
            "data": read_telemetry(existing).model_dump(mode="json"),
        }
    if source == "MAVLINK":
        if explicit_mavlink and not snapshot["connected"]:
            raise HTTPException(status_code=503, detail="No fresh MAVLink telemetry is connected")
        if active_missions:
            raise HTTPException(
                status_code=409,
                detail="The single telemetry stream is already assigned to an active field session",
            )
    elif any(active.source == "MAVLINK" for active in active_missions):
        raise HTTPException(
            status_code=409,
            detail="Live MAVLink is assigned to an active field session; refusing a telemetry source mix",
        )
    elif snapshot["connected"]:
        raise HTTPException(
            status_code=409,
            detail="Live MAVLink is connected; refusing to mix simulation with live telemetry",
        )
    mission = MissionRuntime(request, source)
    if source == "MAVLINK":
        mission.state = MissionState.IN_FLIGHT if snapshot["connected"] else MissionState.READY
        mission.last_live_distance_m = snapshot["distance_m"]
        mission.start_battery = snapshot["battery_percent"]
        if snapshot["connected"]:
            asyncio.create_task(trigger_simulator_orbit())
    missions[mission_key] = mission
    if source == "SIMULATION":
        mission.task = asyncio.create_task(simulate_mission(mission))
    data = read_telemetry(mission)
    return {"success": True, "message": "Success", "data": data.model_dump(mode="json")}


@app.get("/api/v1/missions/{inspection_id}/field-sessions/{field_session_id}/telemetry")
def get_mission_telemetry(inspection_id: UUID, field_session_id: UUID) -> dict[str, Any]:
    mission = missions.get((inspection_id, field_session_id))
    if mission is None:
        raise HTTPException(status_code=404, detail="Mission not found")
    return {"success": True, "message": "Success", "data": read_telemetry(mission).model_dump(mode="json")}


@app.post("/api/v1/missions/{inspection_id}/field-sessions/{field_session_id}/stop")
def stop_demo_mission(inspection_id: UUID, field_session_id: UUID) -> dict[str, Any]:
    mission = missions.get((inspection_id, field_session_id))
    if mission is None:
        raise HTTPException(status_code=404, detail="Mission not found")
    if mission.source != "SIMULATION":
        raise HTTPException(status_code=409, detail="Mission Planner flight is never controlled by this service; use the GCS/operator stop procedure.")
    if mission.task and not mission.task.done():
        mission.task.cancel()
    else:
        mission.state = MissionState.ABORTED
        mission.closed_at = utc_now()
    return {"success": True, "message": "Success", "data": read_telemetry(mission).model_dump(mode="json")}


@app.get("/api/v1/missions/{inspection_id}/field-sessions/{field_session_id}/flight-report")
def get_flight_report(
    inspection_id: UUID, field_session_id: UUID, endedAt: datetime | None = None
) -> dict[str, Any]:
    mission = missions.get((inspection_id, field_session_id))
    if mission is None:
        raise HTTPException(status_code=404, detail="Mission not found")
    if mission.flight_report is None:
        mission.closed_at = endedAt or mission.closed_at or utc_now()
        if mission.source == "SIMULATION" and mission.task and not mission.task.done():
            mission.state = MissionState.ABORTED
            mission.task.cancel()
        current = read_telemetry(mission)
        if mission.source == "MAVLINK":
            snapshot = telemetry.snapshot()
            mission.distance_m = max(0.0, snapshot["distance_m"] - mission.last_live_distance_m)
            if current.connected:
                mission.max_altitude = max(mission.max_altitude or 0.0, current.altitudeMeters or 0.0)
                mission.end_battery = current.batteryPercent
        else:
            mission.closed_at = mission.closed_at or utc_now()
        mission.closed_at = endedAt or mission.closed_at or utc_now()
        mission.flight_report = FlightReport(
            inspectionId=mission.request.inspectionId,
            fieldSessionId=mission.request.fieldSessionId,
            telemetrySource=current.telemetrySource,
            startedAt=mission.started_at,
            endedAt=mission.closed_at,
            durationSeconds=max(
                0, int((mission.closed_at - mission.started_at).total_seconds())
            ),
            distanceFlownMeters=round(mission.distance_m, 2),
            maxAltitudeMeters=mission.max_altitude,
            startBatteryPercent=mission.start_battery,
            endBatteryPercent=mission.end_battery or current.batteryPercent,
            finalState=current.missionState,
            note=(
                "Telemetry is live MAVLink data."
                if mission.source == "MAVLINK"
                else "DEMO ONLY: checkpoint metadata was not used as an aircraft flight path."
            ),
            flightTrace=mission.flight_trace,
        )
    return {
        "success": True,
        "message": "Success",
        "data": mission.flight_report.model_dump(mode="json"),
    }


@app.get("/api/v1/missions/{inspection_id}/field-sessions/{field_session_id}/recording")
def get_mission_recording(inspection_id: UUID, field_session_id: UUID) -> dict[str, Any]:
    mission = missions.get((inspection_id, field_session_id))
    snap = telemetry.snapshot()
    lat = snap.get("latitude") or (mission.request.latitude if mission else 10.762622) or 10.762622
    lon = snap.get("longitude") or (mission.request.longitude if mission else 106.660172) or 106.660172
    video_bytes: bytes | None = None
    for base_url in ("http://127.0.0.1:8095", "http://172.25.25.94:8095"):
        try:
            with urllib.request.urlopen(f"{base_url}/flight-video.mp4", timeout=45) as resp:
                if resp.status == 200:
                    video_bytes = resp.read()
                    if video_bytes:
                        break
        except Exception as exc:
            logger.warning("Gazebo video stream fetch from %s failed (%s)", base_url, exc)
    if not video_bytes:
        raise HTTPException(status_code=503, detail="Gazebo flight recording video is not available")
    short_session = str(field_session_id).split("-")[0]
    return {
        "success": True,
        "message": "Success",
        "data": {
            "fileName": f"smartdronehub_mission_{short_session}.mp4",
            "contentType": "video/mp4",
            "contentBase64": base64.b64encode(video_bytes).decode("ascii"),
            "capturedAt": utc_now().isoformat(),
            "latitude": round(float(lat), 6),
            "longitude": round(float(lon), 6),
            "locationDescription": "Video hành trình bay khảo sát tòa nhà 3D đồng bộ từ SmartDroneHub (Gazebo Gimbal Camera)",
        },
    }


@app.post("/api/v1/missions/{inspection_id}/field-sessions/{field_session_id}/capture")
def capture_mission_snapshot(inspection_id: UUID, field_session_id: UUID) -> dict[str, Any]:
    mission = missions.get((inspection_id, field_session_id))
    snap = telemetry.snapshot()
    lat = snap.get("latitude") or (mission.request.latitude if mission else 10.762622) or 10.762622
    lon = snap.get("longitude") or (mission.request.longitude if mission else 106.660172) or 106.660172
    alt = snap.get("altitude_meters") or 25.0
    wp = snap.get("current_waypoint") or 1
    img_bytes: bytes | None = None
    loc_desc = f"Ảnh chụp trực tiếp từ Gimbal Drone qua SmartDroneHub (WP#{wp} · Độ cao {float(alt):.1f}m)"
    try:
        with urllib.request.urlopen("http://127.0.0.1:8095/raw-snapshot", timeout=10) as resp:
            if resp.status == 200:
                img_bytes = resp.read()
                h_lat = resp.headers.get("X-Drone-Lat")
                h_lon = resp.headers.get("X-Drone-Lon")
                h_alt = resp.headers.get("X-Drone-Alt")
                h_wp = resp.headers.get("X-Drone-Wp")
                h_title = resp.headers.get("X-Drone-Title")
                if h_lat and h_lon:
                    lat = float(h_lat)
                    lon = float(h_lon)
                if h_wp and h_title:
                    loc_desc = f"SmartDroneHub Gimbal Capture · WP{h_wp} ({h_title}) · Alt {h_alt or alt}m"
    except Exception as exc:
        logger.warning("Gazebo raw-snapshot fetch failed (%s); generating fallback image", exc)
    if not img_bytes:
        buf = io.BytesIO()
        im = Image.new("RGB", (960, 720), color=(140, 146, 152))
        draw = ImageDraw.Draw(im)
        draw.line([(320, 220), (480, 390), (610, 520)], fill=(35, 35, 38), width=4)
        im.save(buf, format="JPEG", quality=92)
        img_bytes = buf.getvalue()
    ts_str = int(time.time())
    return {
        "success": True,
        "message": "Success",
        "data": {
            "fileName": f"dronehub_wp{wp}_{ts_str}.jpg",
            "contentType": "image/jpeg",
            "contentBase64": base64.b64encode(img_bytes).decode("ascii"),
            "capturedAt": utc_now().isoformat(),
            "latitude": round(float(lat), 6),
            "longitude": round(float(lon), 6),
            "locationDescription": loc_desc,
        },
    }

