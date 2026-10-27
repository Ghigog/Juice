# Overlay blur flickers on and off

**Status: fixed by moving off `FLAG_BLUR_BEHIND`.** Root cause found in the Android window manager source (AOSP `main`), not on a device.

## Root cause

The overlay used `FLAG_BLUR_BEHIND` with `blurBehindRadius`. The app does not draw that blur. The window manager does, through a **dim layer** (`WindowState.applyDims()`, `Dimmer`, `DimmerAnimationHelper`):

- The dim layer is rebuilt on **every window manager surface pass**. Each pass resets it, and the window must ask for it again. If the window does not qualify in a pass, the layer is removed.
- To qualify, the window must be shown, not hidden, visible by policy and visible "now" in that pass. Any pass that misses one of these drops the blur. The next pass makes a new layer. With no dim amount the fade takes 0 ms, so the blur blinks off and on.
- For a window with no task, like our `TYPE_APPLICATION_OVERLAY`, the dimmer belongs to the **whole display area** and is shared. Only the topmost window asking for a dim wins.
- Every `updateViewLayout` re-runs this. The overlay called it on every reveal frame (the blur ramp) and on every focus regain (a workaround from #80), so it kept poking the fragile path.

None of this can be fixed from the app while the blur is the window manager's dim layer. The earlier fixes (#79, #80, #81, T-13) all worked around it.

## Fix

The overlay window is now a `Dialog` with a translucent theme (`overlayDialog` in `OverlayService.kt`), so it has a `PhoneWindow`, and it uses the public **window background blur**, `Window.setBackgroundBlurRadius`.

- That blur is a blur region on the overlay's own surface, sent by its render thread with each frame. No dim layer, no shared dimmer, no per-pass visibility check, no relayout.
- The reveal ramp only changes the radius. The next frame draws it, in step with the ground.
- The radius stays at least 1 px while the overlay is up, since 0 tears the blur drawable down.
- `applyBackdrop` does nothing when the backdrop is unchanged, and the focus-regain re-apply is gone.
- Back (key or predictive back gesture) reaches the overlay through `onBackPressed`. The dialog never closes itself.

## What still turns the blur off

Android turns all cross-window blur off, for every app, under battery saver, critical thermal status, tunnelled video playback, or the "Disable window blurs" developer option (`BlurController`). Then the overlay shows the scrim. No app can blur the screen behind it in those states.

## Check on a device

1. Blur on, battery saver off. Open the overlay, type, open the keyboard, open coach chat, switch focus. The blur should stay.
2. If it still drops, run `adb shell dumpsys window | grep -i blur` to see whether the system turned blur off.
