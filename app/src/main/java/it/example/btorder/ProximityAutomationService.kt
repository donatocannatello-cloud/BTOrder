package it.example.btorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servizio in foreground che resta in ascolto delle connessioni/disconnessioni Bluetooth
 * (broadcast ACL di sistema) per tutta la sua esecuzione. Quando un dispositivo marcato come
 * "di fiducia" (es. l'auto) si connette, applica le automazioni scelte dall'utente per quel
 * dispositivo; quando si disconnette, le annulla riportando il telefono allo stato precedente.
 *
 * Nota sul vero "sblocco sicuro": Android non consente a un'app di terze parti di bypassare
 * il PIN/pattern del lucchetto (l'API TrustAgentService richiede un permesso di sistema non
 * concedibile alle app normali). Per quello, l'app rimanda l'utente alle impostazioni native
 * "Smart Lock > Dispositivi affidabili" del telefono, quando disponibili. Qui ci si limita ad
 * automazioni concrete: schermo acceso, timeout esteso, scorciatoia per aprire un'app.
 */
class ProximityAutomationService : Service() {

    private val ambitoCoroutine = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager

    /** Un wake lock per ogni dispositivo di fiducia che ha richiesto "schermo sempre acceso". */
    @Suppress("DEPRECATION")
    private val wakeLockPerDispositivo = mutableMapOf<String, PowerManager.WakeLock>()

    /** Timeout schermo originale (ms) salvato prima di essere esteso, per poterlo ripristinare. */
    private val timeoutOriginalePerDispositivo = mutableMapOf<String, Int>()

    private val ricevitoreConnessioni = DispositiviBluetooth.creaRicevitoreConnessioni { indirizzo, connesso ->
        if (connesso) {
            gestisciConnessione(indirizzo)
            ambitoCoroutine.launch {
                TrustedDeviceStore.registraConnessione(applicationContext, indirizzo, System.currentTimeMillis())
            }
        } else {
            gestisciDisconnessione(indirizzo)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(NotificationManager::class.java)

        creaCanaliNotifica()
        startForeground(ID_NOTIFICA_SERVIZIO, costruisciNotificaServizio())
        ContextCompat.registerReceiver(
            this,
            ricevitoreConnessioni,
            DispositiviBluetooth.filtroEventiConnessione(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(ricevitoreConnessioni)
        wakeLockPerDispositivo.keys.toList().forEach { rilasciaSchermoSempreAcceso(it) }
        timeoutOriginalePerDispositivo.keys.toList().forEach { ripristinaTimeoutSchermo(it) }
        ambitoCoroutine.cancel()
        super.onDestroy()
    }

    private fun gestisciConnessione(indirizzo: String) {
        ambitoCoroutine.launch {
            val dispositivo = TrustedDeviceStore.leggiDispositiviUnaVolta(applicationContext)
                .firstOrNull { it.indirizzo == indirizzo } ?: return@launch

            if (dispositivo.schermoSempreAcceso) {
                attivaSchermoSempreAcceso(dispositivo.indirizzo)
            }
            if (dispositivo.estendiTimeoutSchermo) {
                estendiTimeoutSchermo(dispositivo.indirizzo, dispositivo.timeoutEstesoSecondi)
            }
            if (dispositivo.appDaAvviarePackage != null) {
                mostraNotificaAvvioApp(dispositivo)
            }
        }
    }

    private fun gestisciDisconnessione(indirizzo: String) {
        rilasciaSchermoSempreAcceso(indirizzo)
        ripristinaTimeoutSchermo(indirizzo)
        notificationManager.cancel(idNotificaAutomazione(indirizzo))
    }

    @Suppress("DEPRECATION")
    private fun attivaSchermoSempreAcceso(indirizzo: String) {
        if (wakeLockPerDispositivo.containsKey(indirizzo)) return
        // SCREEN_DIM_WAKE_LOCK è deprecato dall'API 17, ma resta l'unico modo per un Service
        // senza una Activity in primo piano di mantenere accesso lo schermo in risposta a un
        // segnale esterno (la connessione Bluetooth all'auto): non esiste un'alternativa con
        // FLAG_KEEP_SCREEN_ON, che funziona solo all'interno di una finestra visibile.
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "BTOrder:schermoAcceso:$indirizzo"
        )
        wakeLock.acquire(TIMEOUT_MASSIMO_WAKELOCK_MS)
        wakeLockPerDispositivo[indirizzo] = wakeLock
    }

    @Suppress("DEPRECATION")
    private fun rilasciaSchermoSempreAcceso(indirizzo: String) {
        val wakeLock = wakeLockPerDispositivo.remove(indirizzo) ?: return
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun estendiTimeoutSchermo(indirizzo: String, secondi: Int) {
        if (!Settings.System.canWrite(applicationContext)) return
        if (!timeoutOriginalePerDispositivo.containsKey(indirizzo)) {
            val originale = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                TIMEOUT_SCHERMO_DI_DEFAULT_MS
            )
            timeoutOriginalePerDispositivo[indirizzo] = originale
        }
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, secondi * 1000)
    }

    private fun ripristinaTimeoutSchermo(indirizzo: String) {
        val originale = timeoutOriginalePerDispositivo.remove(indirizzo) ?: return
        if (!Settings.System.canWrite(applicationContext)) return
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, originale)
    }

    private fun mostraNotificaAvvioApp(dispositivo: DispositivoFiducia) {
        val pacchetto = dispositivo.appDaAvviarePackage ?: return
        val intentAvvio = packageManager.getLaunchIntentForPackage(pacchetto) ?: return

        val pendingIntent = PendingIntent.getActivity(
            this,
            idNotificaAutomazione(dispositivo.indirizzo),
            intentAvvio,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifica = NotificationCompat.Builder(this, CANALE_AUTOMAZIONI)
            .setContentTitle("${dispositivo.nome} connesso")
            .setContentText("Tocca per aprire ${dispositivo.appDaAvviareNome ?: pacchetto}")
            .setSmallIcon(R.drawable.ic_notifica)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (haPermessoNotifiche()) {
            notificationManager.notify(idNotificaAutomazione(dispositivo.indirizzo), notifica)
        }
    }

    private fun haPermessoNotifiche(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun idNotificaAutomazione(indirizzo: String): Int = indirizzo.hashCode()

    private fun creaCanaliNotifica() {
        val canaleServizio = NotificationChannel(
            CANALE_MONITORAGGIO,
            "Monitoraggio dispositivi",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Notifica persistente mentre BTOrder monitora le connessioni Bluetooth"
        }
        val canaleAutomazioni = NotificationChannel(
            CANALE_AUTOMAZIONI,
            "Automazioni alla connessione",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Scorciatoie mostrate quando un dispositivo di fiducia si connette"
        }
        notificationManager.createNotificationChannel(canaleServizio)
        notificationManager.createNotificationChannel(canaleAutomazioni)
    }

    private fun costruisciNotificaServizio() =
        NotificationCompat.Builder(this, CANALE_MONITORAGGIO)
            .setContentTitle("BTOrder attivo")
            .setContentText("In ascolto per applicare le automazioni ai dispositivi di fiducia")
            .setSmallIcon(R.drawable.ic_notifica)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val CANALE_MONITORAGGIO = "canale_monitoraggio_btorder"
        private const val CANALE_AUTOMAZIONI = "canale_automazioni_btorder"
        private const val ID_NOTIFICA_SERVIZIO = 1

        /** Timeout schermo di sistema tipico usato come fallback se la lettura fallisse. */
        private const val TIMEOUT_SCHERMO_DI_DEFAULT_MS = 30_000

        /** Limite di sicurezza: il wake lock non resta comunque acquisito più di 3 ore. */
        private const val TIMEOUT_MASSIMO_WAKELOCK_MS = 3 * 60 * 60 * 1000L
    }
}
