package it.example.btorder

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.ContextCompat

/** ID fisso della voce "Audio Telefono" (auricolare integrato). */
const val ID_AURICOLARE_TELEFONO = "PHONE_EARPIECE"

/** ID fisso della voce "Vivavoce Telefono" (altoparlante integrato). */
const val ID_VIVAVOCE_TELEFONO = "PHONE_SPEAKER"

/** Tipo di voce mostrata nella lista ordinabile. */
enum class TipoVoceDispositivo {
    BLUETOOTH,
    AURICOLARE_TELEFONO,
    VIVAVOCE_TELEFONO
}

/**
 * Una voce della lista ordinabile: un dispositivo audio identificato da un
 * ID stabile (indirizzo MAC per il Bluetooth, costante fissa per le voci
 * integrate del telefono) così l'ordine sopravvive anche se il dispositivo
 * non è momentaneamente connesso o l'app viene riavviata.
 */
data class VoceDispositivoAudio(
    val id: String,
    val nome: String,
    val tipo: TipoVoceDispositivo
)

/**
 * Enumerazione dei dispositivi audio disponibili e applicazione dell'ordine
 * di priorità scelto dall'utente al dispositivo di comunicazione attivo.
 */
object DispositiviAudio {

    private fun haPermessoBluetooth(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Dispositivi Bluetooth associati (bonded) che espongono un profilo
     * audio (cuffie, auricolari, vivavoce auto, ecc.). Vengono considerati i
     * dispositivi accoppiati e non solo quelli connessi in questo momento,
     * così l'utente può definirne la priorità anche quando non sono al
     * momento raggiungibili.
     */
    @Suppress("MissingPermission")
    fun elencaDispositiviBluetoothAudio(context: Context): List<VoceDispositivoAudio> {
        if (!haPermessoBluetooth(context)) return emptyList()

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = bluetoothManager?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return adapter.bondedDevices
            .filter { dispositivo ->
                dispositivo.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
            }
            .map { dispositivo ->
                VoceDispositivoAudio(
                    id = dispositivo.address,
                    // L'alias (rinominato dall'utente in Impostazioni > Bluetooth) ha la
                    // precedenza sul nome trasmesso dal dispositivo stesso.
                    nome = dispositivo.alias ?: dispositivo.name ?: dispositivo.address,
                    tipo = TipoVoceDispositivo.BLUETOOTH
                )
            }
    }

    /** Le due voci fisse sempre presenti in lista, indipendentemente dal Bluetooth. */
    fun vociFisseTelefono(): List<VoceDispositivoAudio> = listOf(
        VoceDispositivoAudio(ID_AURICOLARE_TELEFONO, "Audio Telefono", TipoVoceDispositivo.AURICOLARE_TELEFONO),
        VoceDispositivoAudio(ID_VIVAVOCE_TELEFONO, "Vivavoce Telefono", TipoVoceDispositivo.VIVAVOCE_TELEFONO)
    )

    /**
     * Ricostruisce la lista completa (Bluetooth + voci fisse) rispettando
     * l'ordine già salvato: le voci con un ID presente in [ordineSalvato]
     * mantengono la posizione relativa già scelta dall'utente, mentre i
     * dispositivi mai visti prima vengono aggiunti in coda. In questo modo
     * una nuova scansione non fa perdere l'ordinamento impostato in precedenza.
     */
    fun costruisciListaOrdinata(
        context: Context,
        ordineSalvato: List<String>
    ): List<VoceDispositivoAudio> {
        val tutteLeVoci = elencaDispositiviBluetoothAudio(context) + vociFisseTelefono()
        val mappaVoci = tutteLeVoci.associateBy { it.id }

        val vociInOrdineSalvato = ordineSalvato.mapNotNull { mappaVoci[it] }
        val vociNuove = tutteLeVoci.filter { it.id !in ordineSalvato }

        return vociInOrdineSalvato + vociNuove
    }

    /**
     * Cerca, tra i dispositivi di comunicazione EFFETTIVAMENTE disponibili in
     * questo momento ([AudioManager.getAvailableCommunicationDevices]), il
     * primo che compare in [ordinePriorita] e lo imposta come dispositivo di
     * comunicazione attivo per la chiamata in corso.
     *
     * @return true se un dispositivo è stato individuato e impostato con successo.
     */
    fun applicaPrimoDispositivoDisponibile(
        audioManager: AudioManager,
        ordinePriorita: List<String>
    ): Boolean {
        val disponibili = audioManager.availableCommunicationDevices
        if (disponibili.isEmpty()) return false

        for (id in ordinePriorita) {
            val dispositivoTrovato = disponibili.firstOrNull { it.idStabile() == id }
            if (dispositivoTrovato != null) {
                return audioManager.setCommunicationDevice(dispositivoTrovato)
            }
        }
        return false
    }

    /** Ricava l'ID stabile (MAC per il Bluetooth, costante fissa per l'hardware integrato). */
    private fun AudioDeviceInfo.idStabile(): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> ID_AURICOLARE_TELEFONO
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> ID_VIVAVOCE_TELEFONO
        else -> address
    }
}
