package it.example.ripassofoto

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.example.ripassofoto.ai.ChiaveApiStore
import it.example.ripassofoto.ai.ClienteClaude
import it.example.ripassofoto.ai.ErroreClaude
import it.example.ripassofoto.data.AppDatabase
import it.example.ripassofoto.data.PaginaStudio
import it.example.ripassofoto.data.StudioRepository
import it.example.ripassofoto.ocr.RiconoscimentoTesto
import it.example.ripassofoto.quiz.Domanda
import it.example.ripassofoto.quiz.GeneratoreDomande
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudioRepository(AppDatabase.ottieni(application).paginaStudioDao())

    val pagine: StateFlow<List<PaginaStudio>> = repository.pagine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Foto appena scattata, in attesa di essere passata alla schermata di revisione del testo. */
    var fileFotoCorrente by mutableStateOf<File?>(null)

    /** Domande della sessione di quiz in corso (rigenerate a ogni "Genera domande" / "Rifai il quiz"). */
    private val _domandeCorrenti = MutableStateFlow<List<Domanda>>(emptyList())
    val domandeCorrenti: StateFlow<List<Domanda>> = _domandeCorrenti

    /** True mentre è in corso una generazione delle domande (può richiedere qualche secondo con l'IA). */
    private val _generandoDomande = MutableStateFlow(false)
    val generandoDomande: StateFlow<Boolean> = _generandoDomande

    /** Messaggio non bloccante da mostrare una volta sola (es. fallback dall'IA al generatore locale). */
    private val _avviso = MutableStateFlow<String?>(null)
    val avviso: StateFlow<String?> = _avviso

    fun leggiChiaveApi(): String = ChiaveApiStore.leggi(getApplication()).orEmpty()

    fun salvaChiaveApi(chiave: String) = ChiaveApiStore.salva(getApplication(), chiave)

    fun eliminaChiaveApi() = ChiaveApiStore.elimina(getApplication())

    fun consumaAvviso() {
        _avviso.value = null
    }

    suspend fun riconosciTesto(file: File): String =
        RiconoscimentoTesto.riconosciDaFile(getApplication(), file)

    /**
     * Genera le domande di verifica. Se è configurata una chiave API di Claude la usa per
     * un'analisi più approfondita (domande di comprensione/applicazione/analisi, non solo
     * completamento di parole); altrimenti — o se la chiamata fallisce per qualunque motivo
     * (rete assente, chiave non valida, ecc.) — ricade sul generatore euristico locale, che
     * funziona sempre offline.
     */
    suspend fun generaNuoveDomande(testo: String) {
        _generandoDomande.value = true
        try {
            val chiave = ChiaveApiStore.leggi(getApplication())
            if (chiave != null) {
                try {
                    val domande = ClienteClaude.generaDomande(chiave, testo)
                    if (domande.isNotEmpty()) {
                        _domandeCorrenti.value = domande
                        _avviso.value = null
                        return
                    }
                    _avviso.value = "Claude non ha restituito domande valide: uso il generatore locale."
                } catch (e: ErroreClaude) {
                    _avviso.value = "${e.message} Uso il generatore locale."
                } catch (e: Exception) {
                    _avviso.value = "Errore imprevisto nella generazione con l'IA: uso il generatore locale."
                }
            }
            _domandeCorrenti.value = GeneratoreDomande.genera(testo)
        } finally {
            _generandoDomande.value = false
        }
    }

    suspend fun salvaPagina(titolo: String, testo: String, percorsoImmagine: String?): Long =
        repository.salva(
            PaginaStudio(
                titolo = titolo.ifBlank { "Pagina senza titolo" },
                testoEstratto = testo,
                percorsoImmagine = percorsoImmagine,
                creataIl = System.currentTimeMillis()
            )
        )

    suspend fun leggiPagina(id: Long): PaginaStudio? = repository.leggiPerId(id)

    suspend fun registraEsito(pagina: PaginaStudio, punteggio: Int, totale: Int) {
        repository.aggiorna(pagina.copy(ultimoPunteggio = punteggio, ultimoTotale = totale))
    }

    suspend fun eliminaPagina(pagina: PaginaStudio) = repository.elimina(pagina)

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StudioViewModel(application) as T
            }
    }
}
