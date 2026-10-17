package com.soma.efootballai.capture

/** Heuristic OCR parser. It does not yet understand eFootball-specific screen layouts. */
object ScoreReader {
    data class Result(val score: String?, val clock: String?)

    fun parse(text: String): Result {
        val normalized = text.replace('O', '0').replace('o', '0')
        val clock = Regex("\\b(\\d{1,3})\\s*[:.]\\s*(\\d{2})\\b")
            .findAll(normalized)
            .map { "${it.groupValues[1]}:${it.groupValues[2]}" }
            .firstOrNull { value ->
                val parts = value.split(":")
                parts.size == 2 && (parts[1].toIntOrNull()?.let { it in 0..59 } == true)
            }
        val scoreMatch = Regex("\\b([0-9])\\s*[-:]\\s*([0-9])\\b").find(normalized)
        val score = scoreMatch?.let { "${it.groupValues[1]} : ${it.groupValues[2]}" }
        return Result(score, clock)
    }
}
