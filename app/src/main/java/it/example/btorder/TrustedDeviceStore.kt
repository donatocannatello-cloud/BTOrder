package it.example.btorder

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Istanza di DataStore a livello di applicazione (una sola per Context). */
private val Context.dataStore by preferencesDataStore(name = "btorder_preferenze")

/**
 * Un dispositivo Bluetooth marcato come "di fiducia" (tipicamente l'auto) con le
 * automazioni da applicare quando ci si connette e da annullare alla disconnessione.
 */
data class DispositivoFiducia(
    val indirizzo: String,
    val nome: String,
    val schermoSempreAcceso: Boolean = false,
    val estendiTimeoutSchermo: Boolean = false,
    val timeoutEstesoSecondi: Int = 600,
    val appDaAvviarePackage: String? = null,
    val appDaAvviareNome: String? = null
)

/**
 * Persiste con Jetpack DataStore Preferences l'elenco dei dispositivi di fiducia e le
 * loro automazioni, più la preferenza di avvio automatico del monitoraggio al boot.
 * Ogni dispositivo viene serializzato come record di campi separati da un carattere di
 * controllo ASCII (Unit/Record Separator) improbabile in un nome Bluetooth, evitando così
 * problemi di escaping per nomi che contengono caratteri "normali" come virgole o pipe.
 */
object TrustedDeviceStore {

    private val CHIAVE_DISPOSITIVI = stringPreferencesKey("dispositivi_fiducia")
    private val CHIAVE_AVVIO_AUTOMATICO = booleanPreferencesKey("avvio_automatico_boot")
    private val CHIAVE_SERVIZIO_ATTIVO = booleanPreferencesKey("servizio_automazioni_attivo")
    private val CHIAVE_ULTIME_CONNESSIONI = stringPreferencesKey("ultime_connessioni")

    /** Unit Separator (0x1F): separa i campi di un singolo dispositivo. */
    private const val SEPARATORE_CAMPO = ""

    /** Record Separator (0x1E): separa un dispositivo dal successivo. */
    private const val SEPARATORE_RECORD = ""

    /** Flusso con l'elenco dei dispositivi di fiducia salvati (vuoto se nessuno). */
    fun osservaDispositivi(context: Context): Flow<List<DispositivoFiducia>> =
        context.dataStore.data.map { preferenze ->
            deserializza(preferenze[CHIAVE_DISPOSITIVI].orEmpty())
        }

    /** Lettura una tantum, comoda da usare dal Service. */
    suspend fun leggiDispositiviUnaVolta(context: Context): List<DispositivoFiducia> =
        osservaDispositivi(context).first()

    suspend fun salvaDispositivi(context: Context, dispositivi: List<DispositivoFiducia>) {
        context.dataStore.edit { preferenze ->
            preferenze[CHIAVE_DISPOSITIVI] = serializza(dispositivi)
        }
    }

    suspend fun salvaDispositivo(context: Context, dispositivo: DispositivoFiducia) {
        val attuali = leggiDispositiviUnaVolta(context)
        val aggiornati = attuali.filterNot { it.indirizzo == dispositivo.indirizzo } + dispositivo
        salvaDispositivi(context, aggiornati)
    }

    suspend fun rimuoviDispositivo(context: Context, indirizzo: String) {
        val attuali = leggiDispositiviUnaVolta(context)
        salvaDispositivi(context, attuali.filterNot { it.indirizzo == indirizzo })
    }

    fun osservaAvvioAutomatico(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[CHIAVE_AVVIO_AUTOMATICO] ?: false }

    suspend fun leggiAvvioAutomaticoUnaVolta(context: Context): Boolean =
        osservaAvvioAutomatico(context).first()

    suspend fun impostaAvvioAutomatico(context: Context, attivo: Boolean) {
        context.dataStore.edit { preferenze ->
            preferenze[CHIAVE_AVVIO_AUTOMATICO] = attivo
        }
    }

    /**
     * Se il monitoraggio automazioni è attivo, così che il pulsante nella schermata "Auto e
     * dispositivi" mostri lo stato corretto anche dopo che l'utente ha cambiato scheda e ci è
     * tornato (senza questo, uno stato Compose locale si azzererebbe a ogni ricomposizione, pur
     * con il Service Android ancora effettivamente in esecuzione).
     */
    fun osservaServizioAttivo(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[CHIAVE_SERVIZIO_ATTIVO] ?: false }

    suspend fun impostaServizioAttivo(context: Context, attivo: Boolean) {
        context.dataStore.edit { preferenze ->
            preferenze[CHIAVE_SERVIZIO_ATTIVO] = attivo
        }
    }

    /**
     * Data/ora (epoch millis) dell'ultima volta in cui BTOrder ha osservato la connessione di
     * ciascun dispositivo Bluetooth, registrata dal Service delle automazioni e dalla
     * schermata principale quando sono attivi. Non è una cronologia completa fornita dal
     * sistema (Android non la espone alle app): riflette solo ciò che BTOrder ha visto mentre
     * era in esecuzione, per questo è mostrata come informazione "se disponibile".
     */
    fun osservaUltimeConnessioni(context: Context): Flow<Map<String, Long>> =
        context.dataStore.data.map { deserializzaConnessioni(it[CHIAVE_ULTIME_CONNESSIONI].orEmpty()) }

    suspend fun registraConnessione(context: Context, indirizzo: String, istante: Long) {
        context.dataStore.edit { preferenze ->
            val attuali = deserializzaConnessioni(preferenze[CHIAVE_ULTIME_CONNESSIONI].orEmpty()).toMutableMap()
            attuali[indirizzo] = istante
            preferenze[CHIAVE_ULTIME_CONNESSIONI] = serializzaConnessioni(attuali)
        }
    }

    suspend fun rimuoviUltimaConnessione(context: Context, indirizzo: String) {
        context.dataStore.edit { preferenze ->
            val attuali = deserializzaConnessioni(preferenze[CHIAVE_ULTIME_CONNESSIONI].orEmpty()).toMutableMap()
            attuali.remove(indirizzo)
            preferenze[CHIAVE_ULTIME_CONNESSIONI] = serializzaConnessioni(attuali)
        }
    }

    private fun serializzaConnessioni(mappa: Map<String, Long>): String =
        mappa.entries.joinToString(SEPARATORE_RECORD) { (indirizzo, istante) -> "$indirizzo$SEPARATORE_CAMPO$istante" }

    private fun deserializzaConnessioni(testo: String): Map<String, Long> {
        if (testo.isBlank()) return emptyMap()
        return testo.split(SEPARATORE_RECORD).mapNotNull { record ->
            val campi = record.split(SEPARATORE_CAMPO)
            if (campi.size < 2) return@mapNotNull null
            val istante = campi[1].toLongOrNull() ?: return@mapNotNull null
            campi[0] to istante
        }.toMap()
    }

    private fun serializza(dispositivi: List<DispositivoFiducia>): String =
        dispositivi.joinToString(SEPARATORE_RECORD) { d ->
            listOf(
                d.indirizzo,
                d.nome,
                if (d.schermoSempreAcceso) "1" else "0",
                if (d.estendiTimeoutSchermo) "1" else "0",
                d.timeoutEstesoSecondi.toString(),
                d.appDaAvviarePackage.orEmpty(),
                d.appDaAvviareNome.orEmpty()
            ).joinToString(SEPARATORE_CAMPO)
        }

    private fun deserializza(testo: String): List<DispositivoFiducia> {
        if (testo.isBlank()) return emptyList()
        return testo.split(SEPARATORE_RECORD).mapNotNull { record ->
            val campi = record.split(SEPARATORE_CAMPO)
            if (campi.size < 7) return@mapNotNull null
            DispositivoFiducia(
                indirizzo = campi[0],
                nome = campi[1],
                schermoSempreAcceso = campi[2] == "1",
                estendiTimeoutSchermo = campi[3] == "1",
                timeoutEstesoSecondi = campi[4].toIntOrNull() ?: 600,
                appDaAvviarePackage = campi[5].ifBlank { null },
                appDaAvviareNome = campi[6].ifBlank { null }
            )
        }
    }
}
