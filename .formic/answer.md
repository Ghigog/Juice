{
  "kind": "reroute",
  "reason": "One behaviour on one surface: the backdrop slice's SpinMomentum loses its idle turn and gets one kick from the handle pull that opens the overlay; carousel drag and fling stay as they are.",
  "ticket": {
    "key": "T-1",
    "title": "Spin the backdrop slice from the opening pull instead of an idle turn",
    "userStory": {
      "as": "a Juice user",
      "want": "the backdrop slice to stay still unless I move it, and to spin with force from the pull that opens the overlay",
      "soThat": "the slice feels like a wheel I set going myself, not one that turns on its own"
    },
    "requirements": [
      "Remove the idle turn from SpinMomentum (IDLE_SPIN_DEG_PER_SEC / BACKDROP_TURN_MILLIS): with no boost the slice stays at its current angle; boost still decays by SPIN_FRICTION_SECONDS so a spin coasts to rest.",
      "Leave carousel behaviour unchanged: grab() stops the spin, drag() follows the finger by DRAG_DEG_PER_PX, and a big swipe still kick()s the slice.",
      "Measure the release speed of the SliceHandleView swipe that ends in Release.OPEN (e.g. with a VelocityTracker) and pass it through OverlayService's onOpen to OverlayShell, which kick()s the SpinMomentum once the overlay opens.",
      "Strength scales with the pull's release speed and is capped by MAX_SPIN_BOOST_DEG_PER_SEC; direction follows the pull's vertical direction, using the same sign as a carousel drag the same way.",
      "Opening by tap (the click listener) gives no spin; with reduce motion on the slice never turns, as now.",
      "Update SpinMomentumTest for the removed idle turn and add a test for the open kick."
    ],
    "acceptanceCriteria": [
      {
        "given": "the overlay is open and nobody has touched the carousel",
        "when": "time passes",
        "then": "the backdrop slice stays still"
      },
      {
        "given": "the handle is on screen",
        "when": "I pull the handle hard past the swipe radius to open the overlay",
        "then": "the backdrop slice spins in the pull's direction and slows to a stop"
      },
      {
        "given": "the handle is on screen",
        "when": "I open the overlay with a slower pull",
        "then": "the backdrop slice spins less than after a hard pull before it stops"
      },
      {
        "given": "the handle is on screen",
        "when": "I tap the handle to open the overlay",
        "then": "the backdrop slice does not spin"
      },
      {
        "given": "the backdrop slice is spinning from the opening pull",
        "when": "I touch and drag the carousel up and down",
        "then": "the slice stops and follows my drag as it does today, and stays still after I let go unless I flung it"
      },
      {
        "given": "reduce motion is on",
        "when": "I pull the handle to open the overlay",
        "then": "the backdrop slice does not turn"
      }
    ],
    "fileScope": [
      "app/src/main/java/com/juice/ui/overlay/SpinMomentum.kt",
      "app/src/main/java/com/juice/ui/overlay/OverlayShell.kt",
      "app/src/main/kotlin/com/juice/overlay/SliceHandle.kt",
      "app/src/main/kotlin/com/juice/overlay/OverlayService.kt",
      "app/src/test/java/com/juice/ui/overlay/SpinMomentumTest.kt",
      "app/src/test/java/com/juice/overlay/SliceGestureTest.kt"
    ],
    "storyPoints": 5,
    "dependsOn": [],
    "type": "feature"
  }
}
