package com.soma.efootballai.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

data class ScoreReadResult(
    val score: String? = null,
    val clock: String? = null,
    val rawText: String? = null
)

object ScoreReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)

    fun read(bitmap: Bitmap, callback: (ScoreReadResult) -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            bitmap.recycle()
            callback(ScoreReadResult())
            return
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text
                callback(
                    ScoreReadResult(
                        score = findScore(text),
                        clock = findClock(text),
                        rawText = text
                    )
                )
            }
            .addOnFailureListener {
                callback(ScoreReadResult())
            }
            .addOnCompleteListener {
                busy.set(false)
            }
    }

    private fun findScore(text: String): String? {
        // Common score renderings: 2 - 1, 2:1, or 2 1.
        // Deliberately conservative: avoid treating arbitrary numbers as a score.
        val patterns = listOf(
            Regex("""(?<!\d)([0-9])\s*[-:]\s*([0-9])(?!\d)"""),
            Regex("""(?m)^\s*([0-9])\s+([0-9])\s*$""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val left = match.groupValues[1]
            val right = match.groupValues[2]
            return "$left : $right"
        }
        return null
    }

    private fun findClock(text: String): String? {
        val match = Regex("""(?<!\d)([0-9]{1,3}:[0-5][0-9])(?!\d)""").find(text)
        return match?.groupValues?.get(1)
    }
}
