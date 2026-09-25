package it.example.theremin.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleTest {

    @Test
    fun `La4 corrisponde a 440 Hz e viceversa`() {
        assertEquals(440f, Note.midiToHz(69f), 0.01f)
        assertEquals(69f, Note.hzToMidi(440f), 0.001f)
        assertEquals("La4", Note.nome(69f))
        assertEquals("Do4", Note.nome(60f))
    }

    @Test
    fun `la scala continua non quantizza`() {
        assertEquals(61.37f, Scala.CONTINUA.quantizza(61.37f), 0f)
    }

    @Test
    fun `la pentatonica aggancia al grado piu vicino, anche verso l'ottava successiva`() {
        assertEquals(62f, Scala.PENTATONICA.quantizza(62.9f), 0f) // Re
        assertEquals(64f, Scala.PENTATONICA.quantizza(63.2f), 0f) // Mi
        assertEquals(72f, Scala.PENTATONICA.quantizza(70.8f), 0f) // Do dell'ottava sopra
    }

    @Test
    fun `gli estremi della posizione coprono Do3-Do6`() {
        assertEquals(Note.MIDI_MIN, Note.posizioneToMidi(-1f, Scala.CROMATICA), 0f)
        assertEquals(Note.MIDI_MAX, Note.posizioneToMidi(2f, Scala.CROMATICA), 0f)
    }
}
