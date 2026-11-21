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

# Comma-separated label names that mean "injection" for this model. Models name
# their positive class differently (INJECTION, LABEL_1, jailbreak, ...), so the
# label can't be hard-coded. Check config.json (id2label) on the model's page.
INJECTION_LABELS = {s.strip().upper() for s in os.getenv("INJECTION_LABELS", "INJECTION").split(",")}

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_ID)
pipeline = TextClassificationPipeline(
    model=model,
    tokenizer=tokenizer,
    truncation=True,
    max_length=MAX_LENGTH,
    top_k=None,  # return a score for every label, not just the top one
)

# Map the model's own labels to ids, then find which ids count as "injection".
# Failing here means a mislabeled model stops the sidecar at startup instead of
# scoring every input backwards and silently passing attacks through.
id2label = {int(i): str(label).upper() for i, label in model.config.id2label.items()}
INJECTION_IDS = [i for i, label in id2label.items() if label in INJECTION_LABELS]
if not INJECTION_IDS:
    raise RuntimeError(
        f"none of {sorted(INJECTION_LABELS)} in model labels {id2label}; set INJECTION_LABELS"
    )

INJECTION_LABEL_NAMES = {id2label[i] for i in INJECTION_IDS}

app = FastAPI(title="Bulwark Layer 2 classifier")


class ClassifyRequest(BaseModel):
    text: str


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_ID,
        "labels": id2label,
        "injection_ids": INJECTION_IDS,
    }


@app.post("/classify")
def classify(req: ClassifyRequest):
    # top_k=None gives a score per label; sum the injection labels so the Java
    # side always sees the probability of the injection class on one scale.
    scores = pipeline(req.text)
    # A single string returns a flat list of label scores; a batch would nest
    # them one level deeper. Unwrap so the rest works either way.
    if scores and isinstance(scores[0], list):
        scores = scores[0]
    injection_prob = sum(
        float(s["score"]) for s in scores if s["label"].upper() in INJECTION_LABEL_NAMES
    )
    top = max(scores, key=lambda s: s["score"])
    return {"score": injection_prob, "label": top["label"]}
