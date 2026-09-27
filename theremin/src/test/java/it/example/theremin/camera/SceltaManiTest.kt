package it.example.theremin.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceltaManiTest {

    /** Mano finta: tutti i 21 punti attorno al palmo in (cx, cy), punta dell'indice in (px, py). */
    private fun mano(cx: Float, cy: Float, px: Float, py: Float) =
        List(21) { k -> if (k == SceltaMani.PUNTA_INDICE) PuntoMano2D(px, py) else PuntoMano2D(cx, cy) }

    @Test
    fun `nessuna mano, nessun comando`() {
        assertNull(SceltaMani.comandi(emptyList()))
    }

    @Test
    fun `con una mano la punta dell'indice decide nota e volume`() {
        val (x, y) = SceltaMani.comandi(listOf(mano(0.5f, 0.6f, 0.45f, 0.3f)))!!
        assertEquals(0.45f, x, 1e-6f)
        assertEquals(0.3f, y, 1e-6f)
    }

    @Test
    fun `con due mani la destra suona la nota e la sinistra regola il volume, in qualunque ordine`() {
        val sinistra = mano(0.2f, 0.8f, 0.25f, 0.7f)
        val destra = mano(0.75f, 0.5f, 0.7f, 0.35f)
        for (ordine in listOf(listOf(sinistra, destra), listOf(destra, sinistra))) {
            val (x, y) = SceltaMani.comandi(ordine)!!
            assertEquals(0.7f, x, 1e-6f) // punta dell'indice della mano destra
            assertEquals(0.8f, y, 1e-6f) // palmo della mano sinistra
        }
    }
}
