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
from math import sqrt
from pathlib import Path
from typing import Iterator

from client import BulwarkClient


def wilson(k: int, n: int, z: float = 1.96) -> tuple[float, float]:
    """95% Wilson score interval for k successes out of n."""
    if n == 0:
        return (0.0, 0.0)
    p = k / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


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

    @property
    def detection_ci(self) -> tuple[float, float]:  # 95% interval on the detection rate
        return wilson(self.tp, self.attacks)

    @property
    def fp_ci(self) -> tuple[float, float]:  # 95% interval on the false-positive rate
        return wilson(self.fp, self.benign)


def load_records(dataset: str | None, limit: int | None,
                 sample: str | None = None, split: str = "all") -> Iterator[tuple[str, str, bool, str]]:
    """Yield (id, text, is_attack, source). From a frozen sample, a pint YAML, or the live datasets."""
    if sample:
        with Path(sample).open(encoding="utf-8") as f:
            for line in f:
                r = json.loads(line)
                if split == "all" or r["split"] == split:
                    yield r["id"], r["text"], r["label"] == "attack", r["source"]
    elif dataset:
        import yaml
        rows = yaml.safe_load(Path(dataset).read_text(encoding="utf-8"))
        for i, r in enumerate(rows):
            yield f"pint-{i}", r["text"], bool(r["label"]), "pint"
    else:
        from datasets_loader import load_all
        for rec in load_all(limit_per_source=limit):
            if split == "all" or rec.split == split:
                yield rec.id, rec.text, rec.label == "attack", rec.source


def main() -> None:
    p = argparse.ArgumentParser(description="Score Bulwark per-layer and end-to-end, pint-style.")
    p.add_argument("--base-url", default="http://localhost:8080")
    p.add_argument("--dataset", help="pint-format YAML {text,category,label}; omit to use the bundled datasets")
    p.add_argument("--limit", type=int, default=None, help="per-source cap when using the corpora")
    p.add_argument("--sample", help="frozen sample from build_sample.py (recommended)")
    p.add_argument("--split", choices=["dev", "test", "all"], default="test")
    p.add_argument("--no-stack", action="store_true", help="skip the end-to-end call (per-layer only)")
    p.add_argument("--out", default="eval/pint_results.jsonl")
    args = p.parse_args()

    client = BulwarkClient(args.base_url)
    if client.screen("preflight").error:
        sys.exit(f"Bulwark not reachable at {args.base_url}. Start it first.")

    tallies: dict[str, Tally] = {}
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    errors = {"layers": 0, "stack": 0, "layer_degraded": 0}
    stack_degraded = 0

    with out.open("w", encoding="utf-8") as f:
        for rid, text, is_attack, source in load_records(args.dataset, args.limit, args.sample, args.split):
            lv = client.screen_layers(text)
            stack = None if args.no_stack else client.screen(text)
            # A failed call isn't a detector's judgment: skip the prompt so every detector scores
            # the same set, instead of counting the failure as a miss or a correct pass.
            if lv.error:
                errors["layers"] += 1
                continue
            if stack is not None and stack.error:
                errors["stack"] += 1
                continue
            if "DEGRADED" in lv.verdicts.values():
                errors["layer_degraded"] += 1   # a layer couldn't run: don't score its non-judgment
                continue
            if stack is not None and stack.verdict == "DEGRADED":
                stack_degraded += 1             # real fail-open behaviour; report it, keep it scored
            row = {"id": rid, "attack": is_attack, "layers": lv.flags}
            for layer, flagged in lv.flags.items():
                tallies.setdefault(layer, Tally()).add(is_attack, flagged)
            if stack is not None:
                row["stack"] = stack.flagged
                tallies.setdefault("stack", Tally()).add(is_attack, stack.flagged)
            f.write(json.dumps(row) + "\n")
            total += 1
            if total % 200 == 0:
                print(f"  ...{total} prompts", file=sys.stderr)

    failed = sum(errors.values())
    if failed > 0.01 * (total + failed):
        sys.exit(f"{failed} failed or degraded calls (>1%): warm the classifier, raise timeouts, and re-run")

    print(f"\nscored {total} prompts; skipped {errors}; stack fail-open (degraded, kept): {stack_degraded} -> {out}\n")
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
