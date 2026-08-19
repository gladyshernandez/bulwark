"""Load public prompt-injection datasets and normalise them to {id, text, label, source}.

`label` is ground truth: "attack" (a real injection attempt) or "benign" (a legitimate prompt).
Some named datasets are gated on the Hub (HackAPrompt, WildGuard); they load only when an HF_TOKEN
is available, and are skipped with a warning otherwise so the harness still runs on the open sets.
"""
import itertools
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


def load_all(limit_per_source: int | None = None) -> Iterator[Record]:
    """Yield normalised records from every reachable source, one source at a time."""
    for name, loader in SOURCES.items():
        try:
            rows = loader()
            if limit_per_source:
                rows = itertools.islice(rows, limit_per_source)
            count = 0
            for rec in rows:
                if rec.text and rec.text.strip():
                    yield rec
                    count += 1
            print(f"  loaded {name}: {count}", file=sys.stderr)
        except Exception as e:
            # Gated or unavailable dataset - keep going with the open ones.
            print(f"  skip {name}: {type(e).__name__}: {str(e)[:120]}", file=sys.stderr)
