package it.example.theremin.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generatore di campioni del theremin, indipendente da Android (testabile su JVM).
 *
 * Il suono è una sinusoide arricchita da poche armoniche decrescenti e da un vibrato lento,
 * con portamento (glissando) sulla frequenza e inviluppo morbido sul volume: i bersagli
 * impostati dalla fotocamera arrivano a ~15-30 Hz, qui vengono interpolati campione per campione.
 */
class ThereminSynth(private val sampleRate: Int) {

    @Volatile var frequenzaBersaglio: Float = 440f
    @Volatile var volumeBersaglio: Float = 0f

    /** Valori effettivamente in uscita, letti dalla UI per sincronizzare le onde. */
    @Volatile var frequenzaCorrente: Float = 440f
        private set
    @Volatile var volumeCorrente: Float = 0f
        private set

    private val coeffGlide = coefficiente(0.045f)
    private val coeffVolume = coefficiente(0.030f)

    private var fase = 0.0
    private var faseVibrato = 0.0
    private var freq = 440.0
    private var vol = 0.0

    /** Riempie [out] con campioni in [-1, 1] e restituisce il picco assoluto del blocco. */
    fun render(out: FloatArray, count: Int = out.size): Float {
        val fBersaglio = frequenzaBersaglio.toDouble()
        val vBersaglio = volumeBersaglio.toDouble().coerceIn(0.0, 1.0)
        val incVibrato = 2.0 * PI * 5.5 / sampleRate
        var picco = 0f
        for (i in 0 until count) {
            // Portamento in dominio lineare: con costanti di tempo così brevi la differenza col log è impercettibile
            freq += (fBersaglio - freq) * coeffGlide
            vol += (vBersaglio - vol) * coeffVolume

            faseVibrato += incVibrato
            if (faseVibrato > 2.0 * PI) faseVibrato -= 2.0 * PI
            val f = freq * (1.0 + 0.006 * sin(faseVibrato))

            fase += f / sampleRate
            if (fase >= 1.0) fase -= 1.0
            val w = 2.0 * PI * fase

            var s = sin(w) + 0.32 * sin(2 * w) + 0.16 * sin(3 * w) + 0.06 * sin(4 * w)
            s = tanh(1.2 * s / 1.54) // leggera saturazione "valvolare", normalizzata
            val campione = (s * vol * 0.85).toFloat()
            out[i] = campione
            val a = kotlin.math.abs(campione)
            if (a > picco) picco = a
        }
        frequenzaCorrente = freq.toFloat()
        volumeCorrente = vol.toFloat()
        return picco
    }

    /** Coefficiente di un filtro a un polo con costante di tempo [tauSec]. */
    private fun coefficiente(tauSec: Float): Double = 1.0 - exp(-1.0 / (tauSec * sampleRate))
}
