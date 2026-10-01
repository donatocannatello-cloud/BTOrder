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
     * Esito di un tentativo di instradamento, abbastanza dettagliato da poter essere mostrato
     * all'utente (nella notifica del Service) per capire perché l'audio non è finito dove ci si
     * aspettava, senza dover leggere i log del dispositivo.
     */
    sealed class EsitoInstradamento {
        /**
         * Il dispositivo [id] è stato trovato disponibile ed è stato impostato con successo.
         * [dispositiviVisti] elenca comunque tutto ciò che il sistema riportava in quel momento:
         * se [id] non è quello atteso (es. il vivavoce invece del Bluetooth), permette di
         * verificare se il dispositivo Bluetooth desiderato mancava del tutto dall'elenco.
         */
        data class Applicato(val id: String, val dispositiviVisti: List<String>) : EsitoInstradamento()

        /** Il sistema non riporta ALCUN dispositivo di comunicazione disponibile in questo momento. */
        object NessunDispositivoDisponibile : EsitoInstradamento()

        /**
         * Nessuno dei dispositivi in classifica risulta tra quelli disponibili ora.
         * [dispositiviVisti] elenca cosa riportava effettivamente il sistema in quel momento
         * (tipo e ID), utile per capire se il problema è un ID che non combacia con quello
         * salvato in classifica, senza dover leggere i log del dispositivo.
         */
        data class NessunoInClassificaDisponibile(val dispositiviVisti: List<String>) : EsitoInstradamento()

        /** Il dispositivo [id] era disponibile ma Android ha rifiutato di impostarlo. */
        data class ImpostazioneRifiutata(val id: String) : EsitoInstradamento()
    }

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
     * Il Bluetooth è il caso delicato: l'indirizzo che il sistema riporta qui per il canale
     * vivavoce può non coincidere con quello di accoppiamento salvato in classifica (osservato
     * su un telefono reale). Due risoluzioni alternative, in ordine di affidabilità:
     * [indirizzoHfpConnesso] (il dispositivo che lo stack Bluetooth riporta come effettivamente
     * connesso via HFP in questo momento — affidabile perché durante una chiamata ce n'è al più
     * uno) e, se non disponibile, [mappaNomeIndirizzo] (nome/alias → indirizzo, da
     * [DispositiviBluetooth.mappaNomePerIndirizzo]).
     */
    fun applicaPrimoDispositivoDisponibile(
        audioManager: AudioManager,
        ordinePriorita: List<String>,
        mappaNomeIndirizzo: Map<String, String> = emptyMap(),
        indirizzoHfpConnesso: String? = null
    ): EsitoInstradamento {
        val disponibili = audioManager.availableCommunicationDevices
        if (disponibili.isEmpty()) return EsitoInstradamento.NessunDispositivoDisponibile

        val dispositiviVisti = disponibili.map {
            "${it.tipoLeggibile()}:${it.idStabile(mappaNomeIndirizzo, indirizzoHfpConnesso)}" +
                (it.productName?.toString()?.let { nome -> " (\"$nome\")" } ?: "")
        }
        val ordinePrioritaNormalizzato = ordinePriorita.map { it.uppercase() }
        for (id in ordinePrioritaNormalizzato) {
            val dispositivoTrovato =
                disponibili.firstOrNull { it.idStabile(mappaNomeIndirizzo, indirizzoHfpConnesso) == id }
            if (dispositivoTrovato != null) {
                return if (audioManager.setCommunicationDevice(dispositivoTrovato)) {
                    EsitoInstradamento.Applicato(id, dispositiviVisti)
                } else {
                    EsitoInstradamento.ImpostazioneRifiutata(id)
                }
            }
        }
        return EsitoInstradamento.NessunoInClassificaDisponibile(dispositiviVisti)
    }

    /**
     * Ricava l'ID stabile (MAC per il Bluetooth, costante fissa per l'hardware integrato/USB).
     * Per il Bluetooth, vedi la nota su [applicaPrimoDispositivoDisponibile] per l'ordine di
     * risoluzione; se nessuna delle due alternative è nota, usa comunque il MAC riportato qui,
     * normalizzato in maiuscolo per non far fallire il confronto per un semplice problema di case.
     */
    private fun AudioDeviceInfo.idStabile(
        mappaNomeIndirizzo: Map<String, String>,
        indirizzoHfpConnesso: String?
    ): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> ID_AURICOLARE_TELEFONO
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> ID_VIVAVOCE_TELEFONO
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> ID_CUFFIE_USB
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ->
            indirizzoHfpConnesso?.uppercase()
                ?: mappaNomeIndirizzo[productName?.toString()]?.uppercase()
                ?: address.uppercase()
        else -> address.uppercase()
    }

    private fun AudioDeviceInfo.tipoLeggibile(): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "auricolare"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "vivavoce"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        else -> "tipo$type"
    }
}
