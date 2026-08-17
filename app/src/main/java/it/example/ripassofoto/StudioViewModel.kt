package it.example.ripassofoto

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    suspend fun riconosciTesto(file: File): String =
        RiconoscimentoTesto.riconosciDaFile(getApplication(), file)

    fun generaNuoveDomande(testo: String) {
        _domandeCorrenti.value = GeneratoreDomande.genera(testo)
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
