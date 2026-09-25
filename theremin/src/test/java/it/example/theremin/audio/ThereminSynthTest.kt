package it.example.theremin.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThereminSynthTest {

    @Test
    fun `silenzio a volume zero, suono e glissando verso il bersaglio`() {
        val synth = ThereminSynth(48_000)
        val blocco = FloatArray(4800)
        assertEquals(0f, synth.render(blocco), 0f)

        synth.frequenzaBersaglio = 880f
        synth.volumeBersaglio = 1f
        repeat(10) { synth.render(blocco) } // 1 secondo
        assertEquals(880f, synth.frequenzaCorrente, 1f)
        assertEquals(1f, synth.volumeCorrente, 0.01f)

        val picco = synth.render(blocco)
        assertTrue("picco $picco", picco in 0.3f..1f)

        // Conta i passaggi per lo zero ascendenti: ~88 periodi in 0,1 s
        var periodi = 0
        for (i in 1 until blocco.size) if (blocco[i - 1] < 0f && blocco[i] >= 0f) periodi++
        assertEquals(88f, periodi.toFloat(), 2f)
    }
}
