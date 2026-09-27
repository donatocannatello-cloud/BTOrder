package it.example.theremin.camera

/**
 * Segue nel tempo il punto che comanda una voce: filtro One Euro sulla posizione e presenza
 * che sale in fretta e va a zero dopo 2 fotogrammi senza mano (indipendente da Android).
 */
class InseguitoreVoce {
    private val filtroX = FiltroOneEuro()
    private val filtroY = FiltroOneEuro()
    private var presenza = 0f
    private var fotogrammiSenzaMano = 0

    var x = 0.5f
        private set
    var y = 0.5f
        private set

    /** @param punto (x, y) della voce in questo fotogramma, o null se nessuna mano la comanda */
    fun aggiorna(punto: Pair<Float, Float>?, dt: Float): PosizioneMano {
        if (punto != null) {
            val (px, py) = punto
            if (presenza < 0.3f) {
                // Mano appena comparsa: si parte da dove si trova
                filtroX.reimposta(px)
                filtroY.reimposta(py)
            }
            x = filtroX.filtra(px.coerceIn(0f, 1f), dt)
            y = filtroY.filtra(py.coerceIn(0f, 1f), dt)
            fotogrammiSenzaMano = 0
            presenza = minOf(1f, presenza + 0.5f)
        } else {
            fotogrammiSenzaMano++
            if (fotogrammiSenzaMano >= 2) presenza = 0f
        }
        return PosizioneMano(x, y, presenza)
    }
}
