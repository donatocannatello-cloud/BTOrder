package it.example.btorder

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

/**
 * Registro testuale delle ultime righe diagnostiche dell'instradamento chiamate (SharedPreferences
 * sincrone, come [RegistroCrash]): una notifica non è un canale affidabile per un testo lungo,
 * Android la tronca a una riga anche da "espansa" su molti produttori (visto con Samsung One UI).
 * Qui invece il testo resta integralmente leggibile e copiabile dalla pagina Impostazioni.
 */
object RegistroDiagnostica {
    private const val PREFERENZE = "btorder_diagnostica"
    private const val CHIAVE_LOG = "log"

    /** Limite di caratteri conservati: evita una crescita illimitata su un servizio di lunga durata. */
    private const val MAX_CARATTERI = 8000

    fun aggiungi(context: Context, riga: String) {
        val preferenze = context.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE)
        val orario = DateFormat.format("dd/MM HH:mm:ss", Date())
        val attuale = preferenze.getString(CHIAVE_LOG, "").orEmpty()
        val aggiornato = (if (attuale.isEmpty()) "" else "$attuale\n") + "$orario — $riga"
        preferenze.edit().putString(CHIAVE_LOG, aggiornato.takeLast(MAX_CARATTERI)).apply()
    }

    fun leggi(context: Context): String =
        context.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE).getString(CHIAVE_LOG, "").orEmpty()

    fun cancella(context: Context) {
        context.getSharedPreferences(PREFERENZE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
