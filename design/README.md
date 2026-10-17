# Phnomy — Focus App UI

Figma Design: https://www.figma.com/design/alcAGVEVKA4z9srUYZVPKj

Interactive prototype: https://www.figma.com/proto/alcAGVEVKA4z9srUYZVPKj?node-id=6-120&starting-point-node-id=6%3A120&scaling=scale-down

Editable overview: https://www.figma.com/design/alcAGVEVKA4z9srUYZVPKj?node-id=12-925

## Delivered design

- 13 main light screens: single focus, pomodoro, allowlist, permissions, focus, break, blocked app, exit reason, exit waiting, completion, statistics, history, settings.
- 3 dark samples: home, focus and statistics.
- Editable Android frames at 412 × 915, Noto Sans TC and Roboto Mono.
- Warm paper #F7F7F2, forest green #274C3D, graphite #202A24.
- Semantic variables, shared type styles, Material 3 controls, and reusable component families for buttons, modes, time choices, app rows, metrics, navigation, timers, switches, and notices.
- Supplemental sheets and states cover permissions, missing usage data, first use, interrupted protection, selected apps and deliberate early exit.

## Prototype use

Start from the flow named “01 · 完整專注流程”, or choose a scenario from the Figma flow selector. Other starting points demonstrate pomodoro, app interception, early exit, missing usage access, and interrupted protection.

The prototype uses one-action navigation with explicit branch screens, compatible with the connected Starter plan. Branch frames on the right preserve the selected duration and the stage to return to when canceling an exit.

Click an active countdown to demonstrate the end of that stage. Pomodoro demonstrates the first focus interval, its protected break, and the next interval. Time values and typed input are representative design states. System permission handoffs are explanatory overlays; no phone settings are changed.

Early exit requires a reason state, then an actual 60-second prototype timeout before confirmation becomes available. Canceling returns to the originating focus or break state. The canvas guide also links directly to the wait-complete state for review.

Instagram selection, 25/50-minute presets, a custom 30-minute example, DND toggling, period tabs, themes and primary navigation are connected. Search input and the other app rows show editable visual states.

## Validation

Structural inspection confirmed 75 screen/panel frames including branch states, 1,201 text layers and 390 component instances in the inspected flows. There are no raster image fills, missing product fonts, body overflow, invalid navigation destinations, or invalid exit-ready states. All seven exit-wait branches use a 60-second timeout. Six prototype flow starts are configured.

Composition screenshots were inspected for the entry flow, insights, exit flow and editable overview; the repaired exit-ready state was also inspected. Prototype links were verified through Figma reaction readback. Runtime phone behavior is outside this design deliverable.

`figma-state.json` records the current file and node identities. `nodes-*.json` files preserve construction and mutation ledgers. `qa-structural.json` contains the final inspection result. The JavaScript files are construction sources intended for the Figma Plugin API environment, with `helpers.js` prepended and recorded state supplied as `STATE`.

`Phnomy-UI-overview.png` is a preview export; the design itself remains editable in Figma.
