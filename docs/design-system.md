# Juice design system

Everything is liquid. Every tap is a squeeze. The overlay is thin tinted glass with a drink inside it: things rise in like bubbles, slosh when touched and pop when they leave. Visual reference: the "Juice Design System" canvas (design system, Face Lab, visual novel chat).

## Principles

1. **Liquid, not flat.** Surfaces have weight and settle on springs with a little wobble.
2. **Every action is a squeeze.** Sound, haptic and froth land together.
3. **Rise in, pop out.** Content bubbles up with a sway; on close it swells, bursts into froth, then the ground drains.
4. **Glass over the world.** Panels are white at low alpha over the ground.
5. **Flavour is the accent.** One accent at a time (`Hud.accent`); everything else is ink, glass and white.
6. **Calm when asked.** Reduce motion turns off slosh, froth, bobbing and wobble. Layouts still read when still.

## Tokens (source of truth in code)

- Colour: `JuiceColors` (OverlayParts.kt), `FlavourTheme`, the ground gradient in `JuiceColors`' ground tokens (OverlayParts.kt: GroundTeal, GroundInk, GroundIndigo), used by `overlayTint` in OverlayBackdropLayer.kt, launcher colours in `res/values/colors.xml`.
- Glass: white 10% (fields), 14% (cards, inputs, bubbles, 1 dp border at 25 to 28%), 24% (focused carousel), black 28% (wells).
- Type: Space Grotesk 400/500/700, as `JuiceType` (OverlayParts.kt). Spurt 64, stat 34, card title 25/30 medium, sheet heading 20, dialogue 18/26 medium, name 18 bold, chat 15/21, body 14, button 14 bold, label 13, caption 12, badge 11 bold +1 tracking. Line heights 30, 26, 21 and 13.
- Radius: `JuiceShapes` (OverlayParts.kt). Card 30, input 28, pill 24 (48 high), bubble 16, badge 14, field 12, capsule 50% (anything whose radius is half its height). Touch targets at least 44 dp: clickable controls shorter than that use the `Modifier.touchTarget` helper, which grows the tappable area and leaves the visible pill its own height.
- Motion: `OverlayMotion.kt` (reveal 320 ms, entrance 350 ms with 60 ms stagger, 1.5 sway cycles, pop swell 1.08, 14 froth bubbles).

## Sound and haptics

Sound and haptic land together on every action (principle 2). `FeedbackPlayer` (feedback/Feedback.kt) maps each `FeedbackEvent` to a `Cue` (synthesised by core audio `Synth.kt`) and a `Haptic` (`VibratorOutput` in AndroidFeedback.kt). New screens reuse an existing event rather than adding a sound.

| Event | Moment | Cue | Haptic |
| --- | --- | --- | --- |
| `SNAP` | the tap: a control or card snaps | `TICK` | `SELECTION_TICK` (system tick) |
| `CHECKMARK` | the complete (`playCompletion` pitches it up with the multiplier) | `DONE` | `MEDIUM_IMPACT` (system click) |
| `DISMISS` | a card is dismissed | `X` | `DOUBLE_LOW_BUZZ` (two 40 ms buzzes) |
| `SATIATED_PEAK` | the shake spurt, when the meter peaks (always played boosted) | `FIZZ` | `CONTINUOUS_LOW_RUMBLE` (400 ms) |
| `CAPTURE` | a thing is captured | `POP` | `SELECTION_TICK` |
| `NOT_RECOGNISED` | intake could not read the text | `BAD` | `REJECT` (two 10 ms pulses) |
| `UNDO` | the undo button | `UNDO` | `SELECTION_TICK` |
| `OPEN` | the overlay opens | `OPEN` | `SELECTION_TICK` |
| `LEVEL_UP` | a new level | `LEVEL` | `MEDIUM_IMPACT` |
| `RECAP_OPEN` | the recap opens | `RECAP_OPEN` | `MEDIUM_IMPACT` |
| `RECAP_REVEAL` | a recap figure is revealed | `RECAP_REVEAL` | `SELECTION_TICK` |
| `CARD_POP` | a card pops in or out | `POP` | none (sound only) |

Two more cues are sound only, never a haptic: `MUTTER` (a coach letter's blip, pitch clamped to 0.5 to 2) and the character greeting (`playGreeting`, seeded so each character is recognisable by ear). A boosted event (Satiated) plays 4 semitones up (rate 1.2599). A completion rises one semitone per heat point above 1x and never goes past 7 semitones up (rate 1.4983).

### Sound sets

Each flavour has a `SoundSet` (`SoundSet.kt`); the player hears the set of their flavour. Cues that are not listed below sound the same in every set.

| SoundSet | Flavour | Character |
| --- | --- | --- |
| Bright clicks and pops (`BRIGHT_CLICKS_AND_POPS`) | Electric Citrus | `TICK` is a bare dry click. `DONE` is a clean sine chord with a soft fifth above each note, a froth burst and a small pop on the end. |
| Sub-bass and fizz (`SUB_BASS_AND_FIZZ`) | Berry Surge | `TICK` has a low thump under the click. `DONE` is a plain sine chord with a deep sub-bass drop. `FIZZ` is the hissing spray the shake spurt uses in every set. |
| Metallic chimes (`METALLIC_CHIMES`) | Dragonfruit Obsidian | `TICK` gets a thin metallic tick on top. `DONE` is a long triangle chord with inharmonic overtones that ring like a struck bar, no extra tail. |
| Bells and splashes (`BELLS_AND_SPLASHES`) | Golden Honey | `TICK` is a round, low wooden knock. `DONE` is a chord an octave down, ringing a second like a bell, with a splash of water noise fading behind it. |

`POP` (the capture and card pop) is a short rising blip, `X` a low thud, `BAD` two flat buzzes, `UNDO` a falling blip, `OPEN` a rising sweep, `RECAP_OPEN` two soft notes, `RECAP_REVEAL` a bright triangle chirp and `LEVEL` a five-note rising run, all the same across sets.

### Switches

- **Sound off** (`FeedbackSettings.soundEnabled`): no cue, mutter or greeting plays. Haptics still fire.
- **Haptics off** (`hapticsEnabled`): no vibration. Cues still play.
- **Reduce motion** does not touch sound or haptics. It only stills the visuals (slosh, froth, bobbing, wobble); say it in the UI as motion, not as feedback.

## Component states

Every interactive component has these five states. Glass alphas are from the Tokens list, the accent is `Hud.accent`, shapes come from `JuiceShapes` and text from `JuiceType`.

| State | Look and motion (all components) |
| --- | --- |
| Default | The component's glass alpha, at rest. |
| Pressed | A squeeze: scale down a little on a spring and let it overshoot back on release; no colour change. With reduce motion the squeeze is a plain alpha dip with no spring. |
| Focused | A 2 dp accent outline on the component's own shape, and the glass steps up to 24% (the focused carousel's alpha). Reachable by keyboard and by accessibility focus. |
| Disabled | 0.45 alpha on the whole component and no click: it ignores taps and takes no pressed state. Its shape, size and text stay put so the layout does not move. |
| Busy | A slosh: the content sways like liquid while work runs, and taps are ignored as when disabled, at full alpha. Reduce motion holds it still and shows a still accent dot instead. |

| Component | Default | Shape and type | Component notes |
| --- | --- | --- | --- |
| Card | white 14% glass, 1 dp border at 25 to 28% | `JuiceShapes` card (30); title 25/30 medium, body 14 | Busy while it is being captured or completed: the card sloshes. Pressed squeezes the whole card. Min 44 dp touch target on its controls. |
| Pill | white 10% glass (accent fill when it is the primary action), 48 high | `JuiceShapes` pill (24); button 14 bold | Disabled is the one that dims for lack of input. Pills under 44 dp tall use `Modifier.touchTarget`. Busy replaces the label with a sloshing accent dot of the same width. |
| Input | white 14% glass, 1 dp border at 25 to 28% | `JuiceShapes` input (28); body 14, chat 15/21 | Focused shows the accent outline and the caret in the accent. Busy (coach thinking, intake reading) sloshes the field and keeps the typed text. |
| Bubble | white 14% glass, 1 dp border | `JuiceShapes` bubble (16); chat 15/21 | Rises in with a sway and pops out. Busy is the coach's thinking bubble, a sloshing run of three dots. Not focusable unless it holds an action. |

## Landscape

- The overlay keeps clear of the status bar, navigation bar and cutout on every side, re-read whenever the screen turns.
- Below 480 dp tall (a phone on its side) it is compact: smaller top gap, the undo prompt sits in the header's middle, the settings sheet is at most 560 dp wide.
- A centre card under 300 dp tall and at least 480 dp wide puts the title and steps on the left and the worth, buttons and hint on the right.
- The backdrop slice is 280 dp, or half the screen's height if that is less.
- The slice handle is saved as fractions of the screen, so it follows the screen round; it always lands with its logo clear of the bars and cutout.

## Characters

A face is two eyes and a mouth, each a square, circle or triangle (`Face`). It is drawn by the lid cutter (`core/character/face`):

- `FacePoses` gives each `Expression` a pose: scale, flip, tilt, offset and **lids** that slice the shape from the top (sloped) and bottom. The same rules fit every shape, so no art is drawn per character.
- `FaceAnimator` springs between poses (underdamped, so changes overshoot and jiggle), pops and bursts froth on a change, blinks, glances, breathes, bobs, and flaps the mouth per typed letter. `FaceMotion` gives each character its own feel. `calm` is reduce motion.
- Drawing (`ui/face/CharacterFace.kt`): a cream body (#FFF8EC) with 3-unit rounded joins over a pop shadow in the character's colour (`FaceColors`), offset 2.5 x 3.

Expressions (one tag starts every chat reply): `[neutral] [happy] [excited] [worried] [sad] [stern] [surprised] [thinking] [cheeky]`. Older `[angry]` and `[scared]` still read, as stern and worried.

The coach chat is a visual novel (`CoachChat.kt`): one dialogue box shows the latest line, the face hovers over its top-right corner, the player's reply shows in the same box before the coach thinks and answers, and History opens the whole conversation.
