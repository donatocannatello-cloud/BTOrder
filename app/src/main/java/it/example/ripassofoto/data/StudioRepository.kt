package it.example.ripassofoto.data

import kotlinx.coroutines.flow.Flow

/** Facciata semplice sopra il DAO: isola il resto dell'app dai dettagli di Room. */
class StudioRepository(private val dao: PaginaStudioDao) {

    val pagine: Flow<List<PaginaStudio>> = dao.osservaTutte()

    suspend fun leggiPerId(id: Long): PaginaStudio? = dao.leggiPerId(id)

    suspend fun salva(pagina: PaginaStudio): Long = dao.inserisci(pagina)

    suspend fun aggiorna(pagina: PaginaStudio) = dao.aggiorna(pagina)

    suspend fun elimina(pagina: PaginaStudio) = dao.elimina(pagina)
}
