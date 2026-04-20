# History: Peterman

**Project Context:**
GemOfGemma - an Android application using Gemma models via LiteRT/LlmInference.
Team is working on refining the UI across Chat, Vision, and the newly added Audio tabs.

## Learnings
* 2026-04-19: Joined the team to take over UI/UX design duties from George, ensuring peak Compose and Material 3 quality.

* 2026-04-19: Reviewed George's baseline UI screenshot. Diagnosed missing Material 3 conventions, typography, and spacing issues to establish a baseline for the upcoming UI overhaul.

* 2026-04-19: **Vision tab deep review (Caption result state on real device).** Key findings:
  - **P0 — Caption text clips without scroll.** `CaptionResultPanel` in `CameraScreen.kt:823-890` has no scrollable container. `FrostedBottomCard` with `maxHeightFraction=0.35f` + `padding(bottom=100.dp)` physically cannot display long captions.
  - **P0 — Retake button WCAG failure.** White text on `#00E676` green = ~1.7:1 contrast. Must use dark text or darker green.
  - **P1 — Share icon 36dp violates M3 48dp touch target.** Also opacity 0.7f looks disabled.
  - **P1 — FrostedBottomCard uses magic-number 100dp bottom padding.** Brittle across devices.
  - **P1 — Retake button is raw Surface+clickable, not M3 Button.** Missing ripple + accessibility role.
  - The frosted glass effect, typewriter animation, and mode accent system are genuinely premium patterns worth preserving.
  - Key files: `CameraScreen.kt` (all result panels), `NavGraph.kt` (bottom nav + scaffold), `Color.kt` (mode accents), `Theme.kt` (M3 scheme).
  - Decision logged: `.squad/decisions/inbox/peterman-vision-ui-review.md`
