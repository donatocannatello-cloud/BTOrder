package it.example.forme.gioco

/**
 * Riconosce il gesto di "presa" dai 21 punti di una mano (numerazione MediaPipe), già convertiti
 * in pixel dello schermo così che le distanze abbiano la stessa scala in orizzontale e in verticale.
 *
 * Una mano afferra quando:
 * - **pizzica**: la punta del pollice tocca la punta dell'indice, oppure
 * - **chiude il pugno**: le punte delle dita tornano vicine al polso quanto le nocche.
 *
 * Le soglie di presa e di rilascio sono diverse (isteresi), così una mano chiusa a metà
 * non fa cadere e riprendere la forma a ogni fotogramma.
 */
object Gesti {
    const val POLSO = 0
    const val PUNTA_POLLICE = 4
    const val PUNTA_INDICE = 8
    const val NOCCA_MEDIO = 9
    const val PUNTI_MANO = 21

    private val PUNTE = intArrayOf(8, 12, 16, 20)
    private val NOCCHE = intArrayOf(5, 9, 13, 17)

    /** Collegamenti fra i punti, per disegnare lo scheletro della mano. */
    val OSSA = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12,
        9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
    )

    // Distanza pollice-indice in rapporto alla grandezza della mano
    const val PIZZICO_PRESA = 0.38f
    const val PIZZICO_RILASCIO = 0.55f
    // Distanza punte-polso in rapporto a nocche-polso: mano aperta ≈ 1,9, pugno ≈ 1
    const val PUGNO_PRESA = 1.25f
    const val PUGNO_RILASCIO = 1.45f

    /** Il punto con cui la mano prende le forme: a metà fra la punta del pollice e quella dell'indice. */
    fun puntoPresa(mano: List<Punto>): Punto = mano[PUNTA_POLLICE].verso(mano[PUNTA_INDICE], 0.5f)

    /** Grandezza della mano sullo schermo: dal polso alla nocca del medio. */
    fun misura(mano: List<Punto>): Float =
        mano[POLSO].distanza(mano[NOCCA_MEDIO]).coerceAtLeast(1e-3f)

    fun pizzico(mano: List<Punto>): Float =
        mano[PUNTA_POLLICE].distanza(mano[PUNTA_INDICE]) / misura(mano)

    fun chiusura(mano: List<Punto>): Float {
        val polso = mano[POLSO]
        return PUNTE.indices.map { i ->
            polso.distanza(mano[PUNTE[i]]) / polso.distanza(mano[NOCCHE[i]]).coerceAtLeast(1e-3f)
        }.average().toFloat()
    }

    /** @param afferravaGia se la mano stava già afferrando (si usano le soglie di rilascio) */
    fun afferra(mano: List<Punto>, afferravaGia: Boolean): Boolean {
        val p = pizzico(mano)
        val c = chiusura(mano)
        return if (afferravaGia) {
            p < PIZZICO_RILASCIO || c < PUGNO_RILASCIO
        } else {
            p < PIZZICO_PRESA || c < PUGNO_PRESA
        }
    }
}
