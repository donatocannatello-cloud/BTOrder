package it.example.theremin.audio

import android.content.Context

/** Salva e rilegge le [Impostazioni] tra un avvio e l'altro (SharedPreferences). */
class ImpostazioniStore(context: Context) {

    private val prefs = context.getSharedPreferences("impostazioni_suono", Context.MODE_PRIVATE)

    fun leggi(): Impostazioni {
        val d = Impostazioni()
        return Impostazioni(
            timbro = enumOppure(prefs.getString("timbro", null), d.timbro),
            ottava = prefs.getInt("ottava", d.ottava),
            estensioneOttave = prefs.getInt("estensioneOttave", d.estensioneOttave),
            tonica = prefs.getInt("tonica", d.tonica),
            vibrato = prefs.getFloat("vibrato", d.vibrato),
            vibratoHz = prefs.getFloat("vibratoHz", d.vibratoHz),
            portamentoMs = prefs.getFloat("portamentoMs", d.portamentoMs),
            eco = prefs.getFloat("eco", d.eco),
            calore = prefs.getFloat("calore", d.calore),
            margine = prefs.getFloat("margine", d.margine),
            puntoMano = enumOppure(prefs.getString("puntoMano", null), d.puntoMano),
        )
    }

    fun salva(i: Impostazioni) {
        prefs.edit()
            .putString("timbro", i.timbro.name)
            .putInt("ottava", i.ottava)
            .putInt("estensioneOttave", i.estensioneOttave)
            .putInt("tonica", i.tonica)
            .putFloat("vibrato", i.vibrato)
            .putFloat("vibratoHz", i.vibratoHz)
            .putFloat("portamentoMs", i.portamentoMs)
            .putFloat("eco", i.eco)
            .putFloat("calore", i.calore)
            .putFloat("margine", i.margine)
            .putString("puntoMano", i.puntoMano.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOppure(nome: String?, predefinito: E): E =
        enumValues<E>().firstOrNull { it.name == nome } ?: predefinito
}

