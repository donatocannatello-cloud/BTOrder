package it.example.btorder

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Un dispositivo Bluetooth accoppiato, con lo stato di connessione noto in questo momento. */
data class DispositivoBluetooth(
    val indirizzo: String,
    val nome: String,
    val connesso: Boolean
)

/**
 * Enumerazione dei dispositivi Bluetooth accoppiati (non solo audio: qui l'obiettivo è
 * gestire tutte le periferiche note, l'auto compresa) e osservazione in tempo reale delle
 * connessioni/disconnessioni tramite i broadcast ACL di sistema.
 */
object DispositiviBluetooth {

    fun haPermessoBluetooth(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Tutti i dispositivi accoppiati (bonded), indipendentemente dal tipo di profilo che
     * espongono: auto, cuffie, ma anche tastiere o altre periferiche.
     */
    @Suppress("MissingPermission")
    fun elencaDispositiviAccoppiati(
        context: Context,
        indirizziConnessi: Set<String>
    ): List<DispositivoBluetooth> {
        if (!haPermessoBluetooth(context)) return emptyList()

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return adapter.bondedDevices
            .map { dispositivo ->
                DispositivoBluetooth(
                    indirizzo = dispositivo.address,
                    nome = dispositivo.name ?: dispositivo.address,
                    connesso = dispositivo.address in indirizziConnessi
                )
            }
            .sortedBy { it.nome.lowercase() }
    }

    /** Filtro per i broadcast di sistema emessi alla connessione/disconnessione ACL. */
    fun filtroEventiConnessione(): IntentFilter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
    }

    /**
     * Crea un BroadcastReceiver che invoca [alCambiamento] con l'indirizzo del dispositivo e
     * `true`/`false` a seconda che si sia appena connesso o disconnesso. Va registrato con
     * [filtroEventiConnessione] e deregistrato quando non serve più.
     */
    @Suppress("DEPRECATION")
    fun creaRicevitoreConnessioni(
        alCambiamento: (indirizzo: String, connesso: Boolean) -> Unit
    ): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dispositivo = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                ?: return
            val connesso = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            alCambiamento(dispositivo.address, connesso)
        }
    }
}
