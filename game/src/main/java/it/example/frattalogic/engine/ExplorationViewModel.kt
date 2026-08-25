package it.example.frattalogic.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.example.frattalogic.audio.SoundEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val RAGGIO_VISIBILE = 520f
private const val VELOCITA_BASE = 90f
private const val VELOCITA_MASSIMA = 190f
private const val VELOCITA_ROTAZIONE_GRADI_AL_SEC = 130f

/**
 * Guida un vascello alla deriva in un mare frattale generato dinamicamente:
 * lo sterzo a schermo imposta rotta e velocità, un loop continuo aggiorna la
 * posizione e rigenera il paesaggio visibile. Attraversare un nuovo "mondo"
 * (regione con palette e tipo frattale dominante diversi) apre un breve
 * evento bonus — lo stesso enigma "trova la dissonanza" del prototipo
 * precedente — per stabilizzarne l'ingresso.
 */
class ExplorationViewModel : ViewModel() {

    private val random = Random(System.nanoTime())
    val soundEngine = SoundEngine()

    private val mondoIniziale = MondoGenerator.mondoPer(0f)
    private val _state = MutableStateFlow(
        ExplorationState(
            mondo = mondoIniziale,
            elementiVisibili = MondoGenerator.elementiVicini(0f, 0f, RAGGIO_VISIBILE, mondoIniziale)
        )
    )
    val state: StateFlow<ExplorationState> = _state.asStateFlow()

    @Volatile private var direzioneInput = 0f
    @Volatile private var accelerazioneInput = 0f
    private var cicloAvviato = false

    fun avvia() {
        soundEngine.start()
        soundEngine.aggiornaProfondita(_state.value.mondo.indice)
        if (cicloAvviato) return
        cicloAvviato = true
        viewModelScope.launch {
            var ultimoTick = System.nanoTime()
            while (true) {
                delay(16L)
                val ora = System.nanoTime()
                val dt = ((ora - ultimoTick) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                ultimoTick = ora
                aggiorna(dt)
            }
        }
    }

    fun ferma() = soundEngine.stop()

    /** Chiamato dal joystick a schermo: x/y in [-1, 1] (sinistra/destra, giù/su). */
    fun impostaSterzo(x: Float, y: Float) {
        direzioneInput = x.coerceIn(-1f, 1f)
        accelerazioneInput = (-y).coerceIn(-1f, 1f)
    }

    fun toccaBonus(indice: Int) {
        val corrente = _state.value
        val camera = corrente.cameraBonus ?: return
        if (corrente.esitoBonus != Esito.NESSUNO) return

        val risolto = indice == camera.indiceDissonante
        if (risolto) {
            soundEngine.onRisolto(corrente.mondo.indice)
        } else {
            soundEngine.onRottura()
        }

        _state.value = corrente.copy(
            punteggio = corrente.punteggio + if (risolto) 50 + corrente.mondo.indice * 10 else 0,
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

        val vascello = corrente.vascello
        val nuovaRotta = vascello.rotta + direzioneInput * VELOCITA_ROTAZIONE_GRADI_AL_SEC * dt
        val velocitaTarget = VELOCITA_BASE + (VELOCITA_MASSIMA - VELOCITA_BASE) * ((accelerazioneInput + 1f) / 2f)
        val nuovaVelocita = vascello.velocita + (velocitaTarget - vascello.velocita) * (dt * 2f).coerceAtMost(1f)

        val rad = Math.toRadians(nuovaRotta.toDouble())
        val nuovaX = vascello.x + (cos(rad) * nuovaVelocita * dt).toFloat()
        val nuovaY = vascello.y + (sin(rad) * nuovaVelocita * dt).toFloat()
        val distanzaPercorsa = corrente.distanzaPercorsa + nuovaVelocita * dt

        val nuovoVascello = vascello.copy(x = nuovaX, y = nuovaY, rotta = nuovaRotta, velocita = nuovaVelocita)
        val mondoAggiornato = MondoGenerator.mondoPer(distanzaPercorsa)

        if (mondoAggiornato.indice != corrente.mondo.indice) {
            soundEngine.aggiornaProfondita(mondoAggiornato.indice)
            avviaEventoBonus(corrente, nuovoVascello, mondoAggiornato, distanzaPercorsa)
            return
        }

        _state.value = corrente.copy(
            vascello = nuovoVascello,
            mondo = mondoAggiornato,
            elementiVisibili = MondoGenerator.elementiVicini(nuovaX, nuovaY, RAGGIO_VISIBILE, mondoAggiornato),
            distanzaPercorsa = distanzaPercorsa
        )
    }

    private fun avviaEventoBonus(
        corrente: ExplorationState,
        vascello: Vascello,
        nuovoMondo: Mondo,
        distanzaPercorsa: Float
    ) {
        _state.value = corrente.copy(
            vascello = vascello,
            mondo = nuovoMondo,
            distanzaPercorsa = distanzaPercorsa,
            fase = Fase.EVENTO_BONUS,
            cameraBonus = DiveEngine.generaCamera(nuovoMondo.indice, random),
            indiceSelezionatoBonus = null,
            esitoBonus = Esito.NESSUNO
        )
    }

    private fun concludiEventoBonus() {
        val corrente = _state.value
        _state.value = corrente.copy(
            elementiVisibili = MondoGenerator.elementiVicini(
                corrente.vascello.x, corrente.vascello.y, RAGGIO_VISIBILE, corrente.mondo
            ),
            fase = Fase.ESPLORAZIONE,
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
