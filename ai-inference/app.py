"""Advisory defect detection for SmartDroneInspection.

Serves the YOLO11s detector trained on MBDD2025 (crack, leakage, abscission,
corrosion, bulge). The Spring backend's `YoloInferenceClient` posts the raw image
bytes with the image content type and expects a JSON array of detections, so the
request body is read directly rather than as a multipart upload.
"""

import io
import json
import logging
import os
import threading

from fastapi import FastAPI, Request, Response
from fastembed import TextEmbedding
from PIL import Image, UnidentifiedImageError
from ultralytics import YOLO

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai-inference")

MODEL_PATH = os.environ.get("MODEL_PATH", "/app/models/best.pt")
MODEL_NAME = os.environ.get("MODEL_NAME", "yolo11s")
MODEL_VERSION = os.environ.get("MODEL_VERSION", "mbdd2025-v1")
CONFIDENCE_THRESHOLD = float(os.environ.get("CONFIDENCE_THRESHOLD", "0.20"))
IMAGE_SIZE = int(os.environ.get("IMAGE_SIZE", "1024"))
EMBEDDING_MODEL = os.environ.get(
    "EMBEDDING_MODEL", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
)
FASTEMBED_CACHE_PATH = os.environ.get("FASTEMBED_CACHE_PATH", "/app/cache/fastembed")

app = FastAPI(title="SmartDroneInspection advisory detection")

if not os.path.exists(MODEL_PATH):
    logger.warning("Model weights not found at %s. Ensure weights are mounted.", MODEL_PATH)

model = YOLO(MODEL_PATH)
_embed_lock = threading.Lock()
_embed_model = None


def get_embedding_model() -> TextEmbedding:
    global _embed_model
    if _embed_model is None:
        with _embed_lock:
            if _embed_model is None:
                logger.info("Loading local embedding model %s", EMBEDDING_MODEL)
                _embed_model = TextEmbedding(
                    model_name=EMBEDDING_MODEL,
                    cache_dir=FASTEMBED_CACHE_PATH,
                )
    return _embed_model


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "version": MODEL_VERSION,
        "embeddingModel": EMBEDDING_MODEL,
    }


@app.post("/v1/embeddings")
async def create_embeddings(request: Request, response: Response):
    """OpenAI-compatible local text embeddings for DroneKnowledgeAI (pgvector)."""
    try:
        payload = await request.json()
    except Exception:
        response.status_code = 400
        return {"detail": "Invalid JSON body"}

    raw_input = payload.get("input") if isinstance(payload, dict) else None
    if isinstance(raw_input, str):
        texts = [raw_input]
    elif isinstance(raw_input, list):
        texts = [str(item) for item in raw_input if item is not None]
    else:
        texts = []

    texts = [text.strip() for text in texts if text and text.strip()]
    if not texts:
        response.status_code = 400
        return {"detail": "input is required"}

    vectors = list(get_embedding_model().embed(texts))
    return {
        "object": "list",
        "model": EMBEDDING_MODEL,
        "data": [
            {
                "object": "embedding",
                "index": index,
                "embedding": [round(float(value), 6) for value in vector.tolist()],
            }
            for index, vector in enumerate(vectors)
        ],
    }


@app.post("/predict")
async def predict(request: Request, response: Response):
    payload = await request.body()
    if not payload:
        response.status_code = 400
        return {"detail": "An image payload is required"}

    try:
        image = Image.open(io.BytesIO(payload)).convert("RGB")
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
