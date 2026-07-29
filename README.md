# Bulwark

**A measured LLM prompt-injection firewall** — a language-agnostic proxy that screens LLM traffic for prompt injection in three layers, and honestly measures what each layer catches, misses, and wrongly blocks.

> Status: **Day 1 — pass-through proxy.** Screening layers, evaluation, and the public dashboard are added on later days. See the build plan in the project spec.

## What's here (Day 1)

An OpenAI-compatible proxy. Point any OpenAI client's base URL at Bulwark and it forwards `POST /v1/chat/completions` to the upstream provider unchanged, relaying the response. The default upstream is Anthropic's OpenAI-compatible layer (Claude models); swap `UPSTREAM_BASE_URL` to target any other OpenAI-compatible provider. Detection runs *before* the forward, starting Day 2 (see the hook in `ChatProxyController`).

```
bulwark/
├── pom.xml
├── Dockerfile                     # Railway build
├── docker-compose.yml             # local Postgres (used Day 3+)
├── .env.example
└── src/main/java/com/bulwark/
    ├── BulwarkApplication.java
    ├── HealthController.java       # GET /health
    ├── config/UpstreamProperties.java
    └── proxy/
        ├── ChatProxyController.java   # POST /v1/chat/completions  (Day 2 screening hook)
        └── UpstreamClient.java        # forwards to the upstream provider
```

## Run locally

Requires JDK 21 and Maven (or use the Docker path below).

```bash
export ANTHROPIC_API_KEY=sk-ant-...   # your upstream provider key (Claude by default)
mvn spring-boot:run
```

Verify:

```bash
# health
curl http://localhost:8080/health
# -> {"status":"ok"}

# proxied completion (uses your ANTHROPIC_API_KEY against the upstream provider)
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"claude-3-5-haiku-latest","messages":[{"role":"user","content":"hello"}]}'
```

**Done when:** an OpenAI client pointed at Bulwark's URL gets a normal completion back.

## Configuration

| Env var | Default | Purpose |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | — | Upstream provider API key (required); used as the bearer token |
| `UPSTREAM_BASE_URL` | `https://api.anthropic.com` | Upstream provider base URL (Anthropic's OpenAI-compatible layer) |
| `PORT` | `8080` | Injected by Railway in production |

## Deploy to Railway

1. Create a Railway project (Hobby plan) and add a PostgreSQL database (used from Day 3).
2. Connect this GitHub repo — Railway builds via the `Dockerfile` and auto-deploys on push to `main`.
3. Set `ANTHROPIC_API_KEY` in the service's Variables.
4. Hit `https://<your-service>.up.railway.app/health` → expect `{"status":"ok"}`.

## Next

- **Day 2** — Layer 1 regex screening + block / flag-only modes (hook is marked in `ChatProxyController`).
- **Day 3** — audit log to Postgres, Micrometer metrics, public Grafana dashboard.
