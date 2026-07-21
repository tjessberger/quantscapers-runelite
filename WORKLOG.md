# WORKLOG

Append-only session log for any agent (Claude, Codex, Antigravity, or
whatever comes next) working in this repo. Read this alongside
`AGENTS.md`/`WORKSPACE_AGENTS.md`/`PLUGIN_HUB_SUBMISSION.md` before
starting work — this file tells you what actually happened recently and
what's still loose; those files tell you the standing rules and
submission status. Update this before ending a session: what changed,
what's uncommitted, what you deliberately left undone.

Keep entries short and dated. This is a recent-session log, not permanent
history — `git log` and commit messages are the permanent record. Prune
old entries periodically rather than letting this grow forever.

---

## 2026-07-21 — DEVELOPMENT LOCKED, awaiting Plugin Hub review

Codex submitted an updated Economy Toolset package to the open Plugin
Hub PR (`runelite/plugin-hub#14066`), expanding the product from a
flip-focused tool into Market Overview + Flip Scanner + Alch Scanner +
Item Research + Deep-Probe Audits + local Watchlist. PR description and
title updated to match; pinned commit updated.

**Per `PLUGIN_HUB_SUBMISSION.md`'s own guardrails: feature scope is now
frozen.** No new features until a maintainer reviews. During the freeze,
only fixes required for approval are in scope — see that file's
"Submission guardrails" section before touching anything here.

**Uncommitted / local-only:** none — tree was clean as of this entry
(`git status` verified).

**Deliberately not done:** everything not required for Plugin Hub
approval. Do not resume feature work without the owner explicitly
lifting the freeze.

---
