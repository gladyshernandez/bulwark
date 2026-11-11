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


def rates(t: Tally) -> dict:
    """Detection and false-positive counts, rates, and 95% Wilson intervals for one tally."""
    return {
        "attacks": t.attacks,
        "caught": t.tp,
        "detection_rate": round(t.detection_rate, 4),
        "detection_ci": [round(x, 4) for x in t.detection_ci],
        "benign": t.benign,
        "false_positives": t.fp,
        "fp_rate": round(t.fp_rate, 4),
        "fp_ci": [round(x, 4) for x in t.fp_ci],
    }


def main() -> None:
    p = argparse.ArgumentParser(description="Build Bulwark's per-layer + full-stack results table.")
    p.add_argument("--base-url", default="http://localhost:8080")
    p.add_argument("--dataset", help="pint-format YAML; omit to use the bundled datasets")
    p.add_argument("--limit", type=int, default=None, help="per-source cap")
    p.add_argument("--sample", help="frozen sample from build_sample.py (recommended)")
    p.add_argument("--split", choices=["dev", "test", "all"], default="test")
    p.add_argument("--judge-model", default="claude-sonnet-5", help="Layer 3 model, for cost")
    p.add_argument("--json-out", default="eval/metrics.json")
    p.add_argument("--md-out", default="eval/results.md")
    args = p.parse_args()

    client = BulwarkClient(args.base_url)
    if client.screen("preflight").error:
        sys.exit(f"Bulwark not reachable at {args.base_url}. Start it first.")

    tallies: dict[str, Tally] = {}
    by_source: dict[tuple[str, str], Tally] = {}   # (detector, source) -> tally
    latency_us: dict[str, list[int]] = {}
    cost: dict[str, float] = {}
    contribution: dict[str, int] = {}   # of attacks the stack caught, which layer decided
    errors = {"layers": 0, "stack": 0, "layer_degraded": 0}
    stack_degraded = 0
    total = 0

    for _, text, is_attack, source in load_records(args.dataset, args.limit, args.sample, args.split):
        lv = client.screen_layers(text)
        stack = client.screen(text)
        # A failed call isn't a detector's judgment. Skip the whole prompt so every detector is
        # scored on the same set, rather than counting the failure as a miss or a correct pass.
        if lv.error:
            errors["layers"] += 1
            continue
        if stack.error:
            errors["stack"] += 1
            continue
        if "DEGRADED" in lv.verdicts.values():
            errors["layer_degraded"] += 1   # a layer couldn't run: not its judgment, don't score it
            continue
        if stack.verdict == "DEGRADED":
            stack_degraded += 1             # real fail-open behaviour; report it, keep it scored
        for layer, flagged in lv.flags.items():
            tallies.setdefault(layer, Tally()).add(is_attack, flagged)
            by_source.setdefault((layer, source), Tally()).add(is_attack, flagged)
            latency_us.setdefault(layer, []).append(lv.latencies.get(layer, 0))
        if "layer3-judge" in lv.flags:
            cost["layer3-judge"] = cost.get("layer3-judge", 0.0) + estimate_judge_cost(text, args.judge_model)

        tallies.setdefault("stack", Tally()).add(is_attack, stack.flagged)
        by_source.setdefault(("stack", source), Tally()).add(is_attack, stack.flagged)
        latency_us.setdefault("stack", []).append(stack.latency_micros or 0)
        if stack.flagged and is_attack and stack.layer:
            contribution[stack.layer] = contribution.get(stack.layer, 0) + 1
        total += 1
        if total % 200 == 0:
            print(f"  ...{total} prompts", file=sys.stderr)

    failed = sum(errors.values())
    if failed > 0.01 * (total + failed):
        sys.exit(f"{failed} failed or degraded calls (>1%): warm the classifier, raise timeouts, and re-run")

    order = ["layer1-regex", "layer2-deberta", "layer3-judge", "stack"]
    rows = []
    for d in order:
        t = tallies.get(d)
        if not t:
            continue
        lats = latency_us.get(d, [])
        mean_ms = (sum(lats) / len(lats) / 1000) if lats else 0.0
        rows.append({
            "detector": d, **rates(t),
            "mean_latency_ms": round(mean_ms, 2),
            "est_cost_usd": round(cost.get(d, 0.0), 4),
        })

    source_rows = [{"detector": d, "source": src, **rates(t)}
                   for (d, src), t in sorted(by_source.items())]

    report = {"prompts": total, "detectors": rows, "by_source": source_rows,
              "stack_contribution": contribution, "errors": errors, "stack_degraded": stack_degraded}
    Path(args.json_out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    Path(args.md_out).write_text(render_markdown(report), encoding="utf-8")

    print("\n" + render_markdown(report))
    print(f"\nscored {total} prompts; skipped {errors}; stack fail-open (degraded, kept): {stack_degraded}")
    print(f"\nwrote {args.json_out} and {args.md_out}")


def _det_cell(r: dict) -> str:
    if not r["attacks"]:
        return "—"
    lo, hi = r["detection_ci"]
    return f"{r['detection_rate']:.1%} ({lo:.1%}–{hi:.1%}, {r['caught']}/{r['attacks']})"


def _fp_cell(r: dict) -> str:
    if not r["benign"]:
        return "—"
    lo, hi = r["fp_ci"]
    return f"{r['fp_rate']:.1%} ({lo:.1%}–{hi:.1%}, {r['false_positives']}/{r['benign']})"


def render_markdown(report: dict) -> str:
    lines = [
        f"# Bulwark results ({report['prompts']} prompts)",
        "",
        "Rates are percentages with a 95% Wilson interval and (hits/total).",
        "",
        "| detector | detection rate | false-positive rate | mean latency | est. cost / 1k |",
        "|---|---:|---:|---:|---:|",
    ]
    for r in report["detectors"]:
        cost_1k = r["est_cost_usd"] / report["prompts"] * 1000 if report["prompts"] else 0.0
        cost_str = "self-hosted" if r["est_cost_usd"] == 0 else f"${cost_1k:.2f}"
        lines.append(f"| {r['detector']} | {_det_cell(r)} | {_fp_cell(r)} | "
                     f"{r['mean_latency_ms']:.1f} ms | {cost_str} |")

    if report.get("by_source"):
        lines += ["", "## By source", "",
                  "| detector | source | detection rate | false-positive rate |",
                  "|---|---|---:|---:|"]
        for r in report["by_source"]:
            lines.append(f"| {r['detector']} | {r['source']} | {_det_cell(r)} | {_fp_cell(r)} |")

    contrib = report["stack_contribution"]
    if contrib:
        lines += ["", "**Full-stack detections, by deciding layer:** "
                  + ", ".join(f"{k} {v}" for k, v in sorted(contrib.items()))]
    errs, degraded = report.get("errors", {}), report.get("stack_degraded", 0)
    lines += ["", f"_Skipped, not scored: {errs}. Stack fail-open kept in score: {degraded}._"]
    return "\n".join(lines)


if __name__ == "__main__":
    main()
