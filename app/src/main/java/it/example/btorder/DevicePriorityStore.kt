package it.example.btorder

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val CHIAVE_SERVIZIO_ATTIVO = booleanPreferencesKey("servizio_chiamate_attivo")
    private val CHIAVE_STORIA_NOMI = stringPreferencesKey("storia_nomi_dispositivi")
    private const val SEPARATORE = "§"

    /** Unit Separator (0x1F): separa indirizzo e nome in un record della storia nomi. */
    private const val SEPARATORE_CAMPO = "\u001F"

    /** Record Separator (0x1E): separa un record dal successivo nella storia nomi. */
    private const val SEPARATORE_RECORD = "\u001E"

    /** Quanti indirizzi conservare nella storia nomi: evita una crescita illimitata. */
    private const val MAX_VOCI_STORIA_NOMI = 100

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

    /**
     * Rimuove dalla classifica salvata gli indirizzi Bluetooth non più presenti in
     * [idAncoraValidi]: un dispositivo che viene ri-accoppiato (es. dopo un reset della cache
     * Bluetooth, o scollegato e ricollegato con un indirizzo di sessione diverso) lascia un
     * "fantasma" con il suo vecchio indirizzo, invisibile nella lista (perché non più tra i
     * dispositivi accoppiati) ma ancora presente nell'ordine salvato, nella posizione in cui
     * l'utente l'aveva trascinato. Il nuovo indirizzo con cui il dispositivo si ripresenta viene
     * invece trattato come "mai visto prima" e aggiunto in fondo, perdendo silenziosamente la
     * priorità impostata. Ripulire i fantasmi non basta a impedirlo al prossimo cambio di
     * indirizzo, ma almeno permette di ripartire da una lista leggibile.
     *
     * @return quanti ID sono stati rimossi.
     */
    suspend fun pulisciOrdine(context: Context, idAncoraValidi: Set<String>): Int {
        val attuale = leggiOrdineUnaVolta(context)
        val ripulito = attuale.filter { it in idAncoraValidi }
        salvaOrdine(context, ripulito)
        return attuale.size - ripulito.size
    }

    /**
     * Storia indirizzo → nome degli ultimi dispositivi Bluetooth visti (accoppiati o connessi),
     * registrata ogni volta che l'app li incontra e conservata ANCHE dopo che un indirizzo smette
     * di essere accoppiato. Serve a riconoscere un dispositivo che si ripresenta con un indirizzo
     * diverso da quello salvato in classifica (osservato con un kit auto reale, che cambia
     * indirizzo quasi a ogni connessione): l'indirizzo da solo non è un identificativo stabile
     * per quel dispositivo, ma il nome sì, quindi l'instradamento chiamate lo usa come
     * riconoscimento alternativo quando l'indirizzo non combacia più.
     */
    fun osservaStoriaNomi(context: Context): Flow<Map<String, String>> =
        context.dataStorePriorita.data.map { deserializzaStoriaNomi(it[CHIAVE_STORIA_NOMI].orEmpty()) }

    suspend fun leggiStoriaNomiUnaVolta(context: Context): Map<String, String> =
        osservaStoriaNomi(context).first()

    suspend fun registraNome(context: Context, indirizzo: String, nome: String) {
        if (nome.isBlank()) return
        context.dataStorePriorita.edit { preferenze ->
            val attuale = deserializzaStoriaNomi(preferenze[CHIAVE_STORIA_NOMI].orEmpty()).toMutableMap()
            // Rimosso e re-inserito per spostarlo in fondo (più recente) se già presente, così il
            // taglio al limite massimo scarta prima le voci più vecchie.
            attuale.remove(indirizzo)
            attuale[indirizzo] = nome
            val capata = if (attuale.size > MAX_VOCI_STORIA_NOMI) {
                attuale.entries.drop(attuale.size - MAX_VOCI_STORIA_NOMI).associate { it.key to it.value }
            } else {
                attuale
            }
            preferenze[CHIAVE_STORIA_NOMI] = serializzaStoriaNomi(capata)
        }
    }

    private fun serializzaStoriaNomi(mappa: Map<String, String>): String =
        mappa.entries.joinToString(SEPARATORE_RECORD) { (indirizzo, nome) -> "$indirizzo$SEPARATORE_CAMPO$nome" }

    private fun deserializzaStoriaNomi(testo: String): Map<String, String> {
        if (testo.isBlank()) return emptyMap()
        return testo.split(SEPARATORE_RECORD).mapNotNull { record ->
            val campi = record.split(SEPARATORE_CAMPO)
            if (campi.size < 2) return@mapNotNull null
            campi[0] to campi[1]
        }.toMap()
    }

    /**
     * Se il monitoraggio chiamate è attivo, così che il pulsante nella schermata "Chiamate"
     * mostri lo stato corretto anche dopo che l'utente ha cambiato scheda e ci è tornato
     * (senza questo, uno stato Compose locale si azzererebbe a ogni ricomposizione, pur con
     * il Service Android ancora effettivamente in esecuzione).
     */
    fun osservaServizioAttivo(context: Context): Flow<Boolean> =
        context.dataStorePriorita.data.map { it[CHIAVE_SERVIZIO_ATTIVO] ?: false }

    suspend fun leggiServizioAttivoUnaVolta(context: Context): Boolean =
        osservaServizioAttivo(context).first()

    suspend fun impostaServizioAttivo(context: Context, attivo: Boolean) {
        context.dataStorePriorita.edit { preferenze ->
            preferenze[CHIAVE_SERVIZIO_ATTIVO] = attivo
        }
    }
}
