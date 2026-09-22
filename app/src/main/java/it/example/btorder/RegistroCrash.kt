package it.example.btorder

import android.content.Context

/**
 * Ultimo crash non gestito dell'app, salvato con SharedPreferences sincrone (non DataStore,
 * che è asincrono e potrebbe non fare in tempo a scrivere prima che il processo termini) così
 * che al riavvio successivo BTOrder possa mostrare all'utente il messaggio ed evitare di dover
 * diagnosticare "a scatola chiusa" senza accesso al logcat del dispositivo.
 */
object RegistroCrash {
    private const val PREFERENZE = "btorder_crash"
    private const val CHIAVE_TRACCIA = "ultima_traccia"
    private const val CHIAVE_ISTANTE = "ultimo_istante"

    fun salva(context: Context, traccia: String) {
        context.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE).edit()
            .putString(CHIAVE_TRACCIA, traccia)
            .putLong(CHIAVE_ISTANTE, System.currentTimeMillis())
            .apply()
    }

    /** La traccia dell'ultimo crash salvato, se presente, insieme a quando è avvenuto. */
    fun leggiUltima(context: Context): Pair<String, Long>? {
        val preferenze = context.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE)
        val traccia = preferenze.getString(CHIAVE_TRACCIA, null) ?: return null
        return traccia to preferenze.getLong(CHIAVE_ISTANTE, 0L)
    }

    fun cancella(context: Context) {
        context.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
