package it.example.theremin.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceltaRadioTest {

    private val rtl = StazioneRadio("RTL 102.5 Romeo & Juliet", "https://rtl/stream", homepage = "https://www.rtl.it")
    private val verona = StazioneRadio("Radio Romeo & Juliet", "https://verona/stream", homepage = "https://radioromeoandjuliet.com/")
    private val altra = StazioneRadio("Romeo FM", "https://romeo/stream")

    @Test
    fun `sceglie la radio di Verona e mai quella RTL, anche se piu ascoltata`() {
        assertEquals(verona, SceltaRadio.romeoAndJuliet(listOf(rtl, altra, verona)))
    }

    @Test
    fun `riconosce il nome anche senza sito, scritto con la e commerciale`() {
        val senzaSito = verona.copy(homepage = "")
        assertEquals(senzaSito, SceltaRadio.romeoAndJuliet(listOf(rtl, senzaSito)))
    }

    @Test
    fun `se c'e solo la RTL non sceglie nulla`() {
        assertNull(SceltaRadio.romeoAndJuliet(listOf(rtl, altra)))
    }

    @Test
    fun `ricerca libera`() {
        assertEquals(altra, SceltaRadio.migliore(listOf(rtl, altra), "romeo fm"))
    }
}
