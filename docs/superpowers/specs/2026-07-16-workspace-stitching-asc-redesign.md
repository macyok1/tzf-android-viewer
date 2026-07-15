# Workspace stitching and ASC redesign

Approved by the user on 2026-07-16.

## Camera

- One-finger PAN follows screen pixels at every yaw/pitch: finger up moves the rendered scene up.
- ORBIT remains a floating 48 dp button. Tap toggles mode; long-press drags the button inside the viewport.

## Toolbar and X7

- Remove the permanent `Сшить с` toolbar button. Show the target selector only in the active X7 flow.
- Keep `CUT`, then `ASC`, then `⋯` as the final toolbar actions.
- Remove ASC, point size, point budget, and legacy R/M stitching from `⋯`.

## Registration sets

- Registration-set station rows expose familiar R and M selectors.
- R is the fixed station. M identifies the moving station and its entire registration set moves rigidly.
- Selecting R and M from different sets reveals one bottom `Сшить` action with `Ручная`, `Авто`, and `Уточнение`.
- A station can be detached from a registration set. Its incident links are removed, then all remaining stations are repartitioned by connected components without changing world poses or user folders.
- The old persistent workspace R/M flow is removed.

## ASC

- ASC is a dedicated toolbar action below CUT.
- Dialog asks whether to thin the project. No preserves the existing full export.
- Random thinning accepts target count or percentage.
- Spatial thinning accepts spacing in millimetres or target count; target count estimates spacing from a transformed sample before export.
- A locked CUT filters points in world coordinates before sampling/thinning and before count estimation. An unlocked CUT does not affect export.
- Export remains streaming and does not load the full raw project in memory.

