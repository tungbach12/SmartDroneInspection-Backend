# Advisory detection service

Self-hosted defect detector for MF3-05. It serves the **YOLO11s** model trained on
**MBDD2025** and answers `POST /predict` with the detection shape
`YoloInferenceClient` already expects, so the backend treats it as the default
advisory detector and the compatible vision endpoint (an LLM) as the option behind
it.

## Where the model comes from

| | |
| --- | --- |
| Kaggle kernel | `natsumeeei/smartdroneinspection-mbdd2025-yolo11s-training` |
| Dataset | `mennamahmoudd/mbdd2025-building-defects` (14,471 UAV images) |
| Base model | `yolo11s.pt`, 100 epochs, imgsz 640, AdamW, cosine LR |
| Classes | `crack`, `leakage`, `abscission`, `corrosion`, `bulge` |

Files in this folder:

```
ai-inference/
├── app.py                  FastAPI service (raw-body contract, see below)
├── Dockerfile              CPU image; weights are mounted, not baked in
├── requirements.txt
├── training-notebook.ipynb the Kaggle notebook that produced the weights
├── kernel-metadata.json    Kaggle kernel metadata (dataset source, GPU flags)
└── models/
    ├── best.pt             YOLO11s weights used by the service
    ├── best.onnx           ONNX export (for embedding elsewhere)
    └── training/           results.png, confusion_matrix.png (report evidence)
```

`best.pt` and `best.onnx` are git-ignored: they are large binaries, and a rebuild
should come from the kernel rather than from the repository.

## Re-downloading the weights

The kernel is private, so this needs the Kaggle API token in
`~/.kaggle/access_token` (or `~/.kaggle/kaggle.json`). The output also contains the
converted dataset, which is thousands of files, so always filter to `export/`:

```bash
kaggle kernels output natsumeeei/smartdroneinspection-mbdd2025-yolo11s-training \
  -p .scratch/kaggle-export --file-pattern "^export/" --page-size 200
cp .scratch/kaggle-export/export/best.pt ai-inference/models/best.pt
```

## Running it

Compose starts it as the `ai-inference` service and the backend reaches it at
`http://ai-inference:8000`; the host port is `127.0.0.1:8000` for direct calls.

```bash
docker compose up -d ai-inference
curl http://localhost:8000/health
curl --data-binary @../sample-images/defect-02-bridge-pier-crack.jpg \
  -H "Content-Type: image/jpeg" http://localhost:8000/predict
```

Standalone, without Docker:

```bash
pip install -r requirements.txt
MODEL_PATH=models/best.pt uvicorn app:app --port 8000
```

## Contract

The backend posts the **raw image bytes** with the image content type, not a
multipart upload (the notebook's original `app.py` used `UploadFile`, which the
Spring client never sends). The response is a JSON array:

```json
[
  {
    "modelName": "yolo11s",
    "modelVersion": "mbdd2025-v1",
    "predictedLabel": "corrosion",
    "confidence": 0.87,
    "boundingBox": "{\"xMin\": 0.12, \"yMin\": 0.34, \"xMax\": 0.51, \"yMax\": 0.78}"
  }
]
```

Box values are normalized to 0-1, and `boundingBox` is a JSON string because that
is what `YoloDetection.boundingBox` and the findings UI both parse.

Configuration: `MODEL_PATH`, `MODEL_NAME`, `MODEL_VERSION`, `CONFIDENCE_THRESHOLD`
(default `0.25`), `IMAGE_SIZE` (default `640`).
