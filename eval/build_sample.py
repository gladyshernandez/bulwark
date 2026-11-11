"""Build the frozen eval sample: a seeded random draw per source, tagged dev/test."""
import argparse
import json
from collections import Counter
from dataclasses import asdict
from pathlib import Path

from datasets_loader import load_all

DATASET_REPOS = {
    "deepset": "deepset/prompt-injections",
    "gandalf": "Lakera/gandalf_ignore_instructions",
    "notinject": "leolee99/NotInject",
    "hackaprompt": "hackaprompt/hackaprompt-dataset",
    "wildguard": "allenai/wildguardmix",
}


def dataset_revisions() -> dict[str, str]:
    """Hub commit SHA of each dataset, so the sample can be rebuilt from the same data."""
    from huggingface_hub import HfApi
    api, revs = HfApi(), {}
    for name, repo in DATASET_REPOS.items():
        try:
            revs[name] = api.dataset_info(repo).sha
        except Exception as e:  # gated without a token, or offline
            revs[name] = f"unavailable ({type(e).__name__})"
    return revs


def main() -> None:
    p = argparse.ArgumentParser(description="Freeze a reproducible eval sample.")
    p.add_argument("--limit", type=int, default=800, help="per-source sample size")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--out", default="eval/data/sample.jsonl")
    p.add_argument("--manifest", default="eval/sample-manifest.json")
    args = p.parse_args()

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    counts: Counter = Counter()
    with out.open("w", encoding="utf-8") as f:
        for rec in load_all(limit_per_source=args.limit, seed=args.seed):
            f.write(json.dumps(asdict(rec)) + "\n")
            counts[(rec.source, rec.label, rec.split)] += 1

    manifest = {
        "seed": args.seed,
        "limit_per_source": args.limit,
        "total": sum(counts.values()),
        "counts": {f"{s}/{l}/{sp}": n for (s, l, sp), n in sorted(counts.items())},
        "dataset_revisions": dataset_revisions(),
    }
    Path(args.manifest).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    print(f"wrote {manifest['total']} records -> {out} (limit={args.limit}, seed={args.seed})")
    for (source, label, split), n in sorted(counts.items()):
        print(f"  {source:12} {label:7} {split:5} {n}")


if __name__ == "__main__":
    main()
