"""
Layer 2 classifier sidecar.

Serves a pre-trained DeBERTa prompt-injection classifier behind a tiny HTTP API so the
Java proxy can call it as Layer 2. The model is loaded once at startup; each request runs
a single forward pass and returns the probability that the input is an injection.
"""
import os

from fastapi import FastAPI
from pydantic import BaseModel
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    TextClassificationPipeline,
)

MODEL_ID = os.getenv("MODEL_ID", "protectai/deberta-v3-base-prompt-injection")
MAX_LENGTH = int(os.getenv("MAX_LENGTH", "512"))

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_ID)
pipeline = TextClassificationPipeline(
    model=model,
    tokenizer=tokenizer,
    truncation=True,
    max_length=MAX_LENGTH,
)

app = FastAPI(title="Bulwark Layer 2 classifier")


class ClassifyRequest(BaseModel):
    text: str


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_ID}


@app.post("/classify")
def classify(req: ClassifyRequest):
    result = pipeline(req.text)[0]
    label = result["label"]
    confidence = float(result["score"])
    # The pipeline reports confidence in its predicted label; normalise to the
    # probability of the INJECTION class so the Java side always sees one scale.
    injection_prob = confidence if label.upper() == "INJECTION" else 1.0 - confidence
    return {"score": injection_prob, "label": label}
