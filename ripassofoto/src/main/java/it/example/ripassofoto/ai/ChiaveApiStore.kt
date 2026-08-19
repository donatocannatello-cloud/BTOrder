package it.example.ripassofoto.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Conserva la chiave API personale di Anthropic dell'utente, cifrata a riposo tramite
 * Android Keystore (EncryptedSharedPreferences). La chiave non lascia mai il dispositivo
 * se non nelle richieste dirette a api.anthropic.com fatte da [ClienteClaude].
 */
object ChiaveApiStore {

    private const val NOME_FILE = "chiave_api_claude"
    private const val CHIAVE_PREF = "anthropic_api_key"

    private fun preferenze(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            NOME_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun leggi(context: Context): String? =
        preferenze(context).getString(CHIAVE_PREF, null)?.takeIf { it.isNotBlank() }

    fun salva(context: Context, chiave: String) {
        preferenze(context).edit().putString(CHIAVE_PREF, chiave.trim()).apply()
    }

    fun elimina(context: Context) {
        preferenze(context).edit().remove(CHIAVE_PREF).apply()
    }
}
