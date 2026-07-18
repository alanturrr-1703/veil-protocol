# Veil Protocol: Neon City — Game Rules

A confidential social-deduction game set in a neon-drenched city. The **City** is trying to
survive; the **Shadows** are hunting it from within. Every operative is either played by a
human or by a local LLM agent — and the AI agents play by exactly these rules.

---

## 1. The Setup

- **8 operatives** per match.
- **2 Shadows** (the hidden faction / "mafia"), **6 City** operatives.
- Roles are dealt secretly and committed to the confidential (Midnight) layer as hashes.
  No client is ever told anyone else's role.

### Factions & win conditions

| Faction | Members | Wins when |
|---------|---------|-----------|
| **City** | Oracle, Aegis, Citizens | Every Shadow has been eliminated. |
| **Shadows** | the 2 Shadows | The Shadows equal or outnumber the living City. |

---

## 2. Roles

- **Shadow** (×2) — the hidden killers. Each night the Shadows **coordinate a single kill**.
  They know each other. They must blend in by day and deflect suspicion.
- **Oracle** (detective) — each night may **investigate one operative** and privately learn
  their faction (City or Shadow). Must share intel carefully without exposing themselves.
- **Aegis** (protector) — each night may **shield one operative**; a shielded target
  survives an attack that night.
- **Citizen** (×3) — no night power. Gathers information by moving, questioning NPCs, and
  reasoning in the day, then votes.

---

## 3. Phases (a live, timed loop)

The match runs itself on a real-time clock. When a phase's timer expires it auto-advances.

1. **NIGHT — 45 seconds.** The Shadows have exactly this long to choose and file their kill.
   In the same window the Oracle investigates and the Aegis shields. Night actions are
   secret and resolve together at the end of the phase (Shield → Attack → Investigate).
2. **DAY — 60 seconds.** The body (if any) is announced. Everyone talks openly, trades
   suspicion, and shares (or fakes) information.
3. **VOTE — 30 seconds.** The city votes to exile one suspect. The most-voted operative is
   eliminated and their role is NOT revealed.
4. Back to **NIGHT**. Repeat until a faction wins (checked via the confidential layer after
   each resolution).

---

## 4. Movement, Districts & Rooms

The city is **6 districts** connected as a graph: Neon Plaza, Data Market, Rust Docks,
Spire Tower, Glitch Alley, Hydro Garden. You may move to an **adjacent** district at any
time — including in the lobby, before the match starts.

Each district contains a few **rooms**: the open **Commons** plus side rooms (e.g. the Data
Market's *Back Room* and *Server Cage*). Rooms are for **hiding**.

### Visibility (this is the core secret-keeping rule)

- **You only see who shares your exact room.** Operatives and NPCs in the same district but
  a different room are invisible to you.
- Districts publicly show only an **anonymous head-count** — how many living operatives are
  there, never who.
- Slip into a side room and you vanish from everyone in the Commons; walk into a room to
  discover who (or which NPC) is hiding there.

### Anti-camping

A **Citizen or Oracle may not spend two nights in a row in the same district.** Anyone who
fails to relocate during the day is automatically moved to an adjacent district at nightfall.
Shadows and the Aegis may hold position.

---

## 5. Communication

- **City chat (Day):** open channel, postable during Day and Vote. The dead may read it but
  never post.
- **Shadow chat (Night):** private to living Shadows, so they can coordinate the kill.
- **The Fallen (Dead) chat:** the eliminated talk among themselves, invisible to the living.
- **Whispers (proximity chat):** walk up to another operative in your room and whisper them
  privately — only the two of you can read it. You can only whisper someone in the **same
  room**; step away and the line goes dead.

AI operatives **converse back**: talk in Day/Shadow chat or whisper an AI, and it responds
in character, consistent with its role and these rules.

---

## 6. NPCs (witnesses)

NPCs are **not players** and have **no role**. They are knowledge-holders who wander the
city and witness events.

- An NPC only ever knows **what it personally saw** — it has no access to global state or to
  anyone's hidden role.
- **NPCs never lie**, and can **never name a killer with certainty**. For a killing an NPC
  records the victim and the set of people who were *nearby* — so the best it can offer is
  "it could have been one of these few."
- **NPCs never reveal or guess a role** and never accuse anyone of being a Shadow.
- You must be **in the same room** as an NPC to talk to it. Ask it anything in natural
  language; it answers from memory (and says plainly when it saw nothing relevant).
- NPCs can also **hide in rooms**, just like players.

---

## 7. Confidentiality (why this is a "Veil" game)

The Java engine is the single source of truth. Every message a client receives is a
**per-viewer redaction**: your own role, your own investigation results, your own whispers,
the chat you're cleared for, and only the whereabouts of people in your room. Confidential
state (roles, night actions, memories, exact positions of others) never leaves the server
except as the narrow slice you're authorized to see. Role commitments live in the Midnight
layer as hashes; the winner is resolved confidentially from the living set.

---

## 8. How the AI plays

Each AI operative is driven by a local LLM (Ollama) and is given a briefing of **these
rules plus only its own secret role** (and, for a Shadow, its partner). From that it:

- takes night actions appropriate to its role (Shadows coordinate a kill, Oracle
  investigates, Aegis shields);
- discusses and reacts in Day/Shadow chat, and answers whispers, staying in character and
  never revealing a role it shouldn't;
- votes for who it believes (or wants the city to believe) is a Shadow;
- wanders the districts and rooms so the city always feels alive.

No AI is ever told another operative's hidden role — the same veil that protects human
players protects the game from the AI, too.
