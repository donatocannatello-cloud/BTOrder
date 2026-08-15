package it.example.btorder

import android.content.Context
import android.content.Intent

/** Un'app installata avviabile dall'utente (ha un'Activity con categoria LAUNCHER). */
data class AppLanciabile(val packageName: String, val nomeVisualizzato: String)

/** Enumera le app installate che l'utente può avviare, per la scelta dell'automazione. */
object AppLanciabili {

    @Suppress("DEPRECATION")
    fun elenca(context: Context): List<AppLanciabile> {
        val packageManager = context.packageManager
        val intentPrincipale = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(intentPrincipale, 0)
            .map { risoluzione ->
                AppLanciabile(
                    packageName = risoluzione.activityInfo.packageName,
                    nomeVisualizzato = risoluzione.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.nomeVisualizzato.lowercase() }
    }
}
