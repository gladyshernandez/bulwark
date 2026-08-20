"""Build Bulwark's results table.

Runs the datasets through each layer and the full stack, then reports per detector:
detection rate (attacks caught), false-positive rate (over-defense on benign prompts),
mean latency, and an estimated cost. Also shows which layer catches what in the stack.

Layer 1 and Layer 2 are self-hosted, so their only cost is latency. Layer 3 calls a paid model,
so its cost is estimated from prompt length and the model's price (labelled "est"). Writes a
machine-readable metrics.json and a markdown results table.
"""
import argparse
import json
import sys
from pathlib import Path

from client import BulwarkClient
from pint import Tally, load_records

# Rough token estimate for one judge call: a fixed system prompt, the prompt itself, and a short
# JSON verdict back. Enough for a cost ballpark, not billing.
JUDGE_SYSTEM_TOKENS = 180
JUDGE_OUTPUT_TOKENS = 30
PRICES = {  # model -> ($ per 1M input tokens, $ per 1M output tokens)
    "claude-haiku-4-5": (1.0, 5.0),
    "claude-sonnet-5": (3.0, 15.0),
    "claude-opus-4-8": (5.0, 25.0),
    "claude-opus-5": (5.0, 25.0),
}


def estimate_judge_cost(text: str, model: str) -> float:
    in_price, out_price = PRICES.get(model, PRICES["claude-haiku-4-5"])
    input_tokens = JUDGE_SYSTEM_TOKENS + len(text) / 4
    return (input_tokens * in_price + JUDGE_OUTPUT_TOKENS * out_price) / 1_000_000


def main() -> None:
    p = argparse.ArgumentParser(description="Build Bulwark's per-layer + full-stack results table.")
    p.add_argument("--base-url", default="http://localhost:8080")
    p.add_argument("--dataset", help="pint-format YAML; omit to use the bundled datasets")
    p.add_argument("--limit", type=int, default=None, help="per-source cap")
    p.add_argument("--judge-model", default="claude-sonnet-5", help="Layer 3 model, for cost")
    p.add_argument("--json-out", default="eval/metrics.json")
    p.add_argument("--md-out", default="eval/results.md")
    args = p.parse_args()

    client = BulwarkClient(args.base_url)
    if client.screen("preflight").error:
        sys.exit(f"Bulwark not reachable at {args.base_url}. Start it first.")

    tallies: dict[str, Tally] = {}
    latency_us: dict[str, list[int]] = {}
    cost: dict[str, float] = {}
    contribution: dict[str, int] = {}   # of attacks the stack caught, which layer decided
    total = 0

    for _, text, is_attack in load_records(args.dataset, args.limit):
        lv = client.screen_layers(text)
        if lv.error:
            continue
        for layer, flagged in lv.flags.items():
            tallies.setdefault(layer, Tally()).add(is_attack, flagged)
            latency_us.setdefault(layer, []).append(lv.latencies.get(layer, 0))
        if "layer3-judge" in lv.flags:
            cost["layer3-judge"] = cost.get("layer3-judge", 0.0) + estimate_judge_cost(text, args.judge_model)

        stack = client.screen(text)
        tallies.setdefault("stack", Tally()).add(is_attack, stack.flagged)
        latency_us.setdefault("stack", []).append(stack.latency_micros or 0)
        if stack.flagged and is_attack and stack.layer:
            contribution[stack.layer] = contribution.get(stack.layer, 0) + 1
        total += 1
        if total % 200 == 0:
            print(f"  ...{total} prompts", file=sys.stderr)

    order = ["layer1-regex", "layer2-deberta", "layer3-judge", "stack"]
    rows = []
    for d in order:
        t = tallies.get(d)
        if not t:
            continue
        lats = latency_us.get(d, [])
        mean_ms = (sum(lats) / len(lats) / 1000) if lats else 0.0
        rows.append({
            "detector": d,
            "attacks": t.attacks,
            "caught": t.tp,
            "detection_rate": round(t.detection_rate, 4),
            "benign": t.benign,
            "false_positives": t.fp,
            "fp_rate": round(t.fp_rate, 4),
            "mean_latency_ms": round(mean_ms, 2),
            "est_cost_usd": round(cost.get(d, 0.0), 4),
        })

    report = {"prompts": total, "detectors": rows, "stack_contribution": contribution}
    Path(args.json_out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    Path(args.md_out).write_text(render_markdown(report), encoding="utf-8")

    print("\n" + render_markdown(report))
    print(f"\nwrote {args.json_out} and {args.md_out}")


def render_markdown(report: dict) -> str:
    lines = [
        f"# Bulwark results ({report['prompts']} prompts)",
        "",
        "| detector | detection rate | false-positive rate | mean latency | est. cost / 1k |",
        "|---|---:|---:|---:|---:|",
    ]
    for r in report["detectors"]:
        cost_1k = r["est_cost_usd"] / report["prompts"] * 1000 if report["prompts"] else 0.0
        cost_str = "self-hosted" if r["est_cost_usd"] == 0 else f"${cost_1k:.2f}"
        lines.append(
            f"| {r['detector']} | {r['detection_rate']:.1%} "
            f"({r['caught']}/{r['attacks']}) | {r['fp_rate']:.1%} "
            f"({r['false_positives']}/{r['benign']}) | {r['mean_latency_ms']:.1f} ms | {cost_str} |")
    contrib = report["stack_contribution"]
    if contrib:
        lines += ["", "**Full-stack detections, by deciding layer:** "
                  + ", ".join(f"{k} {v}" for k, v in sorted(contrib.items()))]
    return "\n".join(lines)


if __name__ == "__main__":
    main()
