from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

import app as hub


@pytest.fixture(autouse=True)
def reset_hub_state():
    hub.missions.clear()
    with hub.telemetry._lock:
        hub.telemetry._snapshot.update(
            {
                "received_monotonic": 0.0,
                "received_at": None,
                "heartbeat_monotonic": 0.0,
                "heartbeat_at": None,
                "latitude": None,
                "longitude": None,
                "altitude_meters": None,
                "ground_speed_mps": None,
                "heading_deg": None,
                "battery_percent": None,
                "flight_mode": None,
                "armed": None,
                "current_waypoint": None,
                "last_position": None,
                "distance_m": 0.0,
            }
        )
    yield
    for mission in hub.missions.values():
        if mission.task and not mission.task.done():
            mission.task.cancel()
    hub.missions.clear()


def payload(source="SIMULATION"):
    return {
        "inspectionId": str(uuid4()),
        "fieldSessionId": str(uuid4()),
        "assetName": "Test bridge",
        "latitude": 10.8,
        "longitude": 106.7,
        "inspectionItems": ["Pier P1"],
        "telemetrySource": source,
    }


def test_create_mission_reports_simulation_source_and_no_fake_capture():
    with TestClient(hub.app) as client:
        response = client.post("/api/v1/missions", json=payload())
        assert response.status_code == 200
        data = response.json()["data"]
        assert data["telemetrySource"] == "SMARTDRONEHUB_SIMULATION"
        assert data["latitude"] == pytest.approx(10.8)


def test_live_mission_requires_fresh_mavlink_heartbeat():
    with TestClient(hub.app) as client:
        response = client.post("/api/v1/missions", json=payload("MAVLINK"))
        assert response.status_code == 503


def test_capture_endpoint_is_not_exposed_as_fake_camera():
    paths = {route.path for route in hub.app.routes}
    assert "/api/v1/missions/{inspection_id}/capture" not in paths


def test_haversine_zero_and_short_distance():
    assert hub.haversine(10, 106, 10, 106) == 0
    assert hub.haversine(10, 106, 10.00001, 106) > 1


def test_heartbeat_freshness_is_independent_of_position_packets():
    class Heartbeat:
        base_mode = 128

        @staticmethod
        def get_type():
            return "HEARTBEAT"

    hub.telemetry._apply(Heartbeat(), "GUIDED")
    snapshot = hub.telemetry.snapshot()
    assert snapshot["connected"] is True
    assert snapshot["flight_mode"] == "GUIDED"
    assert snapshot["armed"] is True


def test_missions_are_scoped_to_inspection_and_field_session():
    request = payload()
    with TestClient(hub.app) as client:
        created = client.post("/api/v1/missions", json=request)
        assert created.status_code == 200
        path = (
            f"/api/v1/missions/{request['inspectionId']}"
            f"/field-sessions/{request['fieldSessionId']}/telemetry"
        )
        assert client.get(path).status_code == 200
        other_session = str(uuid4())
        assert client.get(
            f"/api/v1/missions/{request['inspectionId']}/field-sessions/{other_session}/telemetry"
        ).status_code == 404


def test_live_mission_cannot_be_stopped_by_hub_api():
    request = payload("MAVLINK")
    hub.telemetry._apply(
        type("Heartbeat", (), {"base_mode": 0, "get_type": lambda self: "HEARTBEAT"})(),
        "AUTO",
    )
    with TestClient(hub.app) as client:
        assert client.post("/api/v1/missions", json=request).status_code == 200
        response = client.post(
            f"/api/v1/missions/{request['inspectionId']}"
            f"/field-sessions/{request['fieldSessionId']}/stop"
        )
        assert response.status_code == 409


def test_simulation_source_refuses_live_telemetry_mix():
    hub.telemetry._apply(type("Heartbeat", (), {"base_mode": 0, "get_type": lambda self: "HEARTBEAT"})(), "AUTO")
    with TestClient(hub.app) as client:
        response = client.post("/api/v1/missions", json=payload("SIMULATION"))
        assert response.status_code == 409


def test_repeat_start_for_same_session_is_idempotent():
    request = payload()
    with TestClient(hub.app) as client:
        first = client.post("/api/v1/missions", json=request)
        second = client.post("/api/v1/missions", json=request)
        assert first.status_code == second.status_code == 200
        assert second.json()["message"] == "Mission already active"


def test_second_live_mission_cannot_share_single_mavlink_stream():
    hub.telemetry._apply(
        type("Heartbeat", (), {"base_mode": 128, "get_type": lambda self: "HEARTBEAT"})(),
        "AUTO",
    )
    first = payload("MAVLINK")
    second = payload("MAVLINK")
    with TestClient(hub.app) as client:
        assert client.post("/api/v1/missions", json=first).status_code == 200
        assert client.post("/api/v1/missions", json=second).status_code == 409


def test_live_distance_is_relative_to_session_start():
    request = payload("MAVLINK")
    hub.telemetry._apply(
        type("Heartbeat", (), {"base_mode": 128, "get_type": lambda self: "HEARTBEAT"})(),
        "AUTO",
    )
    first_position = type(
        "Position",
        (),
        {
            "get_type": lambda self: "GLOBAL_POSITION_INT",
            "lat": 108000000,
            "lon": 1060000000,
            "relative_alt": 10000,
            "vx": 100,
            "vy": 0,
            "hdg": 0,
        },
    )()
    second_position = type(
        "Position",
        (),
        {
            "get_type": lambda self: "GLOBAL_POSITION_INT",
            "lat": 108000100,
            "lon": 1060000000,
            "relative_alt": 20000,
            "vx": 100,
            "vy": 0,
            "hdg": 0,
        },
    )()
    with TestClient(hub.app) as client:
        hub.telemetry._apply(first_position)
        assert client.post("/api/v1/missions", json=request).status_code == 200
        hub.telemetry._apply(second_position)
        data = client.get(
            f"/api/v1/missions/{request['inspectionId']}"
            f"/field-sessions/{request['fieldSessionId']}/telemetry"
        ).json()["data"]
        expected_distance = hub.haversine(10.8, 106, 10.80001, 106)
        assert abs(data["distanceFlownMeters"] - expected_distance) < 0.1
        assert data["altitudeMeters"] == 20.0
