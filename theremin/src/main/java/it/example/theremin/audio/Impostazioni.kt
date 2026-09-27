package it.example.theremin.audio

import it.example.theremin.camera.PuntoMano

/** Come viene trovata la mano nell'immagine della fotocamera. */
enum class Rilevatore(val etichetta: String) {
    /** Riconoscimento vero della mano con MediaPipe: preciso, indipendente da sfondo e luce. */
    MANO_IA("Riconoscimento mano (IA)"),
    /** Sottrazione dello sfondo: leggerissimo, ma serve uno sfondo fermo e una ricalibrazione. */
    MOVIMENTO("Movimento sullo sfondo"),
}

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
    /**
     * Rinforzo dei bassi, 0..1: sulle note gravi aggiunge armoniche, perché l'altoparlante del
     * telefono non riproduce le frequenze sotto ~250 Hz e senza armoniche le note gravi sparirebbero.
     */
    val rinforzoBassi: Float = 0.6f,
    // --- Mixer ---
    /** Volume del theremin, 0..1, indipendente dalla base musicale. */
    val volumeTheremin: Float = 1f,
    /** Volume della base musicale di sottofondo, 0..1. */
    val volumeBase: Float = 0.6f,
    // --- Lettura della mano ---
    val rilevatore: Rilevatore = Rilevatore.MANO_IA,
    /** Mano (o dito) sul sensore di prossimità, in alto vicino all'altoparlante = silenzio immediato. */
    val prossimitaMuta: Boolean = false,
    /** Fascia ai due lati dell'inquadratura (0..0.3) esclusa dalla tastiera: le note estreme si raggiungono prima del bordo. */
    val margine: Float = 0.12f,
    val puntoMano: PuntoMano = PuntoMano.PUNTA,
    /** Sensibilità del rilevamento, 0..1: più alta se la mano si confonde con lo sfondo, più bassa con sfondi "rumorosi". */
    val sensibilita: Float = 0.5f,
) {
    /** Posizione x della mano nell'inquadratura (0..1) → posizione sulla tastiera (0..1), escludendo i margini. */
    fun tastieraDaCamera(x: Float): Float = ((x - margine) / (1f - 2f * margine)).coerceIn(0f, 1f)

    /** Inversa di [tastieraDaCamera], per disegnare la guida nell'anteprima. */
    fun cameraDaTastiera(t: Float): Float = margine + t.coerceIn(0f, 1f) * (1f - 2f * margine)

    /** Estensione in MIDI della modalità Suona: parte da Do3 e copre [estensioneOttave] ottave. */
    val midiMin: Float get() = Note.MIDI_MIN
    val midiMax: Float get() = Note.MIDI_MIN + 12f * estensioneOttave

    companion object {
        const val OTTAVA_MIN = -3
        const val OTTAVA_MAX = 2
    }
}
