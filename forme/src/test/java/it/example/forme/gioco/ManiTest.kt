package it.example.forme.gioco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManiTest {

    /**
     * Mano stilizzata con il palmo verso la fotocamera e il polso in basso, centrata in [c].
     * [chiusa]: le punte delle dita tornano sul palmo (pugno).
     */
    private fun mano(c: Punto, chiusa: Boolean = false, pizzico: Boolean = false): List<Punto> {
        val punti = MutableList(21) { c }
        punti[0] = c + Punto(0f, 100f)
        // Nocche (5, 9, 13, 17) a 100 px sopra il polso
        val xNocche = floatArrayOf(-45f, -15f, 15f, 45f)
        for (d in 0 until 4) {
            val base = 5 + d * 4
            val xn = xNocche[d]
            punti[base] = c + Punto(xn, 0f)
            val lunghezza = if (chiusa) 0f else 90f
            punti[base + 1] = c + Punto(xn, -lunghezza / 3f)
            punti[base + 2] = c + Punto(xn, -2f * lunghezza / 3f)
            punti[base + 3] = if (chiusa) c + Punto(xn, 30f) else c + Punto(xn, -lunghezza)
        }
        // Pollice verso l'esterno, oppure sulla punta dell'indice
        punti[1] = c + Punto(-40f, 80f)
        punti[2] = c + Punto(-70f, 60f)
        punti[3] = c + Punto(-90f, 40f)
        punti[4] = if (pizzico) punti[8] + Punto(5f, 0f) else c + Punto(-110f, 20f)
        return punti
    }

    @Test
    fun manoApertaNonAfferra() {
        assertFalse(Gesti.afferra(mano(Punto(500f, 500f)), afferravaGia = false))
    }

    @Test
    fun pugnoEPizzicoAfferrano() {
        assertTrue(Gesti.afferra(mano(Punto(500f, 500f), chiusa = true), afferravaGia = false))
        assertTrue(Gesti.afferra(mano(Punto(500f, 500f), pizzico = true), afferravaGia = false))
    }

    @Test
    fun laMappaturaTagliaILatiComeLAnteprima() {
        // Schermo 1080x2340, fotogramma 9:16: l'immagine è alta 2340 e larga 1316, 118 px tagliati per lato
        val m = MappaturaSchermo(1080f, 2340f, 9f / 16f)
        val centro = m.inSchermo(Punto(0.5f, 0.5f))
        assertEquals(540f, centro.x, 0.5f)
        assertEquals(1170f, centro.y, 0.5f)
        val angolo = m.inSchermo(Punto(0f, 0f))
        assertEquals(-118.1f, angolo.x, 0.5f)
        assertEquals(0f, angolo.y, 0.5f)
    }

    @Test
    fun ogniManoRestaAlSuoPostoAncheSeMediaPipeLeScambia() {
        val inseguitore = InseguitoreMani()
        val a = Punto(300f, 1000f)
        val b = Punto(800f, 1000f)
        inseguitore.aggiorna(listOf(mano(a), mano(b)), 1f / 30f)
        val idA = inseguitore.posti.minBy { it.posizione.distanza(a) }.id
        repeat(5) { inseguitore.aggiorna(listOf(mano(b), mano(a)), 1f / 30f) }
        val postoA = inseguitore.posti.first { it.id == idA }
        assertTrue(postoA.posizione.distanza(Gesti.puntoPresa(mano(a))) < 5f)
    }

    @Test
    fun laPresaSopravviveAUnFotogrammaPersoESiRilasciaConLaManoAperta() {
        val inseguitore = InseguitoreMani()
        val c = Punto(500f, 1000f)
        inseguitore.aggiorna(listOf(mano(c, chiusa = true)), 1f / 30f)
        assertTrue(inseguitore.mani().any { it.afferra })

        // Un fotogramma senza mani: la forma resta in mano
        inseguitore.aggiorna(emptyList(), 1f / 30f)
        assertTrue(inseguitore.mani().any { it.afferra })

        // Mano aperta per un fotogramma solo: ancora presa; per due: rilasciata
        inseguitore.aggiorna(listOf(mano(c)), 1f / 30f)
        assertTrue(inseguitore.mani().any { it.afferra })
        inseguitore.aggiorna(listOf(mano(c)), 1f / 30f)
        assertFalse(inseguitore.mani().any { it.afferra })

        // Mano sparita a lungo: non è più presente
        repeat(20) { inseguitore.aggiorna(emptyList(), 1f / 30f) }
        assertTrue(inseguitore.mani().none { it.presente })
    }
}
