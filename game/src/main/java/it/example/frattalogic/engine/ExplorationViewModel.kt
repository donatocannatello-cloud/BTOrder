package it.example.frattalogic.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.example.frattalogic.audio.SoundEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

private const val LARGHEZZA_RENDER = 96
private const val ALTEZZA_RENDER = 160
private const val VELOCITA_ZOOM_BASE = 0.55f
private const val VELOCITA_ZOOM_MASSIMA = 1.6f
private const val DERIVA_LATERALE = 0.12
private const val SEMIAMPIEZZA_INIZIALE = 1.4
private const val SEMIAMPIEZZA_MINIMA = 1e-13
private const val LIVELLI_ZOOM_PER_EVENTO_BONUS = 15f

/**
 * Guida un'immersione continua nel mare frattale: lo sterzo a schermo
 * imposta la velocità di discesa (zoom verso l'interno, asse principale) e
 * una lieve deriva laterale; un loop continuo ricalcola ad ogni fotogramma
 * la finestra sul piano complesso e la passa a [FractalField]. La colonna
 * sonora segue [livelloZoom] con continuità ad ogni fotogramma; ogni tot
 * livelli di zoom (non troppo spesso, per non spezzare il flusso) si apre
 * un breve evento bonus — lo stesso enigma "trova la dissonanza" (nucleo +
 * anello di nodi) delle versioni precedenti — per stabilizzare la discesa.
 */
class ExplorationViewModel : ViewModel() {

    private val random = Random(System.nanoTime())
    val soundEngine = SoundEngine()

    private val _state = MutableStateFlow(ImmersioneState())
    val state: StateFlow<ImmersioneState> = _state.asStateFlow()

    @Volatile private var derivaInput = 0f
    @Volatile private var discesaInput = 0f
    private var cicloAvviato = false

    fun avvia() {
        soundEngine.start()
        soundEngine.aggiornaProfondita(_state.value.livelloZoom.toInt())
        if (cicloAvviato) return
        cicloAvviato = true
        viewModelScope.launch(Dispatchers.Default) {
            var ultimoTick = System.nanoTime()
            while (true) {
                delay(40L)
                val ora = System.nanoTime()
                val dt = ((ora - ultimoTick) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.15f)
                ultimoTick = ora
                aggiorna(dt)
            }
        }
    }

    fun ferma() = soundEngine.stop()

    /** Chiamato dal joystick a schermo: x/y in [-1, 1] (sinistra/destra, giù/su). */
    fun impostaSterzo(x: Float, y: Float) {
        derivaInput = x.coerceIn(-1f, 1f)
        discesaInput = (-y).coerceIn(-1f, 1f)
    }

    fun toccaBonus(indice: Int) {
        val corrente = _state.value
        val camera = corrente.cameraBonus ?: return
        if (corrente.esitoBonus != Esito.NESSUNO) return

        val risolto = indice == camera.indiceDissonante
        if (risolto) {
            soundEngine.onRisolto(corrente.livelloZoom.toInt())
        } else {
            soundEngine.onRottura()
        }

        _state.value = corrente.copy(
            punteggio = corrente.punteggio + if (risolto) 40 + corrente.livelloZoom.toInt() * 8 else 0,
            indiceSelezionatoBonus = indice,
            esitoBonus = if (risolto) Esito.RISOLTO else Esito.ROTTURA
        )

        viewModelScope.launch {
            delay(if (risolto) 900L else 700L)
            concludiEventoBonus()
        }
    }

    private fun aggiorna(dt: Float) {
        val corrente = _state.value
        if (corrente.fase == Fase.EVENTO_BONUS) return

        val velocitaZoom = VELOCITA_ZOOM_BASE + (VELOCITA_ZOOM_MASSIMA - VELOCITA_ZOOM_BASE) *
            ((discesaInput + 1f) / 2f)
        val fattoreZoom = exp(-velocitaZoom * dt)
        val nuovaSemiAmpiezza = (corrente.semiAmpiezza * fattoreZoom).coerceAtLeast(SEMIAMPIEZZA_MINIMA)

        val derivaX = derivaInput * DERIVA_LATERALE * corrente.semiAmpiezza * dt
        val nuovoCentroX = corrente.centroX + derivaX

        val nuovoLivelloZoom = (-ln(nuovaSemiAmpiezza / SEMIAMPIEZZA_INIZIALE) / ln(2.0))
            .toFloat()
            .coerceAtLeast(0f)
        val maxIterazioni = (60 + nuovoLivelloZoom * 4f).toInt().coerceAtMost(400)
        val faseColore = (nuovoLivelloZoom * 14f) % 360f

        val pixel = FractalField.renderizza(
            centroX = nuovoCentroX,
            centroY = corrente.centroY,
            semiAmpiezza = nuovaSemiAmpiezza,
            larghezza = LARGHEZZA_RENDER,
            altezza = ALTEZZA_RENDER,
            maxIterazioni = maxIterazioni,
            faseColore = faseColore
        )

        soundEngine.aggiornaProfondita(nuovoLivelloZoom.toInt())

        val traguardoRaggiunto = (nuovoLivelloZoom / LIVELLI_ZOOM_PER_EVENTO_BONUS).toInt()
        val traguardoPrecedente = (corrente.livelloZoom / LIVELLI_ZOOM_PER_EVENTO_BONUS).toInt()
        if (traguardoRaggiunto > traguardoPrecedente) {
            avviaEventoBonus(corrente, nuovoCentroX, nuovaSemiAmpiezza, nuovoLivelloZoom, pixel)
            return
        }

        _state.value = corrente.copy(
            centroX = nuovoCentroX,
            semiAmpiezza = nuovaSemiAmpiezza,
            livelloZoom = nuovoLivelloZoom,
            pixel = pixel,
            larghezzaPixel = LARGHEZZA_RENDER,
            altezzaPixel = ALTEZZA_RENDER
        )
    }

    private fun avviaEventoBonus(
        corrente: ImmersioneState,
        centroX: Double,
        semiAmpiezza: Double,
        livelloZoom: Float,
        pixel: IntArray
    ) {
        _state.value = corrente.copy(
            centroX = centroX,
            semiAmpiezza = semiAmpiezza,
            livelloZoom = livelloZoom,
            pixel = pixel,
            larghezzaPixel = LARGHEZZA_RENDER,
            altezzaPixel = ALTEZZA_RENDER,
            fase = Fase.EVENTO_BONUS,
            cameraBonus = DiveEngine.generaCamera(livelloZoom.toInt(), random),
            indiceSelezionatoBonus = null,
            esitoBonus = Esito.NESSUNO
        )
    }

    private fun concludiEventoBonus() {
        val corrente = _state.value
        _state.value = corrente.copy(
            fase = Fase.IMMERSIONE,
            cameraBonus = null,
            indiceSelezionatoBonus = null,
            esitoBonus = Esito.NESSUNO
        )
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.stop()
    }
}
