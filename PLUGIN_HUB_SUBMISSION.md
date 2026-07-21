# RuneLite Plugin Hub Status

QuantScapers has been submitted to the RuneLite Plugin Hub from the GitHub
baseline supplied by the user. Treat the submission as pending review.

Do not update the Plugin Hub PR, change its pinned commit, or resubmit the
plugin unless the user explicitly requests it.

## Current product scope

QuantScapers is an OSRS Economy Toolset with market movers, Flip and Alch
scanning, item research, Deep-Probe Audits, and a local Watchlist. It remains
decision support only: there is no Grand Exchange automation.

## Network and compliance summary

- The plugin calls only the OSRS Wiki Real-time Prices API.
- Every request uses the configured contact email in its User-Agent.
- The plugin does not request data until a valid contact email is configured.
- Market data refreshes every 30 seconds while the panel is open and pauses
  when it is closed.
- Automatic audit calls remain capped at three per 30 minutes; manual audits
  use their existing local cap.
- There is no proxy, QuantScapers backend call, player-data collection,
  telemetry, cloud sync, or account system.

## If a future Plugin Hub update is explicitly requested

1. Identify the exact approved `main` commit to pin.
2. Update the Plugin Hub repository reference to that 40-character commit.
3. Run the Plugin Hub checks and address only requested review feedback.
4. Do not change the plugin's API usage, automation posture, or privacy model
   as part of a metadata-only update.
