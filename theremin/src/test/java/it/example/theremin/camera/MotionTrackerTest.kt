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

    @Test
    fun `segue una mano in alto a destra`() {
        val t = MotionTracker()
        val sfondo = fotogramma()
        repeat(20) { t.elabora(sfondo) }
        val mano = fotogramma { x, y -> x in 448..575 && y in 48..143 } // centro (0.8, 0.2)
        var p = t.elabora(mano)
        repeat(10) { p = t.elabora(mano) }
        assertTrue(p.presenza > 0.9f)
        assertEquals(0.8f, p.x, 0.03f)
        assertEquals(0.2f, p.y, 0.03f)

        // Specchiata: la stessa mano appare a sinistra
        val t2 = MotionTracker()
        repeat(20) { t2.elabora(sfondo, specchia = true) }
        repeat(10) { p = t2.elabora(mano, specchia = true) }
        assertEquals(0.2f, p.x, 0.03f)
    }

    @Test
    fun `rotazioni orarie del fotogramma`() {
        assertEquals(Pair(1f, 0f), MotionTracker.ruota(0f, 0f, 90))
        assertEquals(Pair(0f, 1f), MotionTracker.ruota(0f, 0f, 270))
        assertEquals(Pair(1f, 1f), MotionTracker.ruota(0f, 0f, 180))
    }
}
