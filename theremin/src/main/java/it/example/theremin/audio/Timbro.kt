package it.example.theremin.audio

import kotlin.math.sqrt

/**
 * Timbri disponibili, ottenuti per sintesi additiva: ogni timbro è l'elenco delle ampiezze
 * delle armoniche (fondamentale, 2ª, 3ª, …), normalizzate in modo da avere volume simile.
 */
enum class Timbro(val etichetta: String, ampiezze: FloatArray) {
    THEREMIN("Theremin", floatArrayOf(1f, 0.32f, 0.16f, 0.06f)),
    SINUSOIDE("Sinusoide pura", floatArrayOf(1f)),
    FLAUTO("Flauto", floatArrayOf(1f, 0.12f, 0.05f, 0.02f)),
    CLARINETTO("Clarinetto", floatArrayOf(1f, 0f, 0.45f, 0f, 0.25f, 0f, 0.14f, 0f, 0.08f)),
    VIOLINO("Violino", FloatArray(10) { n -> 1f / (n + 1) * (1f - n * 0.05f) }),
    ORGANO("Organo", floatArrayOf(1f, 0.7f, 0f, 0.5f, 0f, 0f, 0f, 0.35f)),
    VOCE("Voce", floatArrayOf(1f, 0.55f, 0.8f, 0.3f, 0.12f, 0.25f, 0.06f)),
    OTTO_BIT("8-bit", FloatArray(15) { n -> if (n % 2 == 0) 1f / (n + 1) else 0f });

    /** Ampiezze normalizzate a energia unitaria (la fondamentale da sola varrebbe 1). */
    val armoniche: FloatArray = run {
        val energia = sqrt(ampiezze.sumOf { (it * it).toDouble() }).toFloat()
        FloatArray(ampiezze.size) { ampiezze[it] / energia }
    }
}
