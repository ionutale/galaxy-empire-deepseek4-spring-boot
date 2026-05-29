# DB-polling scheduled executor game loop

The game uses a `@Scheduled` task every 5 seconds to poll a database `events` table for due actions (`due_at <= now()`), processing build completions, research, fleet arrivals, and resource accrual. Resource generation is computed on-read via timestamp deltas (`(now - last_collected) * rate_per_hour`). Combat resolves immediately upon fleet arrival as an automated calculation. STOMP over WebSocket pushes events to connected clients.

Considered alternatives: Quartz event-scheduling (fragile on restart), tick loop (wasted cycles on empty ticks). DB-polling survives restarts trivially, is simple to debug, and the 5-second delay is imperceptible in a game where actions take minutes or hours.

The frontend is a separate React SPA with routes matching the original's tab bar: Overview, Resources, Facilities, Shipyard, Research, Fleet, Galaxy, Defense, Alliance. Communicates with the backend via REST + STOMP WebSocket.
