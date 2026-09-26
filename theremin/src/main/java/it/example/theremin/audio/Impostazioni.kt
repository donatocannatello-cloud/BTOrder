package it.example.theremin.audio

import it.example.theremin.camera.PuntoMano

/**
 * Parametri regolabili dall'utente, per il suono e per la lettura della mano.
 * Classe immutabile: ogni modifica produce una copia, che viene applicata al synth e salvata.
 */
data class Impostazioni(
    // --- Suono ---
    val timbro: Timbro = Timbro.THEREMIN,
    /** Trasposizione del suono in ottave (-2..+2): cambia l'altezza reale senza cambiare la posizione delle note. */
    val ottava: Int = 0,
    /** Ottave coperte dalla larghezza dell'inquadratura in modalità Suona (1..4): meno ottave = note più larghe. */
    val estensioneOttave: Int = 3,
    /** Tonalità (0 = Do … 11 = Si) a cui si agganciano le scale. */
    val tonica: Int = 0,
    /** Profondità del vibrato, 0..1 (1 = ±3% di frequenza, circa mezzo semitono). */
    val vibrato: Float = 0.2f,
    /** Velocità del vibrato in Hz. */
    val vibratoHz: Float = 5.5f,
    /** Tempo di glissando tra una nota e l'altra, in millisecondi. */
    val portamentoMs: Float = 45f,
    /** Quantità di eco, 0..1. */
    val eco: Float = 0f,
    /** Saturazione "valvolare", 0..1: da suono pulito a caldo e un po' sporco. */
    val calore: Float = 0.3f,
    // --- Lettura della mano ---
    /** Fascia ai due lati dell'inquadratura (0..0.3) esclusa dalla tastiera: le note estreme si raggiungono prima del bordo. */
    val margine: Float = 0.12f,
    val puntoMano: PuntoMano = PuntoMano.PUNTA,
) {
    /** Posizione x della mano nell'inquadratura (0..1) → posizione sulla tastiera (0..1), escludendo i margini. */
    fun tastieraDaCamera(x: Float): Float = ((x - margine) / (1f - 2f * margine)).coerceIn(0f, 1f)

    /** Inversa di [tastieraDaCamera], per disegnare la guida nell'anteprima. */
    fun cameraDaTastiera(t: Float): Float = margine + t.coerceIn(0f, 1f) * (1f - 2f * margine)

    /** Estensione in MIDI della modalità Suona: parte da Do3 e copre [estensioneOttave] ottave. */
    val midiMin: Float get() = Note.MIDI_MIN
    val midiMax: Float get() = Note.MIDI_MIN + 12f * estensioneOttave

    companion object {
        const val OTTAVA_MIN = -2
        const val OTTAVA_MAX = 2
    }
}
