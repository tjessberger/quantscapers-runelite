<!-- SYNCED COPY — canonical source is `Web Projects/AGENTS.md` (one level up,
     outside this repo). Don't hand-edit; edit the canonical file and
     re-copy to all project repos instead, or changes will drift. -->

This file is the **single source of truth** for workspace rules, so any
agent (Claude Code, Antigravity, Codex, or another tool) can pick up
exactly where the last one left off. Claude Code's `~/.claude/CLAUDE.md`
and its persistent memory just point here — if anything disagrees with
this file, this file wins; update the other copy to match rather than
silently picking one.

Each project folder also has its own `AGENTS.md` with project-specific
architecture. Read this file first, then the project's own.

Each project repo also has a `WORKLOG.md` — a short, append-only log of
recent sessions' work (including anything uncommitted/local-only, and
anything deliberately left undone), separate from the standing rules in
`AGENTS.md`. Read it before starting work in that repo, and update it
before ending a session, whichever agent you are. Keep entries short and
recent — this is a perishable session log, not permanent history;
`git log`/commit messages are the permanent record. Prune old entries
rather than letting the file grow forever.

## How this workspace is laid out

Every immediate subfolder of `Web Projects` (e.g. `quantscapers.com`,
`tjessberger.com`) is:
- a live git repo, working directory == repo root (not a copy/mirror step)
- pushed to `https://github.com/tjessberger/<folder-name>` (same name,
  case-sensitive)
- deployed by Cloudflare via **two branches**: `main` → the live domain,
  `dev` → `dev.<domain>` (a separate Cloudflare Worker watching the same
  repo). Pushing either branch auto-deploys — there is no manual
  Cloudflare dashboard step for site files.

A repo with a `worker/` subdirectory (currently only quantscapers.com)
also has a **backend Worker**, deployed separately by a GitHub Actions
workflow (`.github/workflows/deploy-worker.yml`) that runs
`wrangler deploy` automatically whenever a push touches `worker/**`.
**Never run `wrangler deploy` manually** when that workflow exists —
let the Action do it, then verify.

## Behavior rules (apply every session, every project)

1. **Never deploy without being explicitly told to, that turn.** Never
   push to a repo's `dev` or `main` branch (i.e. never run the
   dev-deploy or prod-deploy procedures below) unless the user
   explicitly asks for it in that message. This is low-stakes work —
   the user tests changes locally and reports back before deciding on
   further edits or a deploy. Make the requested edits, then **stop**.
   Don't propose a deploy or chain edit → dev-deploy → verify →
   prod-deploy on your own initiative.
2. **Keep responses terse.** Results and next actions only — no
   narrated reasoning, no "why," no elaboration on alternatives
   considered, unless asked.
3. **Any subagent or background task defaults to the cheapest/fastest
   available model at low effort** (in Claude Code: Haiku 4.5, low
   reasoning effort, set explicitly per task), unless the user names a
   specific model/effort for that task.
4. **Only make changes that are directly requested.** No unsolicited
   refactors, cleanups, or "improvements" bundled into a requested
   change.
5. Prefer plain, simple solutions — no unnecessary abstractions or
   dependencies added "for later."

## Procedure: sync (commit + push to `main`, no deploy verification)

Use when asked to "sync," "push," "back up," "save to GitHub," or
"commit" — not a full deploy, just getting local changes into git.

0. Confirm which project folder (never assume/guess if ambiguous).
1. `git status`. If not a repo: `git init && git branch -M main && git remote add origin https://github.com/tjessberger/<folder-name>`.
   If already a repo, `git remote -v` — if `origin` points elsewhere,
   **stop and ask**, don't repoint it silently.
2. Check `.gitignore` covers any actually-generated files you find in
   *this* project (don't copy patterns from another project that don't
   apply). Don't touch anything else already in it.
3. Show `git status` in full before doing anything — this is the
   transparency step. If the tree is clean, say so and stop.
4. `git add -A`, show what's staged, then commit with a message that
   describes the real diff (not "update files"), ending with:
   ```
   Co-Authored-By: <agent name> <noreply@...>
   ```
   Use a heredoc so multi-line messages format correctly.
5. Push (`git push -u origin main` if no upstream yet, else plain
   `git push`). **If rejected as non-fast-forward, stop and ask** —
   never force-push, never `--no-verify`, never amend an existing
   commit (always a new commit).
6. Show `git log -1 --stat` and the GitHub URL.

## Procedure: dev-deploy (push to `dev`, verify `dev.<domain>`)

**Only when the user explicitly says to deploy to dev.** Same commit
steps as sync above, but push to the `dev` ref only, never touching
local `main`'s tracking or remote `main`:

```bash
git push origin HEAD:dev
```

This never checks out a local `dev` branch — always operate from local
`main` and push straight to the remote `dev` ref. If rejected
non-fast-forward, stop and ask (don't force-push).

Verify per the "Deploy verification" section below, against
`dev.<domain>`.

There is no separate dev backend for quantscapers.com —
`dev.quantscapers.com` calls the same production API
(`api.quantscapers.com`) as the live site. Backend/Worker code changes
only take effect once promoted to `main`.

Report: commit hash + summary, dev branch updated, live-check result.
Link `https://github.com/tjessberger/<folder-name>/tree/dev`.

## Procedure: prod-deploy (push to `main`, verify the live site)

**Only when the user explicitly says to deploy to prod.** Same as sync,
pushed to `main`. Then:

1. Did the push touch `worker/`? If so, the GitHub Action fires — poll
   for a new deployment (`cd worker && npx wrangler deployments list`),
   baseline count first, then ~4x at 20s apart. **A manually-tested
   deploy just before pushing produces identical content, so no *new*
   entry appears even though the Action ran successfully** — verify
   via the Actions tab or a functional endpoint hit, don't assume
   absence of a new entry means failure. If genuinely stuck after
   ~3 min, ask the user to check the Actions tab (repos are private,
   no `gh` auth available here).
2. Verify per the "Deploy verification" section below, against the real
   `<domain>` (not `*.workers.dev`). For quantscapers, also hit
   `?endpoint=latest` on `api.quantscapers.com` and confirm JSON comes
   back.
3. Report: commit shipped (hash + summary), which deploy paths fired,
   Worker deployment status, live health-check results. State
   failures plainly — don't soften.

## Deploy verification (used by both dev-deploy and prod-deploy)

```bash
curl -sI "https://<domain>/?cb=$(date +%s)" --max-time 15 --resolve <domain>:443:104.21.17.125
```

Expect `Server: cloudflare` + HTTP 200. Poll ~4x at 15s apart — a mixed
or wrong result on the *first* check is normal edge-cache lag; only a
*stable* wrong result across 2+ spaced retries is a real failure. The
`--resolve` is deliberate: plain `nslookup`/`curl` without it can show a
stale DNS answer that looks like a failed deploy when it isn't (this
burned two debugging rounds during the original Cloudflare migration).
Before ever reporting "not propagated"/"still on the old host,"
cross-check: `nslookup <domain> 1.1.1.1` (forces a resolver, skips
local cache) and look for `Server: cloudflare`/`CF-RAY` headers on the
`--resolve` curl — the headers are the definitive signal, not the IP a
lookup returns.

A push whose content exactly matches an already-deployed version
produces no new entry in `wrangler deployments list`, even though the
deploy mechanism ran successfully — don't treat "no new deployment"
alone as proof of failure; check a live functional endpoint too.

## Never (all three procedures)

Force-push, amend, skip hooks (`--no-verify`), repoint an existing
`origin` without telling the user, guess the project name, run
`wrangler deploy` manually when a repo's Action already handles it,
declare success without actually verifying, or touch Cloudflare
dashboard settings (DNS, Custom Domains, Access policies, secrets) —
those are the user's to click; describe the exact steps instead.

## Known environment gotchas

- **Deploy propagation takes ~30–90s** after a push before the checks
  in "Deploy verification" above will pass — this is folded into the
  poll-and-retry guidance there, not a separate thing to remember.
- Both `quantscapers.com` and `tjessberger.com` have a `site-worker.js`
  + root `wrangler.jsonc` (`main` + `assets.binding` +
  `run_worker_first`) that intercepts specific paths (currently
  `/robots.txt`, plus `/market/*`, `/resources/*`, and
  `/sitemap-items.xml` for quantscapers) while everything
  else is served straight from static assets with zero Worker
  involvement. This is how `dev.<domain>` serves a blocking
  `robots.txt` without needing a git-committed file that diverges
  between branches (that used to make every dev push a merge
  conflict — don't reintroduce a dev-branch-only file for this).
