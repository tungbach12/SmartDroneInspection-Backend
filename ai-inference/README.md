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
| Dataset | [`asadibehnam/mbdd2025-uav-building-defect-detection`](https://www.kaggle.com/datasets/asadibehnam/mbdd2025-uav-building-defect-detection) — 14,471 VOC-annotated UAV images; Kaggle mirror of [MBDD2025 on Zenodo](https://doi.org/10.5281/zenodo.15622584), CC BY 4.0 |
| Base model | YOLO11s pretrained weights, up to 100 epochs, imgsz 640, AdamW, cosine LR |
| Classes | `crack`, `leakage`, `abscission`, `corrosion`, `bulge` (fixed ID order in notebook) |

Files in this folder:

```
ai-inference/
├── app.py                  FastAPI service (raw-body contract, see below)
├── Dockerfile              CPU image; weights are mounted, not baked in
├── requirements.txt
├── training-notebook.ipynb Simple Kaggle preprocessing, training, and held-out evaluation
├── kernel-metadata.json    Kaggle kernel metadata (dataset source, GPU flags)
└── models/
    ├── best.pt             YOLO11s weights used by the service
    ├── best.onnx           ONNX export (for embedding elsewhere)
    └── training/           results.png, confusion_matrix.png (report evidence)
```

`best.pt` and `best.onnx` are git-ignored: they are large binaries, and a rebuild
should come from the kernel rather than from the repository.

## Training and retrieving a candidate

The training kernel is private and requires an authorized Kaggle API token. The
notebook is configured for `asadibehnam/mbdd2025-uav-building-defect-detection`;
confirm that the Kaggle kernel is attached to this dataset before running it. The
notebook pairs VOC annotations by filename stem, clips out-of-bounds boxes to image
edges, and reports the number clipped or dropped. It trains/evaluates on a deterministic,
dominant-class-stratified image-level 70/20/10 split. The dataset does not expose a verified building/flight group key in
the notebook, so related views may cross splits; this limitation is documented in
the notebook and candidate provenance.

The run exports a **candidate** plus `test_metrics.json`, `split_manifest.csv`,
`class_distribution.csv`, `provenance.json`, training results, and plots. Retrieve
only the export directory:

```bash
kaggle kernels output natsumeeei/smartdroneinspection-mbdd2025-yolo11s-training \
  -p .scratch/kaggle-export --file-pattern "^export/" --page-size 200
```

Do not copy a candidate over `ai-inference/models/best.pt` until held-out metrics
and predictions have been reviewed and promotion is explicitly approved.

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
