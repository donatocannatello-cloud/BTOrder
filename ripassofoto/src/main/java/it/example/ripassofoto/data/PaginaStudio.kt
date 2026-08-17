package it.example.ripassofoto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una pagina fotografata dallo studente: testo riconosciuto via OCR (eventualmente
 * corretto a mano) più l'esito dell'ultimo quiz svolto su quel testo.
 */
@Entity(tableName = "pagine_studio")
data class PaginaStudio(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titolo: String,
    val testoEstratto: String,
    val percorsoImmagine: String?,
    val creataIl: Long,
    val ultimoPunteggio: Int? = null,
    val ultimoTotale: Int? = null
)
