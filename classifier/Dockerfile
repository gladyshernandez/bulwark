# syntax=docker/dockerfile:1
# Layer 2 DeBERTa classifier sidecar.
FROM python:3.11-slim

WORKDIR /app

# Install dependencies first so the layer caches across app.py edits.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Bake the model into the image so the container doesn't download it on first
# request. The model is a build arg so a different one can be baked in; a gated
# model needs HF_TOKEN, passed as a secret mount that never lands in a layer or
# the image history (a build arg would be recoverable from `docker history`).
ARG MODEL_ID=protectai/deberta-v3-base-prompt-injection
ENV MODEL_ID=${MODEL_ID}
RUN --mount=type=secret,id=hf_token,required=false \
    HF_TOKEN="$(cat /run/secrets/hf_token 2>/dev/null || true)" \
    python -c "from transformers import AutoTokenizer, AutoModelForSequenceClassification; \
    import os; m=os.environ['MODEL_ID']; \
    AutoTokenizer.from_pretrained(m); AutoModelForSequenceClassification.from_pretrained(m)"

COPY app.py .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
