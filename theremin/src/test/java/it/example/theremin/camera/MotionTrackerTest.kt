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

    /** Fotogramma con sfondo e mano di luminanza scelta, più rumore del sensore casuale. */
    private fun fotogramma(
        sfondo: Int,
        luceMano: Int,
        rumore: Int = 0,
        rnd: java.util.Random = java.util.Random(1),
        mano: ((Int, Int) -> Boolean)? = null,
    ) = ByteArray(w * h) { i ->
        val base = if (mano != null && mano(i % w, i / w)) luceMano else sfondo
        val r = if (rumore > 0) rnd.nextInt(2 * rumore + 1) - rumore else 0
        (base + r).coerceIn(0, 255).toByte()
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

    private val manoCentrale: (Int, Int) -> Boolean = { x, y -> x in 280..360 && y in 150..479 } // mano+braccio dal basso, x ≈ 0.5

    @Test
    fun `su sfondo bianco la mano poco contrastata viene trovata`() {
        // Sfondo quasi bianco (240) e mano chiara (222): differenza di soli 18 livelli, con rumore del sensore
        val rnd = java.util.Random(7)
        val t = MotionTracker()
        repeat(25) { t.elabora(fotogramma(240, 0, rumore = 4, rnd = rnd)) }
        var p = PosizioneMano(0f, 0f, 0f)
        repeat(10) { p = t.elabora(fotogramma(240, 222, rumore = 4, rnd = rnd, mano = manoCentrale)) }
        assertTrue("presenza ${p.presenza}", p.presenza > 0.9f)
        assertEquals(0.5f, p.x, 0.04f)
    }

    @Test
    fun `se la fotocamera cambia esposizione quando entra la mano, la mano resta dove e`() {
        // Entrando la mano scura, l'esposizione automatica schiarisce tutta la scena di 35 livelli
        val t = MotionTracker()
        repeat(25) { t.elabora(fotogramma(200, 0)) }
        var p = PosizioneMano(0f, 0f, 0f)
        val manoASinistra: (Int, Int) -> Boolean = { x, y -> x in 120..200 && y in 150..479 } // x ≈ 0.25
        repeat(10) { p = t.elabora(fotogramma(235, 90, mano = manoASinistra)) }
        assertTrue(p.presenza > 0.9f)
        assertEquals(0.25f, p.x, 0.04f)
    }

    @Test
    fun `un cambio di luce su tutta la scena, senza mano, non viene preso per una mano`() {
        val t = MotionTracker()
        repeat(25) { t.elabora(fotogramma(150, 0)) }
        var p = PosizioneMano(0f, 0f, 0f)
        repeat(10) { p = t.elabora(fotogramma(190, 0)) }
        assertTrue("presenza ${p.presenza}", p.presenza < 0.05f)
    }

    @Test
    fun `il rumore del sensore da solo non viene preso per una mano`() {
        val rnd = java.util.Random(3)
        val t = MotionTracker().apply { sensibilita = 1f }
        var p = PosizioneMano(0f, 0f, 0f)
        repeat(60) { p = t.elabora(fotogramma(128, 0, rumore = 25, rnd = rnd)) }
        assertTrue("presenza ${p.presenza}", p.presenza < 0.05f)
    }

    @Test
    fun `a mano ferma la posizione non trema`() {
        val t = MotionTracker()
        repeat(25) { t.elabora(fotogramma(180, 0)) }
        val rnd = java.util.Random(5)
        val xs = mutableListOf<Float>()
        repeat(30) {
            // La sagoma "vibra" di qualche pixel come una mano vera tenuta ferma
            val d = rnd.nextInt(9) - 4
            xs += t.elabora(fotogramma(180, 40, mano = { x, y -> x in 280 + d..360 + d && y in 150..479 })).x
        }
        val ultimi = xs.takeLast(15)
        assertTrue("oscillazione ${ultimi.max() - ultimi.min()}", ultimi.max() - ultimi.min() < 0.01f)
    }

    @Test
    fun `quando la mano esce la presenza va a zero subito, anche dopo averla tenuta ferma a lungo`() {
        val t = MotionTracker()
        val sfondo = fotogramma(180, 0)
        val mano = fotogramma(180, 60, mano = manoCentrale)
        repeat(25) { t.elabora(sfondo) }
        repeat(200) { t.elabora(mano) } // circa 7 secondi di mano ferma
        assertTrue(t.elabora(mano).presenza > 0.9f)
        // La mano esce: entro 2 fotogrammi (circa 70 ms) la presenza deve essere zero, senza "fantasmi"
        t.elabora(sfondo)
        val p = t.elabora(sfondo)
        assertEquals(0f, p.presenza, 0f)
        repeat(30) { assertEquals(0f, t.elabora(sfondo).presenza, 0f) }
    }

    @Test
    fun `un piccolo movimento lontano dalla mano non la sposta e da solo non fa suonare`() {
        val t = MotionTracker().apply { punto = PuntoMano.CENTRO }
        repeat(25) { t.elabora(fotogramma(180, 0)) }
        // Solo un piccolo oggetto che si muove in un angolo (es. un riflesso): niente mano
        var p = PosizioneMano(0f, 0f, 0f)
        repeat(6) { k -> p = t.elabora(fotogramma(180, 60, mano = { x, y -> x in 20 + k * 3..45 + k * 3 && y in 20..40 })) }
        assertEquals(0f, p.presenza, 0f)
        // Mano al centro più il piccolo oggetto: si segue la mano
        repeat(6) { k ->
            p = t.elabora(fotogramma(180, 60, mano = { x, y -> manoCentrale(x, y) || (x in 20 + k * 3..45 + k * 3 && y in 20..40) }))
        }
        assertTrue(p.presenza > 0.9f)
        assertEquals(0.5f, p.x, 0.03f)
    }
}
