package it.example.btorder

/**
 * Una voce dell'unica lista di dispositivi mostrata nell'app: un dispositivo Bluetooth
 * accoppiato oppure una delle due voci fisse del telefono (auricolare/vivavoce integrati).
 * La stessa lista serve sia per l'ordine di priorità usato in chiamata, sia per le
 * automazioni di prossimità (che riguardano solo i dispositivi Bluetooth reali).
 */
data class VoceDispositivo(
    val id: String,
    val nome: String,
    val tipo: TipoVoceDispositivo,
    val connesso: Boolean = false,
    val fiducia: DispositivoFiducia? = null
)

object VociDispositivi {

    /** Le due voci fisse sempre presenti in lista, indipendentemente dal Bluetooth. */
    fun vociFisseTelefono(): List<VoceDispositivo> = listOf(
        VoceDispositivo(ID_AURICOLARE_TELEFONO, "Audio Telefono", TipoVoceDispositivo.AURICOLARE_TELEFONO),
        VoceDispositivo(ID_VIVAVOCE_TELEFONO, "Vivavoce Telefono", TipoVoceDispositivo.VIVAVOCE_TELEFONO)
    )

    /**
     * Unisce tutti i dispositivi Bluetooth accoppiati (con l'eventuale scheda di fiducia già
     * associata) e le due voci fisse del telefono in un'unica lista, rispettando l'ordine di
     * priorità già salvato: le voci con un id presente in [ordineSalvato] mantengono la
     * posizione relativa già scelta dall'utente, quelle mai viste prima vengono aggiunte in
     * coda. In questo modo una nuova scansione Bluetooth non fa perdere l'ordinamento.
     */
    fun costruisci(
        bluetooth: List<DispositivoBluetooth>,
        fiducia: List<DispositivoFiducia>,
        ordineSalvato: List<String>
    ): List<VoceDispositivo> {
        val fiduciaPerIndirizzo = fiducia.associateBy { it.indirizzo }
        val tutte = bluetooth.map { dispositivo ->
            VoceDispositivo(
                id = dispositivo.indirizzo,
                nome = dispositivo.nome,
                tipo = TipoVoceDispositivo.BLUETOOTH,
                connesso = dispositivo.connesso,
                fiducia = fiduciaPerIndirizzo[dispositivo.indirizzo]
            )
        } + vociFisseTelefono()

        val mappa = tutte.associateBy { it.id }
        val vociInOrdineSalvato = ordineSalvato.mapNotNull { mappa[it] }
        val vociNuove = tutte.filter { it.id !in ordineSalvato }

        return vociInOrdineSalvato + vociNuove
    }
}
