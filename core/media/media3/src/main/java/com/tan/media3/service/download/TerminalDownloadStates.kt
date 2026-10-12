package com.tan.media3.service.download

/** Deduplicate terminal callbacks without retaining the user's entire download history. */
internal class TerminalDownloadStates(private val capacity: Int = 256) {
    private val states = LinkedHashMap<String, Int>()

    init { require(capacity > 0) }

    fun changed(id: String, state: Int): Boolean {
        val previous = states.remove(id)
        states[id] = state
        while (states.size > capacity) states.remove(states.keys.first())
        return previous != state
    }

    fun removed(id: String) { states.remove(id) }
}
