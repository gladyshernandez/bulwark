# Bulwark evaluation harness

Runs public prompt-injection datasets through a live Bulwark instance and records the decision for
every prompt. This is the foundation for the measurement work: it produces a results file pairing
each prompt's ground-truth label with Bulwark's verdict. Computing detection and false-positive
rates from that file comes later.

## Datasets

Normalised into one schema — `{id, text, label, source}` where `label` is `attack` or `benign`.

Works out of the box (open on the Hub):

- **deepset/prompt-injections** — labeled mix: injections (attack) + legitimate prompts (benign)
- **Gandalf** (Lakera) — attack
- **NotInject** (`leolee99/NotInject`) — benign prompts that contain injection trigger-words

Gated on the Hub — loaded only when authenticated (`huggingface-cli login` or `HF_TOKEN`), skipped
with a warning otherwise:

- **HackAPrompt** (`hackaprompt/hackaprompt-dataset`) — attack; screens the attacker's `user_input`
- **WildGuard** (`allenai/wildguardmix`) — benign; the prompts it labels `unharmful`

Accepting each gated dataset's terms on its Hub page (once, with your account) is required in addition
to authenticating.

## Setup

```bash
python -m venv .venv
.venv/Scripts/activate       # Windows;  source .venv/bin/activate on macOS/Linux
pip install -r requirements.txt
```

## Run

Start Bulwark locally first (it exposes `POST /v1/screen`, which returns the decision without
forwarding upstream — no API key or cost needed):

```bash
# from the repo root, in another shell:
mvn spring-boot:run
```

Then screen the datasets. Use `--limit` to sample while iterating:

```bash
python run.py --limit 50                 # 50 prompts per source, quick check
python run.py                            # full corpora
python run.py --base-url http://host     # target a remote Bulwark
```

Output is `results.jsonl` — one row per prompt with its label and Bulwark's decision
(`flagged`, `action`, `verdict`, `layer`, `rule`, `score`, `latency_micros`).

## pint-benchmark scoring (per-layer + full stack)

`pint.py` scores each layer independently and the full stack, and prints a per-detector table
(detection rate, false-positive rate, accuracy). Per-layer verdicts come from `POST
/v1/screen/layers`, which runs each enabled layer on its own (no escalation gating).

```bash
python pint.py --limit 25                 # score the bundled datasets, sampled
python pint.py --dataset path/to/pint.yaml  # score a pint-format YAML ({text,category,label})
python pint.py --no-stack                 # per-layer only (skip the end-to-end call)
```

Enable Layer 2 (`BULWARK_LAYER2_URL`) / Layer 3 (`BULWARK_LAYER3_ENABLED`) on the Bulwark side to
measure them — with only Layer 1 on, only `layer1-regex` appears. Measuring Layer 3 runs the paid
judge on every prompt, so turn it on deliberately.

To use Lakera's own `pint-benchmark.ipynb`, import `make_pint_eval_function(client, detector)` from
`pint.py` — it returns an `eval_function(text) -> bool` in the shape the notebook expects.

## Results table

`metrics.py` builds the full results table — per layer and the full stack: detection rate,
false-positive rate, mean latency, an estimated cost, and which layer catches what in the stack.

```bash
python metrics.py --limit 25                       # sampled
python metrics.py --judge-model claude-sonnet-5   # model used for the Layer 3 cost estimate
```

Layer 1 and Layer 2 are self-hosted (cost = latency only); Layer 3 calls a paid model, so its cost
is estimated from prompt length and price (turn Layer 3 on to include it). Writes `metrics.json`
and a markdown `results.md`.

Warm the Layer 2 sidecar before a scored run — cold-start requests can exceed the classify timeout
and degrade, undercounting Layer 2's detection.
