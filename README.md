# Veil Protocol — Neon City

A polished multiplayer **cyberpunk social-deduction game**. The City is trying to survive;
the Shadows hunt it from within. Players move through a neon metropolis, question NPC
witnesses, and deduce who the hidden Shadows are — while their own secret roles and night
actions are kept confidential and resolved trustlessly on **Midnight**.

## Architecture at a glance (HYBRID)

Veil Protocol is a **hybrid** system. It does **not** put the whole game on-chain. Instead:

| Layer | Responsibility | Tech |
|---|---|---|
| **Backend** (authoritative) | Real-time simulation: movement, pathfinding, NPCs, lobby/matchmaking, timers, proximity chat, event streaming | Java 21 · Spring Boot · WebSocket |
| **Confidential referee** | ONLY confidential state: role assignment, night actions, attack/shield/investigation resolution, vote & win verification | Midnight Compact (via a relayer) |
| **Client** (render-only) | Renders public state + the narrow slice a player is authorized to see; sends intents | React · TypeScript · Vite · Tailwind · Framer Motion · PixiJS |
| **NPC dialogue** | Phrases what an NPC actually witnessed (never the whole game state) | Ollama (local LLM) |

Java is the single source of truth for **public** state. Midnight is the confidential
referee for **secret** state. The client never computes rules — it renders what it is sent.

See [docs/architecture-v3.md](docs/architecture-v3.md) for the full design, diagrams,
interface catalog, and the incremental build plan.

## Monorepo layout

```
veil-protocol/
├── backend/     # Java 21 + Spring Boot — authoritative real-time game server
├── frontend/    # React + TS + Vite + Tailwind + Framer + PixiJS — render-only client
├── midnight/    # Midnight Compact contracts + runnable confidentiality demo
├── shared/      # Wire contract: JSON schemas + role constants shared by FE/BE
├── docs/        # Architecture, game rules, Midnight design
├── tests/       # Cross-cutting integration / end-to-end tests
└── docker-compose.yml   # Local Ollama + Midnight proof server
```

## Wallet model — Relayer

For fast onboarding and a smooth hackathon demo, **players do not connect wallets**. The
backend acts as a **relayer**, submitting confidential transactions to Midnight on players'
behalf through the `ConfidentialGateway` seam. The architecture is designed so this relayer
can later be swapped for per-player wallets without touching game logic.

## Run it locally

Prereqs: JDK 21+, Maven, Node 20+, (optional) Docker for Ollama / Midnight.

```bash
# 1. Side services (optional): local LLM for NPC dialogue
docker compose up ollama
docker exec -it veil-ollama ollama pull llama3.2:3b

#    ...and the Midnight proof server when running the real confidential layer:
docker compose --profile midnight up midnight-proof

# 2. Backend (authoritative server)  ->  REST :8080, WS ws://localhost:8080/ws/game
npm run dev:backend        # = mvn -f backend/pom.xml spring-boot:run

# 3. Frontend  ->  http://localhost:5173
npm install
npm run dev:frontend

# 4. Confidentiality demo (no toolchain needed)
npm run demo:midnight      # prints roles staying hidden while moves stay verifiable
```

### Real confidential referee (relayer)

By default the backend uses an in-process mock confidential layer. To route confidential
state through the **Midnight relayer** instead — the sidecar that submits transactions on
players' behalf — run the relayer and start the backend on the `midnight` profile:

```bash
npm --workspace @veil/midnight run relayer                              # relayer :6301
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=midnight
```

The relayer ships in local commitment mode (no toolchain) and runs end-to-end today; see
[docs/testnet-deployment.md](docs/testnet-deployment.md) to point it at Midnight testnet.

## Documentation

- [docs/architecture-v3.md](docs/architecture-v3.md) — final hybrid architecture (start here)
- [docs/game-rules.md](docs/game-rules.md) — how the game plays
- [docs/midnight-design.md](docs/midnight-design.md) — the confidential layer
