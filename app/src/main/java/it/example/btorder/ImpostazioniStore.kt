package it.example.btorder

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Istanza di DataStore dedicata alle impostazioni generali dell'app. */
private val Context.dataStoreImpostazioni by preferencesDataStore(name = "btorder_impostazioni")

/**
 * Impostazioni generali dell'app, non legate a un singolo dispositivo. Per ora la sola
 * "modalità silenziosa": esclude le notifiche non essenziali (avvisi e scorciatoie di
 * automazione). La notifica permanente che segnala un Service in foreground non può essere
 * esclusa: è Android stesso a richiederla per lasciarlo girare in background.
 */
object ImpostazioniStore {

    private val CHIAVE_MODALITA_SILENZIOSA = booleanPreferencesKey("modalita_silenziosa")

    fun osservaModalitaSilenziosa(context: Context): Flow<Boolean> =
        context.dataStoreImpostazioni.data.map { it[CHIAVE_MODALITA_SILENZIOSA] ?: false }

    suspend fun leggiModalitaSilenziosaUnaVolta(context: Context): Boolean =
        osservaModalitaSilenziosa(context).first()

    suspend fun impostaModalitaSilenziosa(context: Context, attiva: Boolean) {
        context.dataStoreImpostazioni.edit { preferenze ->
            preferenze[CHIAVE_MODALITA_SILENZIOSA] = attiva
        }
    }
}
