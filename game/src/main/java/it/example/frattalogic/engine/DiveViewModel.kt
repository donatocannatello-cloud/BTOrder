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

enum class Esito { NESSUNO, RISOLTO, ROTTURA }

data class DiveState(
    val camera: Camera,
    val punteggio: Int = 0,
    val profonditaMassima: Int = 0,
    val indiceSelezionato: Int? = null,
    val esito: Esito = Esito.NESSUNO,
    val puoToccare: Boolean = true
)

/**
 * Stato della discesa: ad ogni nodo dissonante trovato si scende di un
 * livello (nuova camera più difficile, un nuovo strumento può entrare nel
 * mix sonoro); toccare un nodo "normale" costa un piccolo passo indietro e
 * un cluster dissonante, ma non interrompe la discesa.
 */
class DiveViewModel : ViewModel() {

    private val random = Random(System.nanoTime())
    val soundEngine = SoundEngine()

    private val _state = MutableStateFlow(DiveState(camera = DiveEngine.generaCamera(0, random)))
    val state: StateFlow<DiveState> = _state.asStateFlow()

    fun avviaAudio() {
        soundEngine.start()
        soundEngine.aggiornaProfondita(_state.value.camera.profondita)
    }

    fun fermaAudio() = soundEngine.stop()

    fun tocca(indiceNodo: Int) {
        val corrente = _state.value
        if (!corrente.puoToccare) return

        val risolto = indiceNodo == corrente.camera.indiceDissonante
        val profonditaAttuale = corrente.camera.profondita
        val nuovaProfondita = if (risolto) profonditaAttuale + 1 else (profonditaAttuale - 1).coerceAtLeast(0)
        val nuovoPunteggio = if (risolto) corrente.punteggio + 10 + profonditaAttuale * 2 else corrente.punteggio

        if (risolto) {
            soundEngine.onRisolto(nuovaProfondita)
        } else {
            soundEngine.onRottura()
        }

        _state.value = corrente.copy(
            punteggio = nuovoPunteggio,
            profonditaMassima = maxOf(corrente.profonditaMassima, nuovaProfondita),
            indiceSelezionato = indiceNodo,
            esito = if (risolto) Esito.RISOLTO else Esito.ROTTURA,
            puoToccare = false
        )

        viewModelScope.launch {
            delay(if (risolto) 900L else 650L)
            prossimaCamera(nuovaProfondita)
        }
    }

    private fun prossimaCamera(nuovaProfondita: Int) {
        val corrente = _state.value
        _state.value = corrente.copy(
            camera = DiveEngine.generaCamera(nuovaProfondita, random),
            indiceSelezionato = null,
            esito = Esito.NESSUNO,
            puoToccare = true
        )
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.stop()
    }
}
