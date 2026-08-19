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
- **NotInject** (`leolee99/NotInject`) — benign prompts full of injection trigger-words (over-defense probes)

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
