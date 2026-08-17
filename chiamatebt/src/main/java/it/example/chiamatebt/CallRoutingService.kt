package it.example.chiamatebt

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servizio in foreground che, per tutta la durata dell'esecuzione, osserva lo
 * stato della linea telefonica tramite [TelephonyCallback] (API 31+). Quando
 * la chiamata passa allo stato OFFHOOK, legge l'ordine di priorità salvato
 * dall'utente e instrada l'audio verso il primo dispositivo effettivamente
 * disponibile in quel momento.
 */
class CallRoutingService : Service() {

    private val ambitoCoroutine = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var audioManager: AudioManager

    private val ascoltatoreStatoChiamata = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> instradaAudioChiamata()
                TelephonyManager.CALL_STATE_IDLE -> audioManager.clearCommunicationDevice()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        creaCanaleNotifica()
        startForeground(ID_NOTIFICA, costruisciNotifica())
        registraAscoltatoreChiamate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (haPermessoStatoChiamata()) {
            telephonyManager.unregisterTelephonyCallback(ascoltatoreStatoChiamata)
        }
        ambitoCoroutine.cancel()
        super.onDestroy()
    }

    private fun haPermessoStatoChiamata(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    private fun registraAscoltatoreChiamate() {
        if (!haPermessoStatoChiamata()) {
            // Senza il permesso il servizio non può svolgere il suo compito: si ferma subito.
            stopSelf()
            return
        }
        telephonyManager.registerTelephonyCallback(mainExecutor, ascoltatoreStatoChiamata)
    }

    /** Legge l'ordine salvato e applica il primo dispositivo disponibile. */
    private fun instradaAudioChiamata() {
        ambitoCoroutine.launch {
            val ordineSalvato = DevicePriorityStore.leggiOrdineUnaVolta(applicationContext)
            if (ordineSalvato.isEmpty()) return@launch
            DispositiviAudio.applicaPrimoDispositivoDisponibile(audioManager, ordineSalvato)
        }
    }

    private fun creaCanaleNotifica() {
        val canale = NotificationChannel(
            CANALE_NOTIFICA,
            "Instradamento chiamate",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Notifica persistente mentre ChiamateBT gestisce l'audio delle chiamate"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canale)
    }

    private fun costruisciNotifica() =
        NotificationCompat.Builder(this, CANALE_NOTIFICA)
            .setContentTitle("ChiamateBT attivo")
            .setContentText("In ascolto per instradare l'audio delle chiamate")
            .setSmallIcon(R.drawable.ic_notifica)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val CANALE_NOTIFICA = "canale_instradamento_chiamate"
        private const val ID_NOTIFICA = 1
    }
}
