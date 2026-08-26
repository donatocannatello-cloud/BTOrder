package it.freebimbogames.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Suoni di interazione condivisi da tutta la suite: un tocco/click generico, un esito positivo
 * (risposta giusta, coppia trovata, mostro colpito...), uno negativo (sbagliato, tempo scaduto,
 * mostro sfuggito...) e una piccola fanfara per fine livello/partita. Un solo SoundPool per tutta
 * l'app, inizializzato una volta in MainActivity prima di ogni gioco.
 */
object SuoniGioco {
    private var soundPool: SoundPool? = null
    private var idTocco = 0
    private var idSuccesso = 0
    private var idErrore = 0
    private var idVittoria = 0
    private var idNoteRitmo = listOf(0, 0, 0, 0)
    private var inizializzato = false

    fun inizializza(context: Context) {
        if (inizializzato) return
        inizializzato = true

        val attributi = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributi)
            .build()
        soundPool = pool

        val app = context.applicationContext
        idTocco = pool.load(app, R.raw.suono_tocco, 1)
        idSuccesso = pool.load(app, R.raw.suono_successo, 1)
        idErrore = pool.load(app, R.raw.suono_errore, 1)
        idVittoria = pool.load(app, R.raw.suono_vittoria, 1)
        idNoteRitmo = listOf(
            pool.load(app, R.raw.nota_ritmo_0, 1),
            pool.load(app, R.raw.nota_ritmo_1, 1),
            pool.load(app, R.raw.nota_ritmo_2, 1),
            pool.load(app, R.raw.nota_ritmo_3, 1)
        )
    }

    /** Un tocco/selezione qualunque: piatto scelto, carta girata, tasto premuto, auto selezionata... */
    fun tocco() {
        soundPool?.play(idTocco, 1f, 1f, 0, 0, 1f)
    }

    /** Un esito positivo: risposta giusta, coppia trovata, mostro colpito, icona trovata... */
    fun successo() {
        soundPool?.play(idSuccesso, 1f, 1f, 0, 0, 1f)
    }

    /** Un esito negativo: risposta sbagliata, coppia non trovata, tempo scaduto, mostro sfuggito... */
    fun errore() {
        soundPool?.play(idErrore, 1f, 1f, 0, 0, 1f)
    }

    /** Fine livello o fine partita completata con successo. */
    fun vittoria() {
        soundPool?.play(idVittoria, 1f, 1f, 0, 0, 1f)
    }

    /** La nota musicale di uno dei 4 tasti di Ritmo Mostruoso, in base al suo [indice] (0-3). */
    fun notaRitmo(indice: Int) {
        soundPool?.play(idNoteRitmo[indice], 1f, 1f, 0, 0, 1f)
    }
}
