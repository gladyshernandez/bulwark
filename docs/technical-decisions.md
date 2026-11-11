# Technical decisions

Why Bulwark is built the way it is.

## Why a proxy

Bulwark runs as a proxy in front of the model, not as a library. An app points its OpenAI client at
Bulwark's base URL instead of the provider — a config change, not a code change — so it works from any
language that can make HTTP requests.

The upstream provider is a config value (`UPSTREAM_BASE_URL`), so Bulwark is not tied to one provider.
It currently forwards to Anthropic's OpenAI-compatible layer.

## Why three layers, cheapest first

Bulwark runs three detectors in order of cost and stops at the first that blocks:

- **Layer 1 — regex rules.** Fast; matches known injection phrases and patterns.
- **Layer 2 — a pre-trained DeBERTa classifier.** Catches paraphrased and reworded inputs the regex
  misses, at a few hundred milliseconds per call.
- **Layer 3 — an LLM judge.** Sends the input to a Claude model for a verdict; the slowest layer and
  the only one that costs money per call.

Because it stops at the first block, most requests are decided by Layers 1 and 2, and the judge only
runs on the ones they pass. In the sample run the stack averaged about 90 ms per request, while a single
judge call took about 1.5 s.

## Why a pre-trained classifier, not a fine-tuned one

Layer 2 uses an off-the-shelf model, which keeps the results reproducible — anyone can run the same
setup. Fine-tuning was out of scope.

## Why an LLM judge, and which model

The judge is the catch-all for the subtle cases the regex and classifier miss. It returns a verdict with
a short reason, and in the sample run it had the highest detection rate and no false positives.

It defaults to `claude-sonnet-5`, and it's off unless you enable it — it's the slow, paid layer, so you
turn it on deliberately.

## Injection vs jailbreak

Bulwark screens for prompt injection, not jailbreaks:

- **Prompt injection** is untrusted input trying to override the developer's instructions. A proxy in
  front of the model can screen the input for it.
- **A jailbreak** gets the model to ignore its own safety training. That is decided by the model, not
  the input, so a proxy cannot prevent it.

Bulwark is scoped to injection.

## Configurable degrade

When a layer can't run — its sidecar or the judge is unavailable — Bulwark records the gap and either
forwards the request (fail open) or refuses it (fail closed), depending on config.

## Indirect injection

Injection can arrive in retrieved documents or tool output, not only the user's message. Bulwark screens
configured request fields (for example `documents` and `context`) as untrusted content, so an injection
inside a retrieved document is checked like any other input.

## Measurement

Bulwark scores each layer on its own and the full stack, reporting detection rate, false-positive rate,
latency, and cost — including the attacks that get through. It runs on public datasets and can be used
with Lakera's pint-benchmark. See [`eval/`](../eval/README.md).

## Why a frozen sample with a dev/test split

The eval draws a seeded random sample from each dataset and freezes it to a file, rather than
re-streaming the datasets on every run. Two problems made this necessary:

- **A random sample, not the first N rows.** The old harness took the first rows of each dataset, which
  are often unrepresentative. Sampling now uses a reservoir draw seeded per source — random but
  reproducible — so adding a source later doesn't reshuffle the others.
- **A held-out test split.** The block threshold was originally tuned on the same prompts the results
  were reported on. The tuning had effectively seen the test answers, so the numbers looked better than
  they were. Each prompt is now assigned to a dev split (~30%, for tuning and model choice) or a test
  split (~70%, for reported numbers) by hashing its text. The assignment is stable — a prompt always
  lands in the same split, whatever the seed or run order — so the two never leak into each other.

The frozen sample's text stays out of git, because the gated datasets can't be redistributed. A small
manifest is committed instead — the seed, the per-source counts, and each dataset's Hub revision — so the
exact sample can be rebuilt without redistributing anyone's data.
