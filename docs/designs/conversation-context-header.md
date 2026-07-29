# Conversation Context Header

> Updated: 2026-07-29

## Design Intent

`ConversationContextHeader` is the persistent context affordance for the chat screen. It must stay compact so `ChatMessagesList` keeps the main reading space.

## Layout Rules

- The header summary remains inside the main `ChatScreen` column and only occupies the collapsed title/status row.
- The expanded system instruction is rendered by `ConversationContextDetailsOverlay` from the root `Box`, similar to `ChatHistoryPanel`.
- The details overlay must not participate in the main column measurement, so expanding it cannot reduce the `ChatMessagesList` `weight(1f)` area.
- Overlay z-order stays below `ChatHistoryPanel` and above the message list: header/main content `10f`, context details `35f`, history panel `45f`, snackbar `50f`.
- The details overlay uses the same responsive width as the header: full width on `ContentType.Single`, constrained `0.72f` width on wider layouts.

## Interaction Rules

- Tapping the header summary toggles the details overlay.
- Changing the active session or system instruction collapses the details overlay.
- Opening either the context details overlay or chat history closes the other secondary panel.
- The details overlay preserves instruction selection, vertical scrolling, and copy-to-clipboard behavior.
