"""Advisory defect detection for SmartDroneInspection.

Combines the self-hosted YOLO11s detector trained on MBDD2025 (crack, leakage,
abscission, corrosion, bulge) with optional Vision LLM enrichment for high-precision
structural defect detection. The Spring backend's `YoloInferenceClient` posts the raw
image bytes with the image content type and expects a JSON array of detections, so the
request body is read directly rather than as a multipart upload.
"""

import base64
import io
import json
import logging
import os
from typing import Any, Dict, List

from fastapi import FastAPI, Request, Response
from PIL import Image, UnidentifiedImageError
import requests
from ultralytics import YOLO

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai-inference")

MODEL_PATH = os.environ.get("MODEL_PATH", "/app/models/best.pt")
MODEL_NAME = os.environ.get("MODEL_NAME", "yolo11s")
MODEL_VERSION = os.environ.get("MODEL_VERSION", "mbdd2025-v1")
CONFIDENCE_THRESHOLD = float(os.environ.get("CONFIDENCE_THRESHOLD", "0.20"))
IMAGE_SIZE = int(os.environ.get("IMAGE_SIZE", "1024"))

# Multimodal Vision API Enrichment
VISION_API_ENABLED = os.environ.get("VISION_API_ENABLED", "true").lower() in ("true", "1", "yes")
VISION_API_BASE_URL = os.environ.get("VISION_API_BASE_URL", "https://9routerhelios.duckdns.org")
VISION_API_KEY = os.environ.get("VISION_API_KEY", "sk-8b37564e8a318f47-csx5f5-0b89e9a7")
VISION_API_MODEL = os.environ.get("VISION_API_MODEL", "capstone")
VISION_API_TIMEOUT = int(os.environ.get("VISION_API_TIMEOUT", "45"))

app = FastAPI(title="SmartDroneInspection advisory detection")

if not os.path.exists(MODEL_PATH):
    raise RuntimeError(
        f"Detector weights not found at {MODEL_PATH}. Put best.pt in "
        "ai-inference/models/ (see ai-inference/README.md)."
    )

model = YOLO(MODEL_PATH)


def calculate_iou(box1: List[float], box2: List[float]) -> float:
    """Calculates Intersection-over-Union for [xMin, yMin, xMax, yMax]."""
    x_left = max(box1[0], box2[0])
    y_top = max(box1[1], box2[1])
    x_right = min(box1[2], box2[2])
    y_bottom = min(box1[3], box2[3])

    if x_right <= x_left or y_bottom <= y_top:
        return 0.0

    intersection_area = (x_right - x_left) * (y_bottom - y_top)
    box1_area = (box1[2] - box1[0]) * (box1[3] - box1[1])
    box2_area = (box2[2] - box2[0]) * (box2[3] - box2[1])
    union_area = box1_area + box2_area - intersection_area
    if union_area <= 0:
        return 0.0
    return intersection_area / union_area


def query_vision_enrichment(payload: bytes) -> List[Dict[str, Any]]:
    """Queries OpenAI-compatible Vision API for complementary high-fidelity defect detections."""
    if not VISION_API_ENABLED or not VISION_API_BASE_URL or not VISION_API_KEY:
        return []

    try:
        b64_image = base64.b64encode(payload).decode("utf-8")
        url = f"{VISION_API_BASE_URL.rstrip('/')}/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {VISION_API_KEY.strip()}",
            "Content-Type": "application/json",
        }
        body = {
            "model": VISION_API_MODEL,
            "stream": False,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "You inspect infrastructure inspection photographs for visible defects.\n"
                        "Reply with one JSON object and nothing else:\n"
                        '{"detections":[{"predictedLabel":"crack | leakage | abscission | corrosion | bulge",'
                        '"confidence":0.85,"boundingBox":{"xMin":0.0,"yMin":0.0,"xMax":1.0,"yMax":1.0}}]}\n'
                        "Box values are normalized 0 to 1. Identify all cracks, concrete spalling/abscission, "
                        "corrosion, water leakage, or crumbling materials. An empty array means no defect."
                    ),
                },
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "List the visible defects in this photograph."},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_image}"}},
                    ],
                },
            ],
        }

        response = requests.post(url, headers=headers, json=body, timeout=VISION_API_TIMEOUT)
        if response.status_code != 200:
            logger.warning("Vision API returned status %s: %s", response.status_code, response.text[:200])
            return []

        raw_content = response.json().get("choices", [{}])[0].get("message", {}).get("content", "")
        # Strip potential markdown json codeblock wrapping
        if "```json" in raw_content:
            raw_content = raw_content.split("```json")[1].split("```")[0].strip()
        elif "```" in raw_content:
            raw_content = raw_content.split("```")[1].split("```")[0].strip()

        parsed = json.loads(raw_content)
        detections = []
        for d in parsed.get("detections", []):
            label = str(d.get("predictedLabel", "crack")).lower().strip()
            conf = float(d.get("confidence", 0.9))
            bb = d.get("boundingBox", {})
            box = [
                max(0.0, min(1.0, float(bb.get("xMin", 0.0)))),
                max(0.0, min(1.0, float(bb.get("yMin", 0.0)))),
                max(0.0, min(1.0, float(bb.get("xMax", 1.0)))),
                max(0.0, min(1.0, float(bb.get("yMax", 1.0)))),
            ]
            detections.append(
                {
                    "modelName": f"{MODEL_NAME}+{VISION_API_MODEL}",
                    "modelVersion": MODEL_VERSION,
                    "predictedLabel": label,
                    "confidence": round(conf, 4),
                    "box_raw": box,
                    "boundingBox": json.dumps(
                        {
                            "xMin": round(box[0], 4),
                            "yMin": round(box[1], 4),
                            "xMax": round(box[2], 4),
                            "yMax": round(box[3], 4),
                        }
                    ),
                }
            )
        return detections
    except Exception as e:
        logger.warning("Vision API call failed, falling back to YOLO only: %s", e)
        return []


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "version": MODEL_VERSION,
        "visionEnrichment": VISION_API_ENABLED,
    }


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

    # 1. Base YOLO11s detection
    yolo_results = model.predict(source=image, imgsz=IMAGE_SIZE, conf=CONFIDENCE_THRESHOLD, verbose=False)

    detections = []
    for result in yolo_results:
        for box in result.boxes:
            x_min, y_min, x_max, y_max = (float(value) for value in box.xyxyn[0].tolist())
            detections.append(
                {
                    "modelName": MODEL_NAME,
                    "modelVersion": MODEL_VERSION,
                    "predictedLabel": model.names[int(box.cls[0])],
                    "confidence": round(float(box.conf[0]), 4),
                    "box_raw": [x_min, y_min, x_max, y_max],
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

    # 2. Vision API enrichment (detecting cracks and spalls missed by small vision filters)
    vision_detections = query_vision_enrichment(payload)

    # 3. Intelligent IoU merge
    final_detections = list(detections)
    for v_det in vision_detections:
        v_box = v_det["box_raw"]
        matched_idx = -1
        max_iou = 0.0

        for idx, existing in enumerate(final_detections):
            iou = calculate_iou(v_box, existing["box_raw"])
            if iou > max_iou:
                max_iou = iou
                matched_idx = idx

        if max_iou >= 0.25 and matched_idx >= 0:
            # Overlapping defect: Vision model refines semantic label and confidence
            curr = final_detections[matched_idx]
            if v_det["confidence"] >= curr["confidence"]:
                final_detections[matched_idx] = v_det
        else:
            # New defect discovered by Vision that YOLO missed
            final_detections.append(v_det)

    # Clean up internal box_raw field before returning to Spring backend
    for item in final_detections:
        item.pop("box_raw", None)

    return final_detections
