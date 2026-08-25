package it.example.frattalogic.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.example.frattalogic.audio.SoundEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class Esito { NESSUNO, CORRETTO, SBAGLIATO }

data class GameState(
    val puzzle: Puzzle,
    val punteggio: Int = 0,
    val streak: Int = 0,
    val recordStreak: Int = 0,
    val difficolta: Int = 0,
    val indiceSelezionato: Int? = null,
    val esito: Esito = Esito.NESSUNO,
    val puoRispondere: Boolean = true
)

class GameViewModel : ViewModel() {

    private val random = Random(System.nanoTime())
    val soundEngine = SoundEngine()

    private val _state = MutableStateFlow(GameState(puzzle = PuzzleEngine.generaPuzzle(0, random)))
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun avviaAudio() = soundEngine.start()
    fun fermaAudio() = soundEngine.stop()

    fun rispondi(indice: Int) {
        val corrente = _state.value
        if (!corrente.puoRispondere) return

        val corretto = indice == corrente.puzzle.indiceCorretto
        val nuovoStreak = if (corretto) corrente.streak + 1 else 0
        val nuovoPunteggio = if (corretto) {
            corrente.punteggio + 10 + corrente.difficolta * 2
        } else {
            corrente.punteggio
        }
        val nuovaDifficolta = (nuovoStreak / 3).coerceAtMost(20)

        soundEngine.onDifficolta(nuovaDifficolta)
        soundEngine.onRisposta(corretto, nuovoStreak)
        if (corretto && nuovoStreak > 0 && nuovoStreak % 5 == 0) {
            soundEngine.onTraguardo()
        }

        _state.value = corrente.copy(
            punteggio = nuovoPunteggio,
            streak = nuovoStreak,
            recordStreak = maxOf(corrente.recordStreak, nuovoStreak),
            difficolta = nuovaDifficolta,
            indiceSelezionato = indice,
            esito = if (corretto) Esito.CORRETTO else Esito.SBAGLIATO,
            puoRispondere = false
        )

        viewModelScope.launch {
            delay(if (corretto) 700L else 1100L)
            prossimoPuzzle()
        }
    }

    private fun prossimoPuzzle() {
        val corrente = _state.value
        _state.value = corrente.copy(
            puzzle = PuzzleEngine.generaPuzzle(corrente.difficolta, random),
            indiceSelezionato = null,
            esito = Esito.NESSUNO,
            puoRispondere = true
        )
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.stop()
    }
}
