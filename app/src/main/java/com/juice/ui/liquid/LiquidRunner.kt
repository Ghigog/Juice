package com.juice.ui.liquid

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.Choreographer

/**
 * Where the juice's physics runs. [shared] is one background thread for the whole app, so a full card's
 * stepping never holds up a UI frame; [immediate] runs everything on the calling thread, for tests.
 */
internal interface LiquidRunner {
    /** Runs [block] on the physics thread. */
    fun post(block: () -> Unit)
    /** Asks for [callback] on the physics thread's next vsync. Call from the physics thread. */
    fun postFrame(callback: Choreographer.FrameCallback)
    fun removeFrame(callback: Choreographer.FrameCallback)
    /** The physics thread's handler, for sensor callbacks; null runs them on the main thread. */
    val handler: Handler?

    companion object {
        val shared: LiquidRunner by lazy {
            val thread = HandlerThread("juice-liquid", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
            ThreadRunner(Handler(thread.looper))
        }

        val immediate: LiquidRunner = object : LiquidRunner {
            override fun post(block: () -> Unit) = block()
            override fun postFrame(callback: Choreographer.FrameCallback) = Choreographer.getInstance().postFrameCallback(callback)
            override fun removeFrame(callback: Choreographer.FrameCallback) = Choreographer.getInstance().removeFrameCallback(callback)
            override val handler: Handler? = null
        }
    }
}

/** Runs on [handler]'s thread; its Choreographer paces the frames, since each looper thread has its own. */
private class ThreadRunner(override val handler: Handler) : LiquidRunner {
    override fun post(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }
    override fun postFrame(callback: Choreographer.FrameCallback) = Choreographer.getInstance().postFrameCallback(callback)
    override fun removeFrame(callback: Choreographer.FrameCallback) = Choreographer.getInstance().removeFrameCallback(callback)
}
