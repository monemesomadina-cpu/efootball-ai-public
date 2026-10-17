package com.soma.efootballai.state

/** In-process state shared by the capture service and the main screen. */
object MatchState {
    data class Snapshot(
        val status: String = "Ready",
        val score: String? = null,
        val clock: String? = null,
        val rawText: String? = null
    )

    private val listeners = mutableSetOf<(Snapshot) -> Unit>()
    @Volatile private var current = Snapshot()

    @Synchronized fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        listener(current)
    }

    @Synchronized fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized fun update(snapshot: Snapshot) {
        current = snapshot
        listeners.toList().forEach { it(snapshot) }
    }
}
