package it.example.btorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Riavvia automaticamente i Service che erano attivi prima dello spegnimento: le automazioni
 * di prossimità se l'utente ha attivato l'apposita preferenza, e l'instradamento chiamate se
 * era acceso al momento dello spegnimento. Gli avvii di foreground service da un
 * BroadcastReceiver per BOOT_COMPLETED sono tra le eccezioni consentite da Android alle
 * restrizioni sull'avvio di servizi dal background.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val risultatoPendente = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val avvioAutomatico = TrustedDeviceStore.leggiAvvioAutomaticoUnaVolta(context)
                if (avvioAutomatico) {
                    avviaServizioInSicurezzaAlBoot(
                        context,
                        Intent(context, ProximityAutomationService::class.java)
                    )
                }
                val instradamentoAttivo = DevicePriorityStore.leggiServizioAttivoUnaVolta(context)
                if (instradamentoAttivo) {
                    val riuscito = avviaServizioInSicurezzaAlBoot(
                        context,
                        Intent(context, CallRoutingService::class.java)
                    )
                    if (!riuscito) DevicePriorityStore.impostaServizioAttivo(context, false)
                }
            } finally {
                risultatoPendente.finish()
            }
        }
    }

    /**
     * Come [avviaServizioInSicurezza] in MainActivity.kt: un riavvio automatico al boot non deve
     * mai poter mandare in crash il processo (qui non c'è nemmeno una UI a segnalarlo).
     */
    private fun avviaServizioInSicurezzaAlBoot(context: Context, intent: Intent): Boolean =
        try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            false
        }
}
