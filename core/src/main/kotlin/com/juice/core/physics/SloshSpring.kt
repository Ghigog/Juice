package com.juice.core.physics

/**
 * Damped spring `a = -k*x - c*v + impulse` driving the reservoir slosh; [position] is clamped to -1..1.
 * Stiffness and damping can change while it runs (a viscous flavour sloshes slower).
 */
class SloshSpring(var stiffness: Float = 60f, var damping: Float = 6f) {
    var position = 0f
        private set
    private var velocity = 0f

    /** Advances the spring by [dt] seconds under [impulse] (acceleration) and returns the new [position]. */
    fun step(dt: Float, impulse: Float = 0f): Float {
        val acceleration = -stiffness * position - damping * velocity + impulse
        velocity += acceleration * dt
        position += velocity * dt
        if (position > 1f || position < -1f) {
            position = position.coerceIn(-1f, 1f)
            velocity = 0f
        }
        return position
    }
}
