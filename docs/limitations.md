# Limitations

What Bulwark doesn't do, and where its numbers can mislead.

## Complete injection defense isn't achieved

Bulwark doesn't catch every attack. In the sample run, about 18% of attacks (15 of 84) got past the full
stack.

## Jailbreaks are out of scope

Bulwark screens for prompt injection, not jailbreaks. Whether the model gives in to a jailbreak is
decided by the model's own safety training, which a proxy cannot change.

## False positives

In the sample, the classifier flagged about 8% of benign prompts (9 of 116), and the full stack flagged
about 10% (12 of 116). The stack's rate is higher than any single layer because a benign prompt flagged
by any one layer is blocked.

## Threshold tuning doesn't reduce the classifier's false positives

Raising the classifier's block threshold from 0.5 to 0.9 didn't change the stack's detection or
false-positive rates. The prompts the classifier flagged — both the attacks it caught and the benign ones
it got wrong — scored at or above 0.9, so moving the cutoff to 0.9 changed nothing.

Sending the classifier's flagged inputs to the judge instead could remove those false positives, since
the judge had none in the sample. It has not been tried: it would call the paid judge on every blocked
request and could lower detection, so it needs its own measurement first.

## The numbers are from a small sample

The results come from 200 prompts (84 attacks, 116 benign) across five public datasets, with the
classifier warmed and the judge on `claude-sonnet-5`. Sample size, which datasets are used, and whether
the classifier is warmed all change the numbers. Re-run and scale them with the harness in
[`eval/`](../eval/README.md).

## Dataset representativeness

Public datasets may not match your traffic. An attack they don't contain was never measured, so treat
the numbers as a starting point for your own evaluation.

## The judge and upstream may be the same model family

The judge runs a Claude model. If the upstream model is also Claude, the two are the same model family —
a possible source of shared blind spots. This was not measured.

## Indirect screening only covers the fields you configure

Bulwark screens retrieved and tool content in the request fields you name (`documents`, `context`, and
any you add). Content under a field you haven't configured is not screened.

## Judge cost and latency

The judge is the slowest layer and the only one that costs money per call. The stack keeps it rare, but
turning it on adds cost and latency to your traffic — measure it against your own volume before using it
in production.

## Future directions

These would extend the measurement, not the claims:

- **Measure jailbreak-style inputs.** The layers already see the input, so the stack could be run against
  a jailbreak dataset (pint-benchmark includes a jailbreak category) and the results reported — as
  screening jailbreak attempts, not preventing a jailbroken model, which a proxy can't do.
- **A fine-tuned classifier.** Fine-tuning on injection data might raise Layer 2's accuracy, but only
  with the training and test data kept strictly separate — if they overlap, the scores look better than
  they are. Off-the-shelf models were chosen to keep the results reproducible.
- **Output filtering.** Screening the model's responses is a separate capability from input screening,
  and is out of the current scope.
