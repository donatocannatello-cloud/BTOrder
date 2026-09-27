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
            rinforzoBassi = prefs.getFloat("rinforzoBassi", d.rinforzoBassi),
            sensibilita = prefs.getFloat("sensibilita", d.sensibilita),
            volumeTheremin = prefs.getFloat("volumeTheremin", d.volumeTheremin),
            volumeBase = prefs.getFloat("volumeBase", d.volumeBase),
            margine = prefs.getFloat("margine", d.margine),
            puntoMano = enumOppure(prefs.getString("puntoMano", null), d.puntoMano),
            rilevatore = enumOppure(prefs.getString("rilevatore", null), d.rilevatore),
            modoDueMani = enumOppure(prefs.getString("modoDueMani", null), d.modoDueMani),
            prossimitaMuta = prefs.getBoolean("prossimitaMuta", d.prossimitaMuta),
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
            .putFloat("rinforzoBassi", i.rinforzoBassi)
            .putFloat("sensibilita", i.sensibilita)
            .putFloat("volumeTheremin", i.volumeTheremin)
            .putFloat("volumeBase", i.volumeBase)
            .putFloat("margine", i.margine)
            .putString("puntoMano", i.puntoMano.name)
            .putString("rilevatore", i.rilevatore.name)
            .putString("modoDueMani", i.modoDueMani.name)
            .putBoolean("prossimitaMuta", i.prossimitaMuta)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOppure(nome: String?, predefinito: E): E =
        enumValues<E>().firstOrNull { it.name == nome } ?: predefinito
}

