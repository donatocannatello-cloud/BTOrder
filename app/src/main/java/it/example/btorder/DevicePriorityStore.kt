package it.example.btorder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Istanza di DataStore dedicata all'ordine di priorità chiamate (una sola per Context). */
private val Context.dataStorePriorita by preferencesDataStore(name = "chiamatebt_preferenze")

/**
 * Gestisce la persistenza dell'ordine di priorità dei dispositivi audio
 * tramite Jetpack DataStore Preferences. L'ordine viene salvato come un'unica
 * stringa di ID separati da un delimitatore: a differenza di un Set,
 * questo preserva l'ordinamento scelto dall'utente tra un riavvio e l'altro.
 */
object DevicePriorityStore {

    private val CHIAVE_ORDINE = stringPreferencesKey("ordine_dispositivi")
    private const val SEPARATORE = "§"

    /** Flusso con la lista ordinata di ID salvata (vuota se non è mai stata salvata). */
    fun osservaOrdine(context: Context): Flow<List<String>> =
        context.dataStorePriorita.data.map { preferenze ->
            preferenze[CHIAVE_ORDINE]
                ?.split(SEPARATORE)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    /** Salva l'ordine corrente (lista di ID) su disco. */
    suspend fun salvaOrdine(context: Context, idsOrdinati: List<String>) {
        context.dataStorePriorita.edit { preferenze ->
            preferenze[CHIAVE_ORDINE] = idsOrdinati.joinToString(SEPARATORE)
        }
    }

    /** Lettura una tantum dell'ordine salvato, comoda da usare dal Service. */
    suspend fun leggiOrdineUnaVolta(context: Context): List<String> =
        osservaOrdine(context).first()
}
