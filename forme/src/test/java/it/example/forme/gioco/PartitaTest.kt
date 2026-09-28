package it.example.forme.gioco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartitaTest {

    private val dt = 1f / 30f

    private fun partita(seme: Long = 7L) = Partita(1080f, 2340f, seme)

    private fun mano(id: Int, p: Punto, afferra: Boolean) = ManoGioco(id, p, presente = true, afferra = afferra)

    /** Porta la mano [id] da [da] ad [a] tenendo la forma, poi la apre. */
    private fun trascina(partita: Partita, id: Int, da: Punto, a: Punto): List<Evento> {
        val eventi = mutableListOf<Evento>()
        eventi += partita.aggiorna(dt, listOf(mano(id, da, false)))
        eventi += partita.aggiorna(dt, listOf(mano(id, da, true)))
        for (k in 1..40) eventi += partita.aggiorna(dt, listOf(mano(id, da.verso(a, k / 40f), true)))
        // Il tempo di far rientrare la forma sotto la mano
        repeat(60) { eventi += partita.aggiorna(dt, listOf(mano(id, a, true))) }
        eventi += partita.aggiorna(dt, listOf(mano(id, a, false)))
        return eventi
    }

    @Test
    fun ilPrimoLivelloHaTreFormeETreIncaviSenzaPesanti() {
        val p = partita()
        assertEquals(3, p.pezzi.size)
        assertEquals(3, p.slot.size)
        assertEquals(p.pezzi.map { it.tipo }.toSet(), p.slot.map { it.tipo }.toSet())
        assertTrue(p.pezzi.none { it.pesante })
    }

    @Test
    fun formeEIncaviNonSiSovrappongonoERestanoNelloSchermo() {
        for (livello in 1..12) {
            val p = partita(livello.toLong())
            p.iniziaLivello(livello)
            val cerchi = p.pezzi.map { it.casa to it.raggio } + p.slot.map { it.centro to it.raggio }
            for ((c, r) in cerchi) {
                assertTrue(c.x - r >= 0f && c.x + r <= p.larghezza)
                assertTrue(c.y - r >= 0f && c.y + r <= p.altezza)
            }
            for (i in cerchi.indices) for (j in i + 1 until cerchi.size) {
                val (a, ra) = cerchi[i]
                val (b, rb) = cerchi[j]
                assertTrue("livello $livello: $a e $b si sovrappongono", a.distanza(b) >= ra + rb)
            }
        }
    }

    @Test
    fun portareUnaFormaNelSuoIncavoLaIncastra() {
        val p = partita()
        val pezzo = p.pezzi[0]
        val eventi = trascina(p, 0, pezzo.casa, p.slotDi(pezzo).centro)
        assertTrue(pezzo.incastrato)
        assertTrue(p.slotDi(pezzo).occupato)
        assertTrue(eventi.any { it is Evento.Incastro })
        assertEquals(Partita.PUNTI_INCASTRO, p.punteggio)
    }

    @Test
    fun unIncavoSbagliatoRimandaLaFormaAlSuoPosto() {
        val p = partita()
        val pezzo = p.pezzi[0]
        val sbagliato = p.slot.first { it.tipo != pezzo.tipo }
        val eventi = trascina(p, 0, pezzo.casa, sbagliato.centro)
        assertFalse(pezzo.incastrato)
        assertTrue(eventi.any { it is Evento.Sbagliato })
        assertEquals(1, p.errori)
        repeat(90) { p.aggiorna(dt, emptyList()) }
        assertEquals(pezzo.casa, pezzo.posizione)
    }

    @Test
    fun unaManoChiusaDaTempoNonPrendeLeForme() {
        val p = partita()
        val pezzo = p.pezzi[0]
        // La mano resta chiusa lontano per un secondo, poi passa sulla forma: non la prende
        p.aggiorna(dt, listOf(mano(0, Punto(0f, 0f), false)))
        repeat(30) { p.aggiorna(dt, listOf(mano(0, Punto(0f, 0f), true))) }
        p.aggiorna(dt, listOf(mano(0, pezzo.casa, true)))
        p.aggiorna(dt, listOf(mano(0, pezzo.casa + Punto(200f, 0f), true)))
        assertEquals(pezzo.casa, pezzo.posizione)
    }

    @Test
    fun leDitaChiuseUnAttimoPrimaDiArrivarePrendonoLaForma() {
        val p = partita()
        val pezzo = p.pezzi[0]
        val lontano = pezzo.casa + Punto(0f, -pezzo.raggio * 3f)
        p.aggiorna(dt, listOf(mano(0, lontano, false)))
        // Il pizzico scatta a qualche fotogramma dalla forma, poi la mano ci arriva sopra
        for (k in 0..6) p.aggiorna(dt, listOf(mano(0, lontano.verso(pezzo.casa, k / 6f), true)))
        val eventi = p.aggiorna(dt, listOf(mano(0, pezzo.casa + Punto(150f, 0f), true)))
        assertTrue(p.tenutoDa(pezzo).isNotEmpty())
        assertTrue(pezzo.posizione != pezzo.casa || eventi.isNotEmpty())
    }

    @Test
    fun laManoRiapertaPrendeUnAltraForma() {
        val p = partita()
        val a = p.pezzi[0]
        val b = p.pezzi[1]
        p.aggiorna(dt, listOf(mano(0, a.casa, false)))
        p.aggiorna(dt, listOf(mano(0, a.casa, true)))
        assertTrue(p.tenutoDa(a).isNotEmpty())
        // Si lascia a dov'è, si riapre la mano, la si porta su b e la si richiude
        p.aggiorna(dt, listOf(mano(0, a.casa, false)))
        p.aggiorna(dt, listOf(mano(0, b.casa, false)))
        p.aggiorna(dt, listOf(mano(0, b.casa, true)))
        assertTrue(p.tenutoDa(b).isNotEmpty())
        assertTrue(p.tenutoDa(a).isEmpty())
    }

    @Test
    fun leFormePesantiSiSollevanoSoloConDueMani() {
        val p = partita()
        p.iniziaLivello(3)
        val pesante = p.pezzi.first { it.pesante }
        val destinazione = p.slotDi(pesante).centro
        val sinistra = Punto(-30f, 0f)
        val destra = Punto(30f, 0f)

        // Con una mano sola la forma non si muove e arriva l'avviso
        p.aggiorna(dt, listOf(mano(0, pesante.casa, false)))
        val eventi = p.aggiorna(dt, listOf(mano(0, pesante.casa, true))) +
            p.aggiorna(dt, listOf(mano(0, pesante.casa + Punto(0f, 100f), true)))
        assertEquals(pesante.casa, pesante.posizione)
        assertTrue(eventi.any { it is Evento.ServonoDueMani })

        // Arriva la seconda mano: ora la forma segue il punto medio delle due
        p.aggiorna(dt, listOf(mano(0, pesante.casa + sinistra, true), mano(1, pesante.casa + destra, false)))
        p.aggiorna(dt, listOf(mano(0, pesante.casa + sinistra, true), mano(1, pesante.casa + destra, true)))
        for (k in 1..40) {
            val c = pesante.casa.verso(destinazione, k / 40f)
            p.aggiorna(dt, listOf(mano(0, c + sinistra, true), mano(1, c + destra, true)))
        }
        repeat(60) { p.aggiorna(dt, listOf(mano(0, destinazione + sinistra, true), mano(1, destinazione + destra, true))) }
        assertTrue(pesante.incastrato)
        assertEquals(Partita.PUNTI_PESANTE, p.punteggio)
    }

    @Test
    fun completatiGliIncastriSiPassaAlLivelloSuccessivo() {
        val p = partita()
        val eventi = mutableListOf<Evento>()
        for (pezzo in p.pezzi.toList()) eventi += trascina(p, 0, pezzo.casa, p.slotDi(pezzo).centro)
        assertEquals(StatoPartita.LIVELLO_COMPLETATO, p.stato)
        assertTrue(eventi.any { it is Evento.LivelloCompletato })

        repeat(120) { eventi += p.aggiorna(dt, emptyList()) }
        assertEquals(2, p.livello)
        assertEquals(4, p.pezzi.size)
        assertTrue(eventi.any { it is Evento.NuovoLivello })
    }

    @Test
    fun alPrimoLivelloLeFormeStannoFermeEIntere() {
        val p = partita()
        assertFalse(p.movimento)
        assertFalse(p.trasformazione)
        val tipi = p.pezzi.map { it.tipo }
        repeat(300) { p.aggiorna(dt, emptyList()) }
        assertEquals(tipi, p.pezzi.map { it.tipo })
        assertTrue(p.pezzi.all { it.posizione == it.casa })
    }

    @Test
    fun alSecondoLivelloLeFormeSiMuovonoSenzaUscireDalCampo() {
        val p = partita()
        p.iniziaLivello(2)
        assertTrue(p.movimento)
        assertFalse(p.trasformazione)
        repeat(600) {
            p.aggiorna(dt, emptyList())
            for (pz in p.pezzi) {
                assertTrue(pz.posizione.x - pz.raggio >= -0.01f && pz.posizione.x + pz.raggio <= p.larghezza + 0.01f)
                assertTrue(pz.posizione.y - pz.raggio >= -0.01f && pz.posizione.y + pz.raggio <= p.altezza + 0.01f)
            }
        }
        assertTrue(p.pezzi.all { it.posizione != it.casa })
    }

    @Test
    fun unaFormaInManoNonScappa() {
        val p = partita()
        p.iniziaLivello(2)
        val pezzo = p.pezzi[0]
        val qui = pezzo.posizione
        p.aggiorna(0f, listOf(mano(0, qui, false)))
        p.aggiorna(0f, listOf(mano(0, qui, true)))
        repeat(60) { p.aggiorna(dt, listOf(mano(0, qui, true))) }
        assertTrue(pezzo.posizione.distanza(qui) < 1f)
    }

    @Test
    fun alTerzoLivelloLeFormeSiTrasformanoRestandoRisolvibili() {
        val p = partita()
        p.iniziaLivello(3)
        assertTrue(p.trasformazione)
        assertFalse(p.movimento)
        val prima = p.pezzi.map { it.tipo }
        val eventi = mutableListOf<Evento>()
        repeat(30 * 10) {
            eventi += p.aggiorna(dt, emptyList())
            // Ogni forma ha sempre un incavo libero del suo tipo e della sua taglia
            for (pz in p.pezzi) {
                val s = p.slot.firstOrNull { it.tipo == pz.tipo && !it.occupato }
                assertTrue(s != null && s.pesante == pz.pesante)
            }
        }
        assertTrue(eventi.any { it is Evento.Cambio })
        assertTrue(prima != p.pezzi.map { it.tipo })
    }

    @Test
    fun dalQuartoLivelloSiMuovonoESiTrasformano() {
        for (n in 4..6) {
            val p = partita()
            p.iniziaLivello(n)
            assertTrue(p.movimento && p.trasformazione)
        }
    }

    @Test
    fun seSiIncastraUnaFormaLeGemelleCambiano() {
        val p = partita()
        p.iniziaLivello(4)
        // Si forzano due forme normali allo stesso tipo, poi se ne incastra una
        val normali = p.pezzi.filter { !it.pesante }
        val a = normali[0]
        val b = normali[1]
        b.tipo = a.tipo
        b.periodoCambio = 0f
        a.periodoCambio = 0f
        trascina(p, 0, a.posizione, p.slotDi(a).centro)
        assertTrue(a.incastrato)
        assertTrue(b.tipo != a.tipo)
    }
}
