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
import kotlin.random.Random

private const val VELOCITA_MASSIMA = 1.4f
private const val COSTANTE_INERZIA = 6.0f
private const val VELOCITA_PAN_PX = 260f
private const val PAN_MASSIMO_PX = 140f
private const val LIVELLI_PER_EVENTO_BONUS = 6.0

/**
 * Guida un'immersione nel tunnel frattale vettoriale: due joystick
 * indipendenti, uno regola solo la velocità di avanzamento (su/giù), l'altro
 * sposta il punto di fuga in ogni direzione. A riposo (nessun joystick
 * azionato) la camera resta perfettamente ferma — nessun movimento
 * automatico. Un loop continuo aggiorna profondità e spostamento; la colonna
 * sonora segue la profondità raggiunta ad ogni fotogramma. Ogni tanti
 * livelli di avanzamento si apre un breve evento bonus — l'enigma "trova la
 * dissonanza" (nucleo + anello di nodi) delle versioni precedenti.
 */
class ExplorationViewModel : ViewModel() {

    private val random = Random(System.nanoTime())
    val soundEngine = SoundEngine()

    private val _state = MutableStateFlow(ImmersioneState())
    val state: StateFlow<ImmersioneState> = _state.asStateFlow()

    @Volatile private var discesaInput = 0f
    @Volatile private var panXInput = 0f
    @Volatile private var panYInput = 0f
    private var cicloAvviato = false

    // Valori effettivi che inseguono il target impostato dai joystick: a
    // riposo il target è sempre zero, quindi la camera si ferma davvero,
    // non solo rallenta verso una velocità di base.
    private var velocitaEffettiva = 0f
    private var panXEffettivo = 0f
    private var panYEffettivo = 0f

    fun avvia() {
        soundEngine.start()
        soundEngine.aggiornaProfondita(_state.value.profondita.toInt())
        if (cicloAvviato) return
        cicloAvviato = true
        viewModelScope.launch(Dispatchers.Default) {
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

    /** Joystick di discesa: y in [-1, 1], su = avanza, giù = retrocede; a riposo la camera è ferma. */
    fun impostaDiscesa(y: Float) {
        discesaInput = (-y).coerceIn(-1f, 1f)
    }

    /** Joystick di direzione: x/y in [-1, 1], sposta il punto di fuga in ogni direzione. */
    fun impostaPan(x: Float, y: Float) {
        panXInput = x.coerceIn(-1f, 1f)
        panYInput = (-y).coerceIn(-1f, 1f)
    }

    fun toccaBonus(indice: Int) {
        val corrente = _state.value
        val camera = corrente.cameraBonus ?: return
        if (corrente.esitoBonus != Esito.NESSUNO) return

        val risolto = indice == camera.indiceDissonante
        if (risolto) {
            soundEngine.onRisolto(corrente.profondita.toInt())
        } else {
            soundEngine.onRottura()
        }

        _state.value = corrente.copy(
            punteggio = corrente.punteggio + if (risolto) 40 + corrente.profondita.toInt() * 8 else 0,
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

        // A riposo (input zero) il target è zero: la camera decelera fino a
        // fermarsi del tutto, non continua ad avanzare a una velocità base.
        val velocitaTarget = discesaInput * VELOCITA_MASSIMA
        val panXTarget = panXInput * VELOCITA_PAN_PX
        val panYTarget = panYInput * VELOCITA_PAN_PX

        val fattoreInerzia = 1f - exp(-COSTANTE_INERZIA * dt)
        velocitaEffettiva += (velocitaTarget - velocitaEffettiva) * fattoreInerzia
        panXEffettivo += (panXTarget - panXEffettivo) * fattoreInerzia
        panYEffettivo += (panYTarget - panYEffettivo) * fattoreInerzia

        val nuovaProfondita = (corrente.profondita + velocitaEffettiva * dt).coerceAtLeast(0.0)
        val nuovoOffsetX = (corrente.offsetX + panXEffettivo * dt).coerceIn(-PAN_MASSIMO_PX, PAN_MASSIMO_PX)
        val nuovoOffsetY = (corrente.offsetY + panYEffettivo * dt).coerceIn(-PAN_MASSIMO_PX, PAN_MASSIMO_PX)

        val livelloIntero = nuovaProfondita.toInt()
        soundEngine.aggiornaProfondita(livelloIntero)

        val traguardoRaggiunto = (nuovaProfondita / LIVELLI_PER_EVENTO_BONUS).toInt()
        val traguardoPrecedente = (corrente.profondita / LIVELLI_PER_EVENTO_BONUS).toInt()
        if (traguardoRaggiunto > traguardoPrecedente) {
            _state.value = corrente.copy(
                profondita = nuovaProfondita,
                offsetX = nuovoOffsetX,
                offsetY = nuovoOffsetY,
                fase = Fase.EVENTO_BONUS,
                cameraBonus = DiveEngine.generaCamera(livelloIntero, random),
                indiceSelezionatoBonus = null,
                esitoBonus = Esito.NESSUNO
            )
            return
        }

        _state.value = corrente.copy(
            profondita = nuovaProfondita,
            offsetX = nuovoOffsetX,
            offsetY = nuovoOffsetY
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
