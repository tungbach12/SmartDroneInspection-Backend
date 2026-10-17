# Advisory detection service: YOLO11s trained on MBDD2025, served over HTTP.
# The weights are mounted at runtime rather than baked into the image, so a new
# best.pt can be dropped into ai-inference/models/ without rebuilding.
FROM python:3.11-slim

# OpenCV, pulled in by ultralytics, needs these shared libraries in a slim base.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# CPU-only torch: this service runs on CPU, and the default wheel pulls in the CUDA
# runtime for no benefit at several times the size.
RUN pip install --no-cache-dir \
        --index-url https://download.pytorch.org/whl/cpu \
        torch==2.5.1 torchvision==0.20.1

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py ./

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health')" || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
