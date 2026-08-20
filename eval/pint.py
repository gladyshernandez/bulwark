"""Score Bulwark with pint-benchmark.

Two things live here:
- `make_pint_eval_function(client, detector)` gives an `eval_function(text) -> bool` in the shape
  Lakera's pint-benchmark notebook expects - for the whole stack, or for one layer.
- Running `python pint.py` scores each layer and the full stack over a pint-format YAML (or the
  datasets) and prints a table of detection rate, false-alarm rate, and accuracy.

Per-layer scores come from /v1/screen/layers, where each layer judges every prompt on its own; the
full-stack score comes from /v1/screen.
"""
import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator

from client import BulwarkClient


def make_pint_eval_function(client: BulwarkClient, detector: str = "stack"):
    """eval_function(text) -> bool for pint-benchmark. detector = 'stack' or a layer name."""
    def eval_function(text: str) -> bool:
        if detector == "stack":
            return client.screen(text).flagged
        return client.screen_layers(text).flags.get(detector, False)
    return eval_function


@dataclass
class Tally:
    tp: int = 0  # attack, flagged
    fp: int = 0  # benign, flagged
    tn: int = 0  # benign, not flagged
    fn: int = 0  # attack, not flagged

    def add(self, is_attack: bool, flagged: bool) -> None:
        if is_attack:
            self.tp += flagged
            self.fn += not flagged
        else:
            self.fp += flagged
            self.tn += not flagged

    @property
    def attacks(self) -> int:
        return self.tp + self.fn

    @property
    def benign(self) -> int:
        return self.fp + self.tn

    @property
    def detection_rate(self) -> float:  # recall on attacks
        return self.tp / self.attacks if self.attacks else 0.0

    @property
    def fp_rate(self) -> float:  # over-defense
        return self.fp / self.benign if self.benign else 0.0

    @property
    def accuracy(self) -> float:
        n = self.tp + self.fp + self.tn + self.fn
        return (self.tp + self.tn) / n if n else 0.0


def load_records(dataset: str | None, limit: int | None) -> Iterator[tuple[str, str, bool]]:
    """Yield (id, text, is_attack). From a pint-format YAML if given, else other datasets."""
    if dataset:
        import yaml
        rows = yaml.safe_load(Path(dataset).read_text(encoding="utf-8"))
        for i, r in enumerate(rows):
            yield f"pint-{i}", r["text"], bool(r["label"])
    else:
        from datasets_loader import load_all
        for rec in load_all(limit_per_source=limit):
            yield rec.id, rec.text, rec.label == "attack"


def main() -> None:
    p = argparse.ArgumentParser(description="Score Bulwark per-layer and end-to-end, pint-style.")
    p.add_argument("--base-url", default="http://localhost:8080")
    p.add_argument("--dataset", help="pint-format YAML {text,category,label}; omit to use the bundled datasets")
    p.add_argument("--limit", type=int, default=None, help="per-source cap when using the corpora")
    p.add_argument("--no-stack", action="store_true", help="skip the end-to-end call (per-layer only)")
    p.add_argument("--out", default="eval/pint_results.jsonl")
    args = p.parse_args()

    client = BulwarkClient(args.base_url)
    if client.screen("preflight").error:
        sys.exit(f"Bulwark not reachable at {args.base_url}. Start it first.")

    tallies: dict[str, Tally] = {}
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    total, errors = 0, 0

    with out.open("w", encoding="utf-8") as f:
        for rid, text, is_attack in load_records(args.dataset, args.limit):
            lv = client.screen_layers(text)
            if lv.error:
                errors += 1
                continue
            row = {"id": rid, "attack": is_attack, "layers": lv.flags}
            for layer, flagged in lv.flags.items():
                tallies.setdefault(layer, Tally()).add(is_attack, flagged)
            if not args.no_stack:
                stack = client.screen(text).flagged
                row["stack"] = stack
                tallies.setdefault("stack", Tally()).add(is_attack, stack)
            f.write(json.dumps(row) + "\n")
            total += 1
            if total % 200 == 0:
                print(f"  ...{total} prompts", file=sys.stderr)

    print(f"\nscored {total} prompts ({errors} errors) -> {out}\n")
    header = (f"{'detector':16}{'attacks':>8}{'caught':>8}{'det.rate':>10}"
              f"{'benign':>8}{'FP':>5}{'FP.rate':>9}{'acc':>8}")
    print(header)
    print("-" * len(header))
    for d in ("layer1-regex", "layer2-deberta", "layer3-judge", "stack"):
        t = tallies.get(d)
        if not t:
            continue
        print(f"{d:16}{t.attacks:>8}{t.tp:>8}{t.detection_rate:>9.1%}"
              f"{t.benign:>8}{t.fp:>5}{t.fp_rate:>8.1%}{t.accuracy:>8.1%}")


if __name__ == "__main__":
    main()
