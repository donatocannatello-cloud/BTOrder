package it.example.theremin.learn

import it.example.theremin.audio.Note
import it.example.theremin.audio.Scala
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LezioneTest {

    @Test
    fun `lettura della partitura`() {
        assertEquals(
            listOf(Nota(60, 1f), Nota(66, 0.5f), Nota(58, 2f)),
            Brano.leggiPartitura("C4:1 F#4:0.5  Bb3:2")
        )
    }

    @Test
    fun `il repertorio ha una decina di brani, tutti nell'estensione del theremin`() {
        assertTrue(Repertorio.brani.size >= 10)
        for (b in Repertorio.brani) {
            assertTrue(b.titolo, b.note.size >= 10)
            assertTrue(b.titolo, b.estensioneMin >= Note.MIDI_MIN && b.estensioneMax <= Note.MIDI_MAX)
            assertTrue(b.titolo, b.note.all { it.battiti > 0f })
        }
    }

    @Test
    fun `la posizione guida produce esattamente la nota richiesta`() {
        for (b in Repertorio.brani) {
            for (n in b.note) {
                val x = Note.midiToPosizione(n.midi.toFloat(), b.estensioneMin, b.estensioneMax)
                val suonata = Note.posizioneToMidi(x, Scala.CROMATICA, b.estensioneMin, b.estensioneMax)
                assertEquals(b.titolo, n.midi.toFloat(), suonata, 0f)
            }
        }
    }

    @Test
    fun `avanza solo tenendo la nota intonata, anche se ripetuta`() {
        val lezione = Lezione(Brano("Prova", "", 120, "E4:1 E4:1 G4:1"))
        assertFalse(lezione.aggiorna(62f, 1000f)) // stonata
        assertFalse(lezione.aggiorna(null, 1000f)) // mano assente
        assertFalse(lezione.aggiorna(64f, 100f))
        assertTrue(lezione.aggiorna(64.2f, 100f))
        assertEquals(1, lezione.indice)
        // La stessa nota va ritenuta di nuovo per la ripetizione
        assertFalse(lezione.aggiorna(64f, 100f))
        assertTrue(lezione.aggiorna(64f, 100f))
        assertTrue(lezione.aggiorna(67f, 200f))
        assertTrue(lezione.completata)

        lezione.ricomincia()
        assertEquals(0, lezione.indice)
    }
}
