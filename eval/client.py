"""HTTP client for Bulwark's screen-only endpoint.

Sends a prompt to POST /v1/screen and returns Bulwark's decision. The endpoint screens the
input through the same detection stack as the proxy but never forwards it upstream, so the
harness can score thousands of prompts cheaply.
"""
from dataclasses import dataclass

import requests


@dataclass
class Decision:
    """Bulwark's verdict on one prompt."""
    flagged: bool             # True when Bulwark judged the input a prompt injection
    action: str | None        # ALLOW / FLAG / BLOCK (or ERROR when the call failed)
    blocked: bool             # True when the request would be refused
    layer: str | None         # the layer that decided
    verdict: str | None       # CLEAN / INJECTION / DEGRADED
    rule: str | None
    score: float | None
    latency_micros: int | None
    error: str | None = None


@dataclass
class LayerVerdicts:
    """Each enabled layer's independent verdict on one prompt."""
    flags: dict[str, bool]    # layer name -> flagged (True = that layer alone called it an injection)
    error: str | None = None


class BulwarkClient:
    def __init__(self, base_url: str = "http://localhost:8080", timeout: float = 30.0):
        base = base_url.rstrip("/")
        self.url = base + "/v1/screen"
        self.layers_url = base + "/v1/screen/layers"
        self.timeout = timeout

    def screen(self, text: str) -> Decision:
        body = {"model": "eval", "messages": [{"role": "user", "content": text}]}
        try:
            resp = requests.post(self.url, json=body, timeout=self.timeout)
            resp.raise_for_status()
            data = resp.json()
        except Exception as e:  # network error, non-2xx, bad JSON - record, don't crash the run
            return Decision(False, "ERROR", False, None, None, None, None, None, error=str(e))

        verdict = data.get("verdict")
        return Decision(
            flagged=(verdict == "INJECTION"),
            action=data.get("action"),
            blocked=bool(data.get("blocked")),
            layer=data.get("layer"),
            verdict=verdict,
            rule=data.get("rule"),
            score=data.get("score"),
            latency_micros=data.get("latencyMicros"),
        )

    def screen_layers(self, text: str) -> LayerVerdicts:
        """Each enabled layer's independent verdict, for per-layer measurement."""
        body = {"model": "eval", "messages": [{"role": "user", "content": text}]}
        try:
            resp = requests.post(self.layers_url, json=body, timeout=self.timeout)
            resp.raise_for_status()
            data = resp.json()
        except Exception as e:
            return LayerVerdicts(flags={}, error=str(e))
        flags = {layer["layer"]: bool(layer.get("flagged")) for layer in data.get("layers", [])}
        return LayerVerdicts(flags=flags)
