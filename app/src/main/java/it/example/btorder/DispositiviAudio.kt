package it.example.btorder

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** ID fisso della voce "Audio Telefono" (auricolare integrato). */
const val ID_AURICOLARE_TELEFONO = "PHONE_EARPIECE"

/** ID fisso della voce "Vivavoce Telefono" (altoparlante integrato). */
const val ID_VIVAVOCE_TELEFONO = "PHONE_SPEAKER"

/** ID fisso della voce "Cuffie USB" (cuffie collegate via cavo USB/micro-USB). */
const val ID_CUFFIE_USB = "WIRED_USB_HEADSET"

/** Tipo di voce mostrata nella lista unificata dei dispositivi. */
enum class TipoVoceDispositivo {
    BLUETOOTH,
    AURICOLARE_TELEFONO,
    VIVAVOCE_TELEFONO,
    CUFFIE_USB
}

/** Applicazione dell'ordine di priorità scelto dall'utente al dispositivo di comunicazione attivo. */
object DispositiviAudio {

    /**
     * true se al momento risulta collegata una cuffia via USB (es. tramite adattatore
     * micro-USB): a differenza del Bluetooth non esiste un concetto di "accoppiamento"
     * persistente, quindi qui si può solo rilevare la presenza fisica attuale.
     */
    fun cuffieUsbConnesse(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
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

    /** Ricava l'ID stabile (MAC per il Bluetooth, costante fissa per l'hardware integrato/USB). */
    private fun AudioDeviceInfo.idStabile(): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> ID_AURICOLARE_TELEFONO
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> ID_VIVAVOCE_TELEFONO
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> ID_CUFFIE_USB
        else -> address
    }
}
