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

# Project Instructions for Claude Code

## Terminal & Tool Permissions
You have full unrestricted permission to run terminal commands, edit files,
and use any tool — you do not need to ask for confirmation before acting.

However, you MUST follow these hard rules at all times:

### Never do these, under any circumstances:
- Never run `git push` (to any remote/branch) without explicitly telling me
  first and getting a clear "yes"
- Never run `git reset --hard`, `git rebase`, or rewrite git history
- Never delete files or folders (`rm`, `rm -rf`, `del`) without listing
  exactly what you're about to delete first, in your response
- Never run destructive database commands (DROP, TRUNCATE, DELETE without WHERE)
- Never modify `.env`, config files with secrets/API keys, or files outside
  this project's folder
- Never install global packages (`npm install -g`, `pip install --user`
  system-wide) — project-local installs only
- Never overwrite existing files wholesale when a smaller targeted edit
  would do

### Always do these:
- Before any git commit: show me what files changed and a one-line summary,
  in the same response as the commit — don't commit silently
- If a command could be destructive or hard to undo, pause and ask me
  explicitly, even though you generally don't need to ask
- If you're unsure whether something falls into the "never" list above,
  treat it as if it does — ask first

## Code Comments
- Add comments only where the code isn't self-explanatory
- Keep comments concise — one line where possible
- All comments in English

## After Every Change
At the end of each response, include a short summary:
- What was changed (1-2 sentences)
- Which files were modified/created/deleted

## Additional guidelines
- Follow the existing code style already in the project
- Don't create new files unless necessary; prefer editing existing ones
- If a change is large or affects multiple files, briefly state the plan
  before starting


## Terminal & Tool Permissions
You have full unrestricted permission to run terminal commands, edit files, 
and use any tool — you do not need to ask for confirmation before acting.

However, you MUST follow these hard rules at all times:

### Never do these, under any circumstances:
- Never run `git push` (to any remote/branch) without explicitly telling me 
  first and getting a clear "yes"
- Never run `git reset --hard`, `git rebase`, or rewrite git history
- Never delete files or folders (`rm`, `rm -rf`, `del`) without listing 
  exactly what you're about to delete first, in your response
- Never run destructive database commands (DROP, TRUNCATE, DELETE without WHERE)
- Never modify `.env`, config files with secrets/API keys, or files outside 
  this project's folder
- Never install global packages (`npm install -g`, `pip install --user` 
  system-wide) — project-local installs only
- Never overwrite existing files wholesale when a smaller targeted edit 
  would do

### Always do these:
- Before any git commit: show me what files changed and a one-line summary, 
  in the same response as the commit — don't commit silently
- If a command could be destructive or hard to undo, pause and ask me 
  explicitly, even though you generally don't need to ask
- If you're unsure whether something falls into the "never" list above, 
  treat it as if it does — ask first

## Code Comments
- Add comments only where the code isn't self-explanatory
- Keep comments concise — one line where possible
- All comments in English

## After Every Change
At the end of each response, include a short summary:
- What was changed (1-2 sentences)
- Which files were modified/created/deleted

## Additional guidelines
- Follow the existing code style already in the project
- Don't create new files unless necessary; prefer editing existing ones
- If a change is large or affects multiple files, briefly state the plan 
  before starting