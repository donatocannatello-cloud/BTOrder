package it.example.btorder

import android.media.AudioDeviceInfo
import android.media.AudioManager

/** ID fisso della voce "Audio Telefono" (auricolare integrato). */
const val ID_AURICOLARE_TELEFONO = "PHONE_EARPIECE"

/** ID fisso della voce "Vivavoce Telefono" (altoparlante integrato). */
const val ID_VIVAVOCE_TELEFONO = "PHONE_SPEAKER"

/** Tipo di voce mostrata nella lista unificata dei dispositivi. */
enum class TipoVoceDispositivo {
    BLUETOOTH,
    AURICOLARE_TELEFONO,
    VIVAVOCE_TELEFONO
}

/** Applicazione dell'ordine di priorità scelto dall'utente al dispositivo di comunicazione attivo. */
object DispositiviAudio {

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
