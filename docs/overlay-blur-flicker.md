# Overlay blur flickers while typing (T-13)

**Status: root cause not confirmed.** This spike was done without a device or emulator, from the code and Android's documented behaviour. Nothing here was measured on hardware, so the causes below are ranked hypotheses, not findings.

## Reproduction (to run on a device with blur support)

1. Enable Blur in Juice settings; battery saver off; Developer options "Disable window blurs" off.
2. Open the overlay from the slice handle. The screen behind is blurred.
3. Tap the capture field so the keyboard opens, then type. Watch for the backdrop switching between blur and the dark scrim.

## What the code does

`OverlayService.applyBackdrop()` re-reads `Backdrop.choose(settings.blur, windowManager.isCrossWindowBlurEnabled, powerManager.isPowerSaveMode)`, writes the result into the Compose state `backdrop` (which drives the `blurred` parameter of `OverlayShell`, so the tint changes with it) and calls `updateViewLayout`. Triggers:

1. the cross-window blur listener (`blurListener`);
2. `onWindowFocusChanged(true)` on the root view;
3. `applySettings`, only when `settings.blur` changed.

Facts established from the code (and pinned by tests):

- Trigger 3 cannot fire from typing: it requires `settings.blur` to change.
- `withBackdrop` is idempotent: re-applying the same backdrop keeps `FLAG_BLUR_BEHIND` and the radius, and SCRIM then BLUR restores them. So our own `updateViewLayout` call does not drop the flag; the params object is the window's own and the flag stays set.
- `Backdrop.choose` is stateless: any single reading of `isCrossWindowBlurEnabled == false` or `isPowerSaveMode == true` produces SCRIM, and the next trigger with the normal reading produces BLUR. There is no debounce. (`BackdropTest`)
- `SOFT_INPUT_ADJUST_NOTHING` is set in `overlayLayoutParams()`, and `updateViewLayout` is passed the same params, so it does not change the soft input mode.

## Hypotheses, ranked

1. **Trigger 2 fires when the IME opens or closes, and the reading it takes is momentarily wrong.** The overlay is focusable, so showing the IME can move window focus (IME/popup windows, autofill or suggestion strips) and return it, calling `applyBackdrop()` repeatedly during the animation. Each call runs `updateViewLayout` mid-IME-animation, which is a layout pass that may let the compositor drop the blur for a frame. The scrim flip needs no wrong reading for this; the flicker could be the layout pass itself rather than the SCRIM state.
2. **Trigger 1 fires with a transient `false`.** The platform notifies the listener as blur availability changes (power save, thermal, some transitions). If it reports a brief false during keyboard transitions the backdrop goes SCRIM then BLUR. Plausible, but no documentation says the IME affects it.
3. **`isPowerSaveMode` reads true briefly.** Unlikely while typing.
4. **Compose recomposition of `OverlayShell`** with `blurred` toggling is a visual effect only and is a consequence of 1 to 3, not a cause.

## Cannot be reproduced under Robolectric

Robolectric has no compositor, no real IME window and no real focus dispatch, so which system event fires on typing cannot be seen there. The added tests pin the surrounding behaviour (the sequence of readings that flips the backdrop and back; the idempotence of the window params). `applyBackdrop` itself is private to the service and was left untouched.

## What to measure next

Add temporary logging on a device (`adb logcat -s OverlayService`) in `applyBackdrop()` printing the trigger, `settings.blur`, `isCrossWindowBlurEnabled`, `isPowerSaveMode`, the chosen backdrop and `SystemClock.uptimeMillis()`, then type with the keyboard opening and closing. Then:

- If `onWindowFocusChanged(true)` appears with the keyboard, hypothesis 1 holds; check whether flicker remains when the call is skipped for an unchanged backdrop.
- If the listener fires with `false`, hypothesis 2 holds; log what else changed at that moment (`dumpsys window`).
- If the logged backdrop stays BLUR throughout yet the screen flickers, the cause is in the compositor/layout pass, not the choice.

## Recommended fix (not shipped here)

Make `applyBackdrop()` a no-op when the chosen backdrop equals the current one (skip `updateViewLayout` and the state write), and consider debouncing a change to SCRIM by one frame or ~100 ms. This fixes hypotheses 1 and 2 without changing behaviour for genuine setting changes. Then re-check on a device.
