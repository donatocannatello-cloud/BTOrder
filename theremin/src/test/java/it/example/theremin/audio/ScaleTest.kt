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

    @Test
    fun `la tonalita sposta la scala`() {
        // Pentatonica di Re: Re Mi Fa# La Si
        assertEquals(66f, Scala.PENTATONICA.quantizza(65.8f, tonica = 2), 0f) // Fa#
        assertEquals(62f, Scala.PENTATONICA.quantizza(61.6f, tonica = 2), 0f) // Re
        assertEquals(71f, Scala.PENTATONICA.quantizza(71.4f, tonica = 2), 0f) // Si
    }

    @Test
    fun `i margini fanno raggiungere gli estremi prima del bordo`() {
        val imp = Impostazioni(margine = 0.15f)
        assertEquals(0f, imp.tastieraDaCamera(0.1f), 0f)
        assertEquals(1f, imp.tastieraDaCamera(0.9f), 0f)
        assertEquals(0.5f, imp.tastieraDaCamera(0.5f), 1e-6f)
        assertEquals(0.3f, imp.tastieraDaCamera(imp.cameraDaTastiera(0.3f)), 1e-6f)
    }
}
