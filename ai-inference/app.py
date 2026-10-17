"""Advisory defect detection for SmartDroneInspection.

Serves the YOLO11s detector trained on MBDD2025 (crack, leakage, abscission,
corrosion, bulge). The Spring backend's `YoloInferenceClient` posts the raw image
bytes with the image content type and expects a JSON array of detections, so the
request body is read directly rather than as a multipart upload.
"""

import io
import json
import os

from fastapi import FastAPI, Request, Response
from PIL import Image, UnidentifiedImageError
from ultralytics import YOLO

MODEL_PATH = os.environ.get("MODEL_PATH", "/app/models/best.pt")
MODEL_NAME = os.environ.get("MODEL_NAME", "yolo11s")
MODEL_VERSION = os.environ.get("MODEL_VERSION", "mbdd2025-v1")
CONFIDENCE_THRESHOLD = float(os.environ.get("CONFIDENCE_THRESHOLD", "0.25"))
IMAGE_SIZE = int(os.environ.get("IMAGE_SIZE", "640"))

app = FastAPI(title="SmartDroneInspection advisory detection")

if not os.path.exists(MODEL_PATH):
    raise RuntimeError(
        f"Detector weights not found at {MODEL_PATH}. Put best.pt in "
        "ai-inference/models/ (see ai-inference/README.md)."
    )

model = YOLO(MODEL_PATH)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_NAME, "version": MODEL_VERSION}


@app.post("/predict")
async def predict(request: Request, response: Response):
    payload = await request.body()
    if not payload:
        response.status_code = 400
        return {"detail": "An image body is required"}

    try:
        image = Image.open(io.BytesIO(payload))
        image.load()
    except (UnidentifiedImageError, OSError):
        response.status_code = 400
        return {"detail": "The uploaded bytes are not a readable image"}

    results = model.predict(source=image, imgsz=IMAGE_SIZE, conf=CONFIDENCE_THRESHOLD, verbose=False)

    detections = []
    for result in results:
        for box in result.boxes:
            # Ultralytics returns [xMin, yMin, xMax, yMax] normalized to 0-1, which is the
            # bounding-box shape the backend and the findings UI both read.
            x_min, y_min, x_max, y_max = (float(value) for value in box.xyxyn[0].tolist())
            detections.append(
                {
                    "modelName": MODEL_NAME,
                    "modelVersion": MODEL_VERSION,
                    "predictedLabel": model.names[int(box.cls[0])],
                    "confidence": round(float(box.conf[0]), 4),
                    "boundingBox": json.dumps(
                        {
                            "xMin": round(x_min, 4),
                            "yMin": round(y_min, 4),
                            "xMax": round(x_max, 4),
                            "yMax": round(y_max, 4),
                        }
                    ),
                }
            )

    return detections
