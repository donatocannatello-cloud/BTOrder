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
    fun unaManoGiaChiusaNonPrendeLeForme() {
        val p = partita()
        val pezzo = p.pezzi[0]
        // La mano arriva già chiusa sulla forma: non c'è il gesto di presa
        p.aggiorna(dt, listOf(mano(0, Punto(0f, 0f), true)))
        p.aggiorna(dt, listOf(mano(0, pezzo.casa, true)))
        p.aggiorna(dt, listOf(mano(0, pezzo.casa + Punto(200f, 0f), true)))
        assertEquals(pezzo.casa, pezzo.posizione)
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
}
