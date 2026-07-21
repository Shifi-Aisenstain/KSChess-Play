# Instructions for AI assistants working in this repo

Before exploring the codebase, reading source files broadly, or answering questions about how this
project is structured, **read [ARCHITECTURE.md](ARCHITECTURE.md) first.** It documents the package
layout, the real-time "no turns / cooldowns" game engine mechanics, the client/server network protocol,
and known dead code / rough edges. It exists specifically so a new session doesn't need to re-derive
this from scratch.

After reading it:
- For architecture/design questions, answer from the doc directly when it covers the topic.
- For bug fixes or feature work, still Read/Grep the actual files the doc points to before editing —
  the doc is a map, not a substitute for looking at current code. It can drift from the code over time;
  if something you observe contradicts the doc, trust the code and flag the mismatch to the user.
- If you make an architecturally significant change (new package, new protocol message type, removing
  one of the "dead code" items listed in ARCHITECTURE.md §9, changing the tick/cooldown constants),
  update ARCHITECTURE.md in the same change.
