# Layer 2 DeBERTa classifier sidecar.
FROM python:3.11-slim

WORKDIR /app

# Install dependencies first so the layer caches across app.py edits.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Bake the model into the image so the container doesn't download it on first request.
ENV MODEL_ID=protectai/deberta-v3-base-prompt-injection
RUN python -c "from transformers import AutoTokenizer, AutoModelForSequenceClassification; \
    import os; m=os.environ['MODEL_ID']; \
    AutoTokenizer.from_pretrained(m); AutoModelForSequenceClassification.from_pretrained(m)"

COPY app.py .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
