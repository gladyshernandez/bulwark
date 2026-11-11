"""Load public prompt-injection datasets and normalise them to {id, text, label, source}.

`label` is ground truth: "attack" (a real injection attempt) or "benign" (a legitimate prompt).
Some named datasets are gated on the Hub (HackAPrompt, WildGuard); they load only when an HF_TOKEN
is available, and are skipped with a warning otherwise so the harness still runs on the open sets.
"""
import hashlib
import random
import re
import sys
from dataclasses import dataclass
from typing import Callable, Iterator

from datasets import load_dataset

ATTACK = "attack"
BENIGN = "benign"


@dataclass
class Record:
    id: str
    text: str
    label: str
    source: str


def _normalise(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip().lower()


def _digest(text: str) -> bytes:
    return hashlib.sha1(_normalise(text).encode("utf-8")).digest()


def _unique(rows: Iterator[Record]) -> Iterator[Record]:
    """Drop empty and duplicate texts within one source."""
    seen: set[bytes] = set()
    for rec in rows:
        if not rec.text or not rec.text.strip():
            continue
        d = _digest(rec.text)
        if d in seen:
            continue
        seen.add(d)
        yield rec


def _reservoir(rows: Iterator[Record], k: int, rng: random.Random) -> list[Record]:
    """Uniform random sample of k rows from a stream (Algorithm R)."""
    sample: list[Record] = []
    for i, row in enumerate(rows):
        if i < k:
            sample.append(row)
        else:
            j = rng.randint(0, i)
            if j < k:
                sample[j] = row
    return sample


def _deepset() -> Iterator[Record]:
    # Labeled mix: label 1 = injection, 0 = legitimate.
    for i, r in enumerate(load_dataset("deepset/prompt-injections", split="train")):
        label = ATTACK if r["label"] == 1 else BENIGN
        yield Record(f"deepset-{i}", r["text"], label, "deepset")


def _gandalf() -> Iterator[Record]:
    # Prompts that tricked Lakera's Gandalf into leaking its secret - all attacks.
    for i, r in enumerate(load_dataset("Lakera/gandalf_ignore_instructions", split="train")):
        yield Record(f"gandalf-{i}", r["text"], ATTACK, "gandalf")


def _notinject() -> Iterator[Record]:
    # Benign prompts loaded with injection trigger-words - an over-defense stress test.
    i = 0
    for split in ("NotInject_one", "NotInject_two", "NotInject_three"):
        for r in load_dataset("leolee99/NotInject", split=split):
            yield Record(f"notinject-{i}", r["prompt"], BENIGN, "notinject")
            i += 1


def _hackaprompt() -> Iterator[Record]:
    # Gated - needs HF_TOKEN. Crowd-sourced injection attempts; user_input is the attacker's text.
    for i, r in enumerate(load_dataset("hackaprompt/hackaprompt-dataset", split="train")):
        text = r.get("user_input") or r.get("prompt") or ""
        if text:
            yield Record(f"hackaprompt-{i}", text, ATTACK, "hackaprompt")


def _wildguard() -> Iterator[Record]:
    # Gated - needs HF_TOKEN. Keep only prompts the dataset labels unharmful (a general benign set).
    ds = load_dataset("allenai/wildguardmix", "wildguardtrain", split="train")
    for i, r in enumerate(ds):
        if r.get("prompt_harm_label") == "unharmful" and r.get("prompt"):
            yield Record(f"wildguard-{i}", r["prompt"], BENIGN, "wildguard")


SOURCES: dict[str, Callable[[], Iterator[Record]]] = {
    "deepset": _deepset,
    "gandalf": _gandalf,
    "notinject": _notinject,
    "hackaprompt": _hackaprompt,  # gated; skipped without HF_TOKEN
    "wildguard": _wildguard,      # gated; skipped without HF_TOKEN
}


def load_all(limit_per_source: int | None = None, seed: int = 42) -> Iterator[Record]:
    """Yield deduplicated records; a seeded random sample per source when limited."""
    seen: dict[bytes, str] = {}  # digest -> label, for cross-source dedupe
    for name, loader in SOURCES.items():
        try:
            rows = _unique(loader())
            if limit_per_source:
                rows = iter(_reservoir(rows, limit_per_source, random.Random(f"{seed}-{name}")))
            kept = dropped = 0
            for rec in rows:
                d = _digest(rec.text)
                if d in seen:
                    if seen[d] != rec.label:
                        print(f"  label conflict: {rec.id}", file=sys.stderr)
                    dropped += 1
                    continue
                seen[d] = rec.label
                kept += 1
                yield rec
            print(f"  loaded {name}: {kept} (cross-source dupes dropped: {dropped})", file=sys.stderr)
        except Exception as e:
            # Gated or unavailable dataset - keep going with the open ones.
            print(f"  skip {name}: {type(e).__name__}: {str(e)[:120]}", file=sys.stderr)
