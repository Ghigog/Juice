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

- Colour: `JuiceColors` (OverlayParts.kt), `FlavourTheme`, the ground in `overlayTint` (OverlayShell.kt), launcher colours in `res/values/colors.xml`.
- Glass: white 10% (fields), 14% (cards, inputs, bubbles, 1 dp border at 25 to 28%), 24% (focused carousel), black 28% (wells).
- Type: Space Grotesk 400/500/700. Card title 25/30 medium, dialogue 18/26 medium, name 18 bold, chat 15, body 14, button 14 bold, badge 11 bold +1 tracking.
- Radius: card 30, input 28, pill 24 (48 high), bubble 16, badge 14, field 12. Touch targets at least 44 dp.
- Motion: `OverlayMotion.kt` (reveal 320 ms, entrance 350 ms with 60 ms stagger, 1.5 sway cycles, pop swell 1.08, 14 froth bubbles).

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
