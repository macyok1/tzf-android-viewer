# Instrument menus and dialogs

Approved from the visual preview by the user on 2026-07-16.

## Direction

Replace visually disconnected Android `PopupMenu` and input `AlertDialog` surfaces with a shared surveying-instrument UI matching the point-cloud workspace.

## Anchored menus

- Open next to the invoking control and flip direction when space is insufficient.
- Use a translucent graphite card, cool-gray one-pixel border, 16--18 dp radius, and strong restrained shadow.
- Each 56--68 dp row has a compact icon tile, action title, optional explanatory line, and optional state pill.
- Cyan indicates active/toggle state. Amber remains reserved for moving/editable scan state. Destructive actions use muted red.
- Outside tap and Back dismiss the menu.
- The workspace `⋯`, project-card `⋯`, scan menus, and registration menus share this component.

## Modal cards

- Dim and lightly blur the underlying screen where supported.
- Use a centered 320--440 dp graphite card with accent line, eyebrow, title, optional explanation, content, and aligned actions.
- Text input has a persistent field label, dark inset surface, cyan focus border, helper/error line, and automatic keyboard focus.
- Primary actions use cyan fill. Secondary actions use a bordered quiet button. Destructive confirmation uses muted red.
- Creation, copy, rename, delete, ASC choices, stitching choices, and X7 workflows share the visual shell.

## Projects screen

- Keep the main create action, but use the custom input card for naming.
- Project cards open on body tap and replace the four small actions with one overflow action containing Copy, Rename, and Delete.

## Responsiveness and accessibility

- Phone menus remain compact and anchored to the rail.
- Expanded layouts use wider cards without becoming full-screen dialogs.
- Touch targets remain at least 44 dp; rows and buttons expose meaningful content descriptions.
- Long lists become scrollable inside a bounded card.
