# SmartDroneHub telemetry bridge

Telemetry-only companion service for ArduPilot SITL/Mission Planner. It observes MAVLink UDP telemetry or returns an explicitly labelled demo simulation. It **does not** arm, guide, upload missions to, or control aircraft. Checkpoint labels are not treated as a route. Live MAVLink remains observation-only; the operator controls the aircraft from the GCS. It does not generate camera images; upload an actual camera/simulator frame through SmartDroneInspection's authenticated evidence endpoint.

## Run locally

```sh
python -m venv .venv
. .venv/bin/activate  # Windows PowerShell: .venv\\Scripts\\Activate.ps1
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8090
```

## Mission Planner MAVLink forwarding

1. Start the backend Compose stack: `docker compose up -d --build smartdronehub backend` (PowerShell is the same command). Hub HTTP is bound to `127.0.0.1:8090`; MAVLink UDP is published at `0.0.0.0:14551` for Mission Planner forwarding.
2. In Mission Planner, connect SITL normally, then open **Ctrl+F → MAVLink** (the Advanced Tools forwarding dialog). Select UDP and configure the destination host/port as `127.0.0.1:14551`; leave any option that allows remote parameter changes/control unchecked. Mission Planner's documented forwarder generally targets one recipient, so do not replace its GCS/SITL connection—forward the existing MAVLink stream to Hub.
3. Confirm `http://localhost:8090/health` returns `mavlinkConnected: true`. A heartbeat must be fresh before the backend accepts explicit live mode. Then create an Inspector field session (with permit recorded) and choose **Connect live MAVLink** in the session panel.
4. If SITL/MP is not running, explicitly choose **Start telemetry simulation**. The demo source is labelled separately and is not a generated safe route, aircraft motion, or evidence.

If the Hub runs in Docker on the same host, forward to host loopback at port 14551 published by Compose; do not use the container IP. If MP is on a different computer, use the Docker host's LAN IP and allow inbound UDP 14551 in the host firewall. MAVLink mode is read-only here; stop/RTL/land through the operator's GCS only. Restarting the Hub clears its in-memory mission records.

### GCS setup links

- Mission Planner forwarding dialog: <https://ardupilot.org/planner/docs/common-mp-tools.html#Mavlink>
- ArduPilot SITL with modern Gazebo (optional camera simulation): <https://ardupilot.org/dev/docs/sitl-with-gazebo.html>

## API

The authenticated Spring backend exposes the browser API under the active Inspector's inspection/session scope:

- `POST /api/v1/inspections/{inspectionId}/field-sessions/{sessionId}/drone-hub/start?telemetrySource=AUTO|MAVLINK|SIMULATION`
- `GET /api/v1/inspections/{inspectionId}/field-sessions/{sessionId}/drone-hub/telemetry` while the field session is active
- `GET /api/v1/inspections/{inspectionId}/field-sessions/{sessionId}/drone-hub/flight-report` once the field session is closed

The Hub's internal companion API is not directly exposed to browsers:

- `GET /health`
- `POST /api/v1/missions`
- `GET /api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/telemetry`
- `POST /api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/stop` (stops only the Hub's demo task; it never sends aircraft commands)
- `GET /api/v1/missions/{inspectionId}/field-sessions/{fieldSessionId}/flight-report?endedAt={fieldSessionEndedAt}`

The Spring adapter supplies the persisted field-session `endedAt` when compiling its report so the Hub records the business session's actual end time. The first report read freezes the report in the Hub's in-memory store; later reads return the same snapshot.

Mission data is in-memory and resets at restart. This service is a development/demo bridge, not a safety system or operational flight controller. Live camera acquisition is out of scope without a configured Gazebo camera/image stream or real camera source.
