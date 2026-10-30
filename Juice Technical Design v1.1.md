# JUICE: Technical Design & System Architecture
**Version:** 1.1.0
**Status:** Ready for Android MVP implementation
**MVP platform:** Android only. iOS and desktop are post-MVP (section 11).

---

## 1. Vision

**Juice** is an overlay-driven productivity and memory aid built around tactile feedback, liquid shader physics, and fast AI text classification.

It is a quick-access overlay, not a list app. Completing items yields **Juice** (XP). Completing items in quick succession sloshes liquid into the **Thirst Meter** (combo multiplier). Reaching **Satiated** boosts rewards and unlocks cosmetics (Flavors, Glass Cards, Audio Packs).

Juice is also a memory aid: capture is instant, and the overlay is one swipe away from anywhere.

---

## 2. MVP Scope

**In:** Android overlay, capture, AI intake, carousel, gestures, Thirst and Juice economy, flavors, local persistence, settings.

**Out (post-MVP):** cross-device sync, anti-cheat, iOS, macOS, Windows.

---

## 3. Stack (Android MVP)

| Layer | Choice | Why |
| :--- | :--- | :--- |
| Language / UI | Kotlin, Jetpack Compose | Overlay, services and shaders are all native Android work |
| Liquid shader | AGSL via `RuntimeShader` | Native GPU shader, no GLSL port needed. Requires API 33 |
| Min SDK | 33 | AGSL floor. Window blur (API 31) is covered |
| Persistence | Room (SQLite) | Standard, offline first |
| Audio | `SoundPool` for MVP | Low latency, polyphonic enough for short samples. Move to Oboe only if latency fails |
| Haptics | `VibrationEffect` and `HapticFeedbackConstants` | Native |
| AI | Groq API with strict JSON output, rules-based offline fallback | See section 7 |

Later platforms: reconsider Kotlin Multiplatform or Flutter once the Android core is proven. The core engine (economy, parsing, gestures) should stay free of Android types so it can move.

```
+-----------------------------------------------------------------------------+
|                    TOP-CENTER TRIGGER STRIP (overlay window)                |
+-----------------------------------------------------------------------------+
                                      |
                                      v
+-----------------------------------------------------------------------------+
|                             JUICE CORE ENGINE                               |
|   +---------------------------------+   +-------------------------------+   |
|   |  Carousel Scroll & Snap Physics |   | Gesture Recognizer ($1)       |   |
|   +---------------------------------+   +-------------------------------+   |
|   +---------------------------------+   +-------------------------------+   |
|   | Economy (XP, Thirst, Undo)      |   | Persistence (Room)            |   |
|   +---------------------------------+   +-------------------------------+   |
+-----------------------------------------------------------------------------+
               |                                            |
               v                                            v
+-----------------------------+              +--------------------------------+
|     AI INTAKE CLASSIFIER    |              |       GRAPHICS & AUDIO         |
| Groq JSON mode + fallback   |              | AGSL shader, haptics, SoundPool|
+-----------------------------+              +--------------------------------+
```

---

## 4. Android Overlay

### 4.1 Trigger strip
- A thin touchable strip at the **top center** of the screen, similar in spirit to the Pixel bottom gesture bar. **Swipe right** (left to right) on it, or tap it, to open the overlay. In the overlay the strip becomes a handle: swipe left on it, tap it, or press Back to close.
- Implemented as a small `TYPE_APPLICATION_OVERLAY` window (about 30% of screen width, about 24dp tall). Only the strip is touchable. The rest of the screen is untouched.
- Position and width are adjustable in settings, since camera cutouts and OEM status bar behavior vary.
- A top strip avoids the system back gesture zones on the left and right edges.

### 4.2 Service and permissions
- `ForegroundService` with a low-priority persistent notification. Declare a foreground service type (`specialUse`) for Android 14+.
- `SYSTEM_ALERT_WINDOW` only. Guide the user to the overlay permission screen on first run.
- **No `AccessibilityService`.** The strip does not need it, and avoiding it removes the main Play Store review risk. Revisit only if a feature truly requires it.

### 4.3 Overlay window
- Opening swaps the strip for a full-screen overlay window hosting the Compose UI. Closing returns to the strip.
- Render and animate only while the overlay is visible. Nothing runs per frame while it is closed.

---

## 5. UI and Liquid Shader

### 5.1 Layout
- **Left column (carousel):** vertical list with magnetic snap points. Items scale with proximity to the focal dot ($1.0\times \rightarrow 1.15\times$).
- **Center card:** frosted glass. Shows the current item. Gestures are drawn here.
- **Bottom bar:** AI text entry plus the **Thirst Reservoir** shader and multiplier badge.

```
+-------------------------------------------------------------+
|                    [ top strip ]                            |
|                                                             |
|   +--------+    +---------------------------------------+   |
|   | Task 1 |    |                                       |   |
|   +--------+    |      CENTER CARD (frosted glass)      |   |
|   | Task 2 |    |                                       |   |
|   +--[*]---+    |   "Finalize Mobile PRD Architecture"  |   |
|   | Task 3 |    |   Goal | Deadline: 18:00              |   |
|   +--------+    |                                       |   |
|   | Task 4 |    +---------------------------------------+   |
|   +--------+                                                |
|   +-----------------------------------------------------+   |
|   | [~ ~ ~ THIRST RESERVOIR ~ ~ ~]                 [8x] |   |
|   | [ Enter new note or task...                     ]   |   |
|   +-----------------------------------------------------+   |
+-------------------------------------------------------------+
```

### 5.2 Blur
- **Background blur** is a settings toggle. When on, the overlay uses Android 12+ window background blur (`Window.setBackgroundBlurRadius`, see docs/overlay-blur-flicker.md). When off, or when unsupported or battery saver is active, it falls back to a dark semi-opaque scrim with the same glass styling.
- Default: on where supported, off under battery saver. Blur is costly on low-end GPUs, so the toggle matters.

### 5.3 Liquid (particles + AGSL)
The Thirst Reservoir is a particle fluid behind the centre card (`com.juice.core.liquid`, pure Kotlin), drawn by `JuiceLiquidView`.

- **Sim:** Clavet-style viscoelastic particles (`LiquidSim`), stepped at a fixed 60 Hz in dp. Gravity comes from the accelerometer (`ScreenGravity`), so the juice runs to whichever side is down; a finger on the card pushes it, and carousel snaps and completions `kick` it sideways.
- **Froth:** shakes and fast swipes raise agitation (`Froth`), which turns surface juice to foam and grows rising bubbles that pop. Golden Honey is thicker and still (foam, no bubbles); Satiated keeps a gentle fizz.
- **Fill:** the meter sets the particle count (`LiquidEngine.particlesFor`); a full meter fills the card. New juice streams in from the side opposite gravity; drained juice leaves from the surface. Particles are a little bigger than the lab's (`h` 30 dp, not 26) so a full card is about 1,800 of them.
- **Shading:** each particle stamps a soft blob into a `RenderNode` (red juice, green speed, blue foam); an AGSL `RenderEffect` thresholds that field into a glossy surface in the flavour's accent and deep colours. Foam cells, bubbles and pops are sprites on top. On a software canvas, or if the shader fails to compile, it draws plain dots.
- **Performance:** the engine steps on its own thread (`LiquidRunner`) and hands each finished step to the UI as a `LiquidFrame`, so physics never holds up a UI frame. Particles go into the field in one `drawVertices` call, as do foam cells. The field is shaded at a fraction of the card's resolution and scaled up. The **Liquid quality** setting (Fast, Balanced by default, Best) picks particle size and that fraction (`LiquidQuality`). `adb logcat -s JuiceLiquid` prints step and draw times every few seconds while the juice moves.
- **Battery:** sensors and the frame loop run only while the view is shown. Shallow juice settles and the loop stops on its own. Deep juice keeps churning slowly inside (one density pass a frame cannot hold up a tall column), so after 3 seconds with no tilt, touch, kick or pour the loop stops anyway and the juice holds still. Reduce motion holds gravity straight down and ignores touches, kicks and shakes; turning particles off removes the froth.

---

## 6. Economy

### 6.1 Rules
- Completing an item earns **Juice** and adds to the **Thirst Meter** by item size. The meter drains slowly over time.
- Farming is allowed. The intended player is someone realistically ticking off many tasks. Anti-cheat for bogus tasks is post-MVP.
- **Deadlines raise value.** An item with a deadline is classified as a Goal and is worth more than the same item without one ("do laundry" vs "do laundry by 6pm"). Phrasing that adds a real deadline is fine and intended.
- The multiplier is read **before** the completion adds to the meter, so a completion never boosts itself.

$$\text{Juice Earned} = \text{Base XP} \times M_{\text{Thirst}}$$

| Type | Base XP | Meter fill on completion |
| :--- | :--- | :--- |
| Note | 10 | +5% |
| Task | 25 | +12% |
| Goal | 100 | +30% |
| Project | 500 | +50% |

All fill and drain values are tunables. Starting drain: 1% of the meter per hour.

### 6.2 Thirst multiplier
- **Thirsty** (0 to 25% full): $M = 1\times$
- **Hydrated** (26 to 75%): $M$ scales linearly from $2\times$ to $4\times$
- **Satiated** (76 to 100%): $M = 8\times$. Triggers carbonated particles and boosted audio pitch.

### 6.3 Undo and dismiss
- Every checkmark and X shows a short **undo** (about 10 seconds). Undo reverts the status, XP, and meter fill.
- **X dismiss** archives the item with no XP and no fill.
- No confirmation dialogs. Undo replaces them.

### 6.4 Flavor progression

| Level | Name | Visual | Audio |
| :--- | :--- | :--- | :--- |
| 1 | **Electric Citrus** | Amber-orange fluid, soft carbonation | Bright mechanical clicks, wet pops |
| 5 | **Berry Surge** | Deep magenta fluid, bubbles | Sub-bass thumps, fizzing loops |
| 10 | **Dragonfruit Obsidian** | Dark violet crystal glass, sparkling fluid | Metallic chime drops |
| 25 | **Golden Honey** | High-viscosity gold fluid, heavy physics | Acoustic bell chimes, deep splashes |

---

## 7. AI Smart Intake

### 7.1 Pipeline
1. User enters raw text.
2. An **optimistic Note card** appears instantly (0ms UI delay).
3. A background worker sends the text to the parser with the current date and timezone.
4. The parser returns JSON and the card updates to `Note`, `Task`, `Goal`, or `Project`.

### 7.2 Parser requirements
- Use the provider's **JSON mode or schema-constrained output**. Do not rely on a "no markdown" instruction alone.
- Inject current date, time, and timezone into every request so "tomorrow at 6" resolves correctly.
- **Realistic boundaries:** the deadline must be a valid future ISO-8601 time within one year, otherwise it is null. `sub_items` is capped at 10. Titles are capped in length. Validate the response against the schema and fall back to a plain Note on any failure.
- Users may override the type on the card at any time.

### 7.3 Failure and edit handling
- **Offline or API failure:** the card stays a Note, is queued, and retries with backoff. A simple rules-based fallback (a date or time phrase means Goal) runs locally.
- **User edits before the parse lands:** user edits win. A late parse result only fills fields the user has not touched, and never overwrites the title.
- **Project sub-items:** each sub-item becomes its own card linked to the Project. Completing all of them completes the Project.
- **Privacy:** the MVP sends input text to a cloud endpoint. Onboarding discloses this. Settings include an "on-device only" toggle that uses the rules-based fallback and sends nothing.

### 7.4 Prompt
Current date and timezone are injected at the top of the system prompt.

```text
Today is {date}, timezone {tz}.
You are the Juice Intake Parser. Output only JSON matching the schema.

Types:
- "Note": plain thoughts or facts.
- "Task": an actionable item.
- "Goal": a task with a specific date, time, or target metric.
- "Project": an item with multiple sub-steps.

Schema:
{
  "type": "Note" | "Task" | "Goal" | "Project",
  "title": "string",
  "completion_condition": "string | null",
  "deadline": "ISO-8601 string | null",
  "sub_items": ["string"] | null
}
```

### 7.5 Reminders and notifications

Reminders are local notifications. Nothing is pushed from a server.

- **Groq chooses the time and text.** The parser may return `notify_at` (ISO-8601) and `notify_text` for a Task, Goal or Project. They are kept or dropped together. `notify_at` is dropped when it is unparsable, in the past, over a year out, or later than the `deadline`. `notify_text` is dropped when blank or when there is no valid `notify_at`. A rejected value never fails the parse; the item just has no reminder. A Routine ignores `notify_at`, because its schedule comes from its cadence.
- **Routine timing.** A Routine notifies on its next due date at 08:00 local time, or at its cadence time when that is earlier than 08:00. The text is the Routine title. An item without `notify_text` falls back to its title.
- **One alarm per item.** `ReminderScheduler` sets one inexact, idle-tolerant alarm (`setAndAllowWhileIdle`) at the planned time, so no exact-alarm permission is needed. The plan (`core/notify/ReminderPlan.kt`) returns nothing for a time that is not after now.
- **Cancel and reschedule.** Scheduling an item first cancels its existing alarm, then sets a new one only if the item still qualifies. So the alarm is cancelled or replaced when the item changes, is completed or dismissed (it is no longer Active), or becomes a Character. After a reboot, `BootReceiver` reschedules every item, since alarms do not survive one.
- **Posting.** When the alarm fires, the receiver posts the notification on the `juice_reminders` channel, if the `POST_NOTIFICATIONS` permission is granted. Without it, nothing is posted.
- **Tap opens the overlay.** The notification's content intent starts the overlay service and opens the overlay.
- **Out of scope:** server push, exact alarms, recurring or snoozable reminders, notification action buttons, and user-editable reminder times.

---

## 8. Gestures

- **Recognizer:** template matching with the $1 recognizer (or $P), with one template set for checkmark and one for X. This tolerates varied handwriting better than fixed vector rules. Reject below a confidence threshold and show a subtle "not recognized" shake.
- **Drawing vs scrolling:** scrolling happens outside the card (carousel column). Drawing happens only inside the center card. No ambiguity by region.
- **Tap alternative:** a settings option shows Complete and Dismiss buttons on the card, for accessibility and for anyone who prefers tapping.
- **Comfort settings:** toggles for haptics and reduce motion (disables slosh, particles, and heavy animation).

---

## 9. Audio and Haptics

| Trigger | Audio | Haptic |
| :--- | :--- | :--- |
| Carousel snap | Plastic click, 200Hz drop | selection tick |
| Checkmark | Wet citrus squeeze, ascending chime | medium impact |
| X dismiss | Low dry thud | double low buzz |
| Satiated peak | Carbonated fizz, +4 semitones | continuous low rumble |

All sounds and haptics respect the settings toggles above.

---

## 10. Roadmap (Android MVP)

### Phase 0: Overlay spike (Week 1)
MVP testing is scoped to Pixel devices only.

- [x] Top-center strip with swipe-right (or tap) to open, on Pixel.
- [ ] Verify behavior on Pixel with gesture nav and 3-button nav.

### Phase 1: Core (Weeks 2 to 4)
- [x] Overlay shell, carousel with snap physics, center card.
- [x] $1 gesture recognizer for checkmark and X, undo.
- [x] Room persistence.

### Phase 2: Feel (Weeks 5 to 6)
- [x] AGSL reservoir with damped spring slosh.
- [x] SoundPool mixer and haptics.
- [x] Thirst meter, drain, multiplier, XP.
- [x] Blur toggle with fallback.

### Phase 3: Intelligence and progression (Weeks 7 to 8)
- [x] Groq intake with JSON mode, date injection, validation, offline fallback.
- [ ] Groq key set as the `GROQ_API_KEY` repository secret, so CI-built APKs use cloud intake. Without it they run on the local rules only.
- [x] Levels and flavor switching.
- [x] Settings screen: tap alternative, haptics, reduce motion, blur, on-device only, strip position.

---

## 11. Post-MVP

- **Sync** across devices.
- **Anti-cheat** for bogus tasks.
- **iOS:** sandbox blocks system-wide overlays. Plan: standalone app, Control Center widget or Action Button deep link (`juice://capture`), and a Share Sheet extension into the AI parser.
- **Desktop (macOS, Windows):** borderless always-on-top transparent window, opened by edge hover or a global hotkey.

---

## 12. Risks

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| Top strip behaves differently across OEMs, cutouts, and status bar gestures | Core entry point unreliable | MVP scoped to Pixel; other devices post-MVP. Adjustable strip position and width |
| Overlay permission friction on first run | Drop-off at onboarding | Short guided permission flow, clear explanation |
| Foreground service killed by aggressive OEM battery management | Strip disappears | Foreground service, battery optimization exemption prompt, restart on boot |
| Shader and blur battery or thermal cost | Poor reviews, throttling | Render only while visible, blur toggle, reduce motion, cap frame work |
| Cloud intake sends user text off device | Privacy concerns | Onboarding disclosure, on-device only toggle |
| LLM misclassification or malformed output | Wrong XP, bad cards | Schema validation, boundaries, user type override, Note fallback |
| Meter farming by bogus tasks | Economy inflation | Accepted for MVP. Revisit with anti-cheat post-MVP |
| Accidental gestures | Lost progress | Undo on every completion and dismiss |
| Play Store policy on overlays | Rejection | No AccessibilityService, clear disclosure of overlay use |
