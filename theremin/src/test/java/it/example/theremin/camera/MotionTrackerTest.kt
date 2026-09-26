package it.example.theremin.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTrackerTest {

    private val w = 640
    private val h = 480

    private fun fotogramma(mano: ((Int, Int) -> Boolean)? = null) = ByteArray(w * h) { i ->
        if (mano != null && mano(i % w, i / w)) 30 else 180.toByte()
    }

    private fun MotionTracker.elabora(f: ByteArray, rot: Int = 0, specchia: Boolean = false) =
        elabora(f, w, h, w, 1, rot, specchia)

    @Test
    fun `nessuna presenza sulla scena statica`() {
        val t = MotionTracker()
        val sfondo = fotogramma()
        repeat(30) { t.elabora(sfondo) }
        assertTrue(t.elabora(sfondo).presenza < 0.01f)
    }

    private fun MotionTracker.segui(sfondo: ByteArray, mano: ByteArray, specchia: Boolean = false): PosizioneMano {
        repeat(20) { elabora(sfondo, specchia = specchia) }
        var p = elabora(mano, specchia = specchia)
        repeat(10) { p = elabora(mano, specchia = specchia) }
        return p
    }

    @Test
    fun `al centro segue il baricentro della mano`() {
        val t = MotionTracker().apply { punto = PuntoMano.CENTRO }
        val mano = fotogramma { x, y -> x in 448..575 && y in 48..143 } // centro (0.8, 0.2)
        val p = t.segui(fotogramma(), mano)
        assertTrue(p.presenza > 0.9f)
        assertEquals(0.8f, p.x, 0.03f)
        assertEquals(0.2f, p.y, 0.03f)
    }

    @Test
    fun `in modalita punta segue la cima della sagoma, anche specchiata`() {
        val mano = fotogramma { x, y -> x in 448..575 && y in 48..143 }
        val p = MotionTracker().segui(fotogramma(), mano)
        assertEquals(0.8f, p.x, 0.03f)
        assertTrue("y ${p.y}", p.y in 0.1f..0.2f)

        val specchiata = MotionTracker().segui(fotogramma(), mano, specchia = true)
        assertEquals(0.2f, specchiata.x, 0.03f)
    }

    @Test
    fun `il braccio non trascina la punta verso il centro`() {
        // Mano in alto a sinistra con il braccio che scende in diagonale fino al centro in basso
        val conBraccio = fotogramma { x, y ->
            (x in 0..90 && y in 60..160) || (y in 160..479 && x in (y - 160) * 280 / 320 until (y - 160) * 280 / 320 + 70)
        }
        val punta = MotionTracker().segui(fotogramma(), conBraccio)
        val centro = MotionTracker().apply { punto = PuntoMano.CENTRO }.segui(fotogramma(), conBraccio)
        assertTrue("punta ${punta.x}", punta.x < 0.1f)
        assertTrue("centro ${centro.x}", centro.x > 0.2f)
    }

    @Test
    fun `rotazioni orarie del fotogramma`() {
        assertEquals(Pair(1f, 0f), MotionTracker.ruota(0f, 0f, 90))
        assertEquals(Pair(0f, 1f), MotionTracker.ruota(0f, 0f, 270))
        assertEquals(Pair(1f, 1f), MotionTracker.ruota(0f, 0f, 180))
    }
}
