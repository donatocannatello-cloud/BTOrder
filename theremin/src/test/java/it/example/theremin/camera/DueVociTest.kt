package it.example.theremin.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DueVociTest {

    private fun mano(px: Float, py: Float) =
        List(21) { k -> if (k == SceltaMani.PUNTA_INDICE) PuntoMano2D(px, py) else PuntoMano2D(px, py + 0.2f) }

    @Test
    fun `con due mani la sinistra va alla prima voce e la destra alla seconda, in qualunque ordine`() {
        val sinistra = mano(0.25f, 0.4f)
        val destra = mano(0.7f, 0.3f)
        for (ordine in listOf(listOf(sinistra, destra), listOf(destra, sinistra))) {
            val v = SceltaMani.voci(ordine, floatArrayOf(0.5f, 0.5f))
            assertEquals(0.25f, v[0]!!.x, 1e-6f)
            assertEquals(0.7f, v[1]!!.x, 1e-6f)
        }
    }

    @Test
    fun `con una sola mano resta alla voce piu vicina, senza scambi`() {
        // La voce 1 era a destra (0.8): una mano a 0.65 continua a essere la voce 1
        val v = SceltaMani.voci(listOf(mano(0.65f, 0.5f)), floatArrayOf(0.2f, 0.8f))
        assertNull(v[0])
        assertEquals(0.65f, v[1]!!.x, 1e-6f)
    }

    @Test
    fun `ogni voce si spegne subito quando la sua mano esce`() {
        val voce = InseguitoreVoce()
        repeat(3) { voce.aggiorna(0.4f to 0.3f, 1f / 30f) }
        assertEquals(1f, voce.aggiorna(0.4f to 0.3f, 1f / 30f).presenza, 0f)
        voce.aggiorna(null, 1f / 30f)
        assertEquals(0f, voce.aggiorna(null, 1f / 30f).presenza, 0f)
    }
}
