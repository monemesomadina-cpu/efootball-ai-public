package com.soma.efootballai.state

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

object MatchState {
    data class Snapshot(
        val status: String = "Ready",
        val score: String? = null,
        val clock: String? = null,
        val rawText: String? = null
    )

    private val listeners = CopyOnWriteArraySet<(Snapshot) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var current = Snapshot()

    fun snapshot(): Snapshot = current

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        mainHandler.post { listener(current) }
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun update(snapshot: Snapshot) {
        current = snapshot
        mainHandler.post {
            listeners.forEach { listener -> listener(snapshot) }
        }
    }
}
