"""Run every dataset prompt through Bulwark and record its decision.

Load the public attack/benign corpora, screen each prompt via POST /v1/screen, and write one result row per prompt. It does NOT
compute detection / false-positive rates yet.
"""
import argparse
import json
import sys
from pathlib import Path

from client import BulwarkClient
from datasets_loader import load_all


def main() -> None:
    parser = argparse.ArgumentParser(description="Screen public datasets through Bulwark.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Bulwark base URL")
    parser.add_argument("--limit", type=int, default=None, help="max prompts per source (sampling)")
    parser.add_argument("--out", default="eval/results.jsonl", help="output JSONL path")
    args = parser.parse_args()

    client = BulwarkClient(args.base_url)

    # Preflight: fail fast with a clear message if Bulwark isn't up.
    probe = client.screen("preflight")
    if probe.error:
        sys.exit(f"Bulwark not reachable at {args.base_url} ({probe.error}). Start it first.")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)

    total, errors, counts = 0, 0, {}
    with out.open("w", encoding="utf-8") as f:
        for rec in load_all(limit_per_source=args.limit):
            d = client.screen(rec.text)
            errors += 1 if d.error else 0
            f.write(json.dumps({
                "id": rec.id,
                "source": rec.source,
                "label": rec.label,          # ground truth: attack / benign
                "flagged": d.flagged,        # Bulwark's call: injection or not
                "action": d.action,
                "verdict": d.verdict,
                "layer": d.layer,
                "rule": d.rule,
                "score": d.score,
                "latency_micros": d.latency_micros,
                "error": d.error,
            }) + "\n")
            total += 1
            counts[rec.source] = counts.get(rec.source, 0) + 1
            if total % 200 == 0:
                print(f"  ...{total} prompts screened", file=sys.stderr)

    print(f"done: {total} prompts screened ({errors} errors) -> {out}")
    for source, n in sorted(counts.items()):
        print(f"  {source}: {n}")


if __name__ == "__main__":
    main()
