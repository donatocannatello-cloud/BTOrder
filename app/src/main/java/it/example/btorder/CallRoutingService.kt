package it.example.btorder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
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
import kotlinx.coroutines.delay
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
    private lateinit var notificationManager: NotificationManager

    /** true tra l'OFFHOOK e il successivo IDLE: usato per sapere se vale la pena reagire
     *  a un nuovo dispositivo audio che compare durante la chiamata. Scritta sul thread
     *  principale (i callback di sistema) e letta anche dalla coroutine di instradamento. */
    @Volatile
    private var chiamataInCorso = false

    private val ascoltatoreStatoChiamata = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    chiamataInCorso = true
                    instradaAudioChiamata()
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    chiamataInCorso = false
                    audioManager.clearCommunicationDevice()
                }
            }
        }
    }

    /**
     * Il dispositivo Bluetooth in cima alla classifica spesso non è ancora tra
     * [AudioManager.getAvailableCommunicationDevices] esattamente nell'istante dell'OFFHOOK: il
     * sistema può impiegare istanti per stabilire il canale SCO/A2DP con l'auricolare. Oltre ai
     * tentativi ravvicinati in [instradaAudioChiamata], questo callback riprova ogni volta che
     * un nuovo dispositivo audio compare durante una chiamata già in corso (es. l'auto si
     * connette qualche secondo dopo l'inizio della chiamata).
     */
    private val ascoltatoreNuoviDispositivi = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (chiamataInCorso) instradaAudioChiamata()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NotificationManager::class.java)

        creaCanaleNotifica()
        startForeground(ID_NOTIFICA, costruisciNotifica("In ascolto per instradare l'audio delle chiamate"))
        registraAscoltatoreChiamate()
        audioManager.registerAudioDeviceCallback(ascoltatoreNuoviDispositivi, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (haPermessoStatoChiamata()) {
            telephonyManager.unregisterTelephonyCallback(ascoltatoreStatoChiamata)
        }
        audioManager.unregisterAudioDeviceCallback(ascoltatoreNuoviDispositivi)
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
            // Senza il permesso il servizio non può svolgere il suo compito: si ferma subito, ma
            // prima sincronizza lo stato "attivo" salvato (altrimenti il pulsante in app resta
            // bloccato su "Ferma", come se il servizio funzionasse, mentre in realtà è già morto)
            // e lascia una notifica non legata al Service, così l'utente capisce perché.
            ambitoCoroutine.launch { DevicePriorityStore.impostaServizioAttivo(applicationContext, false) }
            notificationManager.notify(
                ID_NOTIFICA_AVVISO,
                NotificationCompat.Builder(this, CANALE_NOTIFICA)
                    .setContentTitle("BTOrder - Instradamento chiamate fermo")
                    .setContentText("Manca il permesso \"Telefono\": aprilo dall'app e riavvia il monitoraggio")
                    .setSmallIcon(R.drawable.ic_notifica)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            )
            stopSelf()
            return
        }
        telephonyManager.registerTelephonyCallback(mainExecutor, ascoltatoreStatoChiamata)
    }

    /**
     * Legge l'ordine salvato e applica il primo dispositivo disponibile, riprovando per
     * qualche secondo se il dispositivo in cima alla classifica non compare subito tra quelli
     * effettivamente disponibili (vedi nota su [ascoltatoreNuoviDispositivi]). L'esito di ogni
     * tentativo viene scritto nella notifica del servizio: è l'unico modo per l'utente (e per
     * chi lo assiste) di capire cosa è successo davvero durante l'ultima chiamata, senza dover
     * leggere i log del telefono.
     */
    private fun instradaAudioChiamata() {
        ambitoCoroutine.launch {
            val ordineSalvato = DevicePriorityStore.leggiOrdineUnaVolta(applicationContext)
            if (ordineSalvato.isEmpty()) {
                aggiornaNotifica("Nessun dispositivo in classifica: apri l'app e trascina almeno una voce")
                return@launch
            }

            repeat(TENTATIVI_INSTRADAMENTO) { tentativo ->
                if (!chiamataInCorso) return@launch
                when (val esito = DispositiviAudio.applicaPrimoDispositivoDisponibile(audioManager, ordineSalvato)) {
                    is DispositiviAudio.EsitoInstradamento.Applicato -> {
                        aggiornaNotifica("Ultima chiamata instradata su: ${etichettaDispositivo(esito.id)}")
                        return@launch
                    }
                    is DispositiviAudio.EsitoInstradamento.ImpostazioneRifiutata -> {
                        aggiornaNotifica(
                            "Ultima chiamata: Android ha rifiutato di usare ${etichettaDispositivo(esito.id)}"
                        )
                        return@launch
                    }
                    DispositiviAudio.EsitoInstradamento.NessunDispositivoDisponibile -> {
                        if (tentativo == TENTATIVI_INSTRADAMENTO - 1) {
                            aggiornaNotifica("Ultima chiamata: il sistema non riportava alcun dispositivo audio disponibile")
                        } else {
                            delay(INTERVALLO_TENTATIVO_MS)
                        }
                    }
                    is DispositiviAudio.EsitoInstradamento.NessunoInClassificaDisponibile -> {
                        if (tentativo == TENTATIVI_INSTRADAMENTO - 1) {
                            aggiornaNotifica(
                                "Ultima chiamata: nessuno dei dispositivi in classifica era disponibile " +
                                    "(visti: ${esito.dispositiviVisti.joinToString(", ").ifBlank { "nessuno" }})"
                            )
                        } else {
                            delay(INTERVALLO_TENTATIVO_MS)
                        }
                    }
                }
            }
        }
    }

    private fun etichettaDispositivo(id: String): String = when (id) {
        ID_AURICOLARE_TELEFONO -> "Auricolare del telefono"
        ID_VIVAVOCE_TELEFONO -> "Vivavoce del telefono"
        ID_CUFFIE_USB -> "Cuffie USB"
        else -> id
    }

    private fun aggiornaNotifica(testo: String) {
        notificationManager.notify(ID_NOTIFICA, costruisciNotifica(testo))
    }

    private fun creaCanaleNotifica() {
        val canale = NotificationChannel(
            CANALE_NOTIFICA,
            "Instradamento chiamate",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Notifica persistente mentre BTOrder gestisce l'audio delle chiamate"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canale)
    }

    private fun costruisciNotifica(testo: String) =
        NotificationCompat.Builder(this, CANALE_NOTIFICA)
            .setContentTitle("BTOrder - Instradamento chiamate attivo")
            .setContentText(testo)
            .setSmallIcon(R.drawable.ic_notifica)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val CANALE_NOTIFICA = "canale_instradamento_chiamate"
        private const val ID_NOTIFICA = 2
        private const val ID_NOTIFICA_AVVISO = 4

        /** Numero di tentativi ravvicinati subito dopo l'OFFHOOK. */
        private const val TENTATIVI_INSTRADAMENTO = 6

        /** Intervallo tra un tentativo e il successivo. */
        private const val INTERVALLO_TENTATIVO_MS = 500L
    }
}
