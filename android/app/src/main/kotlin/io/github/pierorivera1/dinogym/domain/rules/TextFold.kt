package io.github.pierorivera1.dinogym.domain.rules

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for comparing names: lower case, accents removed, trimmed. "Curl de Bíceps"
 * and "curl de biceps" fold to the same text, so search and name matching ignore both.
 */
object TextFold {
    private val marks = Regex("\\p{Mn}+")

    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(marks, "").trim()
}
