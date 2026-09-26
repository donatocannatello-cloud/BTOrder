package it.example.theremin.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generatore di campioni del theremin, indipendente da Android (testabile su JVM).
 *
 * Il suono è una somma di armoniche (il [Timbro]) con vibrato, saturazione ed eco regolabili,
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

    // Parametri del suono, aggiornati da [applica]
    @Volatile private var armoniche = Timbro.THEREMIN.armoniche
    @Volatile private var profonditaVibrato = 0.006
    @Volatile private var velocitaVibrato = 5.5
    @Volatile private var coeffGlide = coefficiente(0.045f)
    @Volatile private var quantitaEco = 0.0
    @Volatile private var guadagnoSaturazione = 1.4

    private val coeffVolume = coefficiente(0.030f)

    private var fase = 0.0
    private var faseVibrato = 0.0
    private var freq = 440.0
    private var vol = 0.0

    private val lineaEco = FloatArray((sampleRate * 0.32f).toInt())
    private var posEco = 0

    fun applica(imp: Impostazioni) {
        armoniche = imp.timbro.armoniche
        profonditaVibrato = 0.03 * imp.vibrato.coerceIn(0f, 1f)
        velocitaVibrato = imp.vibratoHz.coerceIn(1f, 12f).toDouble()
        coeffGlide = coefficiente(imp.portamentoMs.coerceIn(3f, 1000f) / 1000f)
        quantitaEco = imp.eco.coerceIn(0f, 1f).toDouble()
        guadagnoSaturazione = 0.6 + 3.0 * imp.calore.coerceIn(0f, 1f)
    }

    /** Riempie [out] con campioni in [-1, 1] e restituisce il picco assoluto del blocco. */
    fun render(out: FloatArray, count: Int = out.size): Float {
        val fBersaglio = frequenzaBersaglio.toDouble()
        val vBersaglio = volumeBersaglio.toDouble().coerceIn(0.0, 1.0)
        val arm = armoniche
        val glide = coeffGlide
        val vibrato = profonditaVibrato
        val incVibrato = 2.0 * PI * velocitaVibrato / sampleRate
        val eco = quantitaEco
        val retroazione = 0.55 * eco
        val drive = guadagnoSaturazione
        val normalizzazione = 1.0 / tanh(drive)
        val limiteArmoniche = sampleRate * 0.45
        var picco = 0f
        for (i in 0 until count) {
            // Portamento in dominio lineare: con costanti di tempo brevi la differenza col log è impercettibile
            freq += (fBersaglio - freq) * glide
            vol += (vBersaglio - vol) * coeffVolume

            faseVibrato += incVibrato
            if (faseVibrato > 2.0 * PI) faseVibrato -= 2.0 * PI
            val f = freq * (1.0 + vibrato * sin(faseVibrato))

            fase += f / sampleRate
            if (fase >= 1.0) fase -= 1.0
            val w = 2.0 * PI * fase

            var s = 0.0
            for (k in arm.indices) {
                val a = arm[k]
                if (a == 0f) continue
                if (f * (k + 1) > limiteArmoniche) break // niente armoniche oltre Nyquist (aliasing)
                s += a * sin((k + 1) * w)
            }
            s = tanh(drive * s * 0.8) * normalizzazione * 0.8 // saturazione "valvolare", normalizzata
            var campione = s * vol * 0.85

            if (eco > 0.0) {
                val ritardato = lineaEco[posEco].toDouble()
                lineaEco[posEco] = (campione + ritardato * retroazione).toFloat()
                posEco = (posEco + 1) % lineaEco.size
                campione = (campione + ritardato * eco) / (1.0 + 0.5 * eco)
            }

            val c = campione.toFloat().coerceIn(-1f, 1f)
            out[i] = c
            val a = kotlin.math.abs(c)
            if (a > picco) picco = a
        }
        frequenzaCorrente = freq.toFloat()
        volumeCorrente = vol.toFloat()
        return picco
    }

    /** Coefficiente di un filtro a un polo con costante di tempo [tauSec]. */
    private fun coefficiente(tauSec: Float): Double = 1.0 - exp(-1.0 / (tauSec * sampleRate))
}
