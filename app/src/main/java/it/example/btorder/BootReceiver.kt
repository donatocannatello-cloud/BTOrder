package it.example.btorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Riavvia automaticamente [ProximityAutomationService] dopo l'accensione del telefono, se
 * l'utente ha attivato l'apposita preferenza. Gli avvii di foreground service da un
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
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, ProximityAutomationService::class.java)
                    )
                }
            } finally {
                risultatoPendente.finish()
            }
        }
    }
}
