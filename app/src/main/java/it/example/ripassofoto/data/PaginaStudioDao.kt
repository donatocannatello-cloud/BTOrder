package it.example.ripassofoto.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaginaStudioDao {

    @Query("SELECT * FROM pagine_studio ORDER BY creataIl DESC")
    fun osservaTutte(): Flow<List<PaginaStudio>>

    @Query("SELECT * FROM pagine_studio WHERE id = :id")
    suspend fun leggiPerId(id: Long): PaginaStudio?

    @Insert
    suspend fun inserisci(pagina: PaginaStudio): Long

    @Update
    suspend fun aggiorna(pagina: PaginaStudio)

    @Delete
    suspend fun elimina(pagina: PaginaStudio)
}
