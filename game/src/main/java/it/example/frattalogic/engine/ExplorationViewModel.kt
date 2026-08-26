package it.example.frattalogic.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.example.frattalogic.audio.SoundEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

// Risoluzione di rendering: si adatta alla dimensione reale dello schermo
// (vedi impostaRisoluzione) invece di restare fissa — altrimenti l'immagine
// resta sempre un piccolo bitmap ingrandito, indipendentemente da quanto si
// scende in profondità. Questi sono solo i valori di default finché la UI
// non riporta la dimensione reale del canvas, e i limiti di sicurezza.
private const val LARGHEZZA_RENDER_DEFAULT = 96
private const val ALTEZZA_RENDER_DEFAULT = 160
private const val DIVISORE_RISOLUZIONE = 5
private const val LARGHEZZA_RENDER_MINIMA = 96
private const val LARGHEZZA_RENDER_MASSIMA = 220
private const val ALTEZZA_RENDER_MINIMA = 160
private const val ALTEZZA_RENDER_MASSIMA = 380

private const val VELOCITA_ZOOM_BASE = 0.55f
private const val VELOCITA_ZOOM_MASSIMA = 1.6f
private const val DERIVA_LATERALE = 0.5f
private const val SEMIAMPIEZZA_INIZIALE = 1.4
private const val SEMIAMPIEZZA_MINIMA = 1e-13
private const val LIVELLI_ZOOM_PER_EVENTO_BONUS = 15f

// Inerzia della camera: velocità di discesa e deriva laterale inseguono
// l'input del joystick invece di seguirlo istantaneamente — ma abbastanza
// in fretta da restare percepibile come controllo, non solo come deriva.
private const val COSTANTE_INERZIA = 4.5f

// Respiro idle: un lieve drift laterale sempre presente, anche a joystick
// fermo, così la camera non è mai perfettamente immobile.
private const val FREQUENZA_RESPIRO_DERIVA = 0.8f
private const val AMPIEZZA_RESPIRO_DERIVA = 0.018f

// "FOV" che respira con la velocità: variazione puramente visiva della
// finestra inquadrata (non della vera traiettoria di discesa).
private const val FREQUENZA_RESPIRO_FOV = 1.3f
private const val AMPIEZZA_RESPIRO_FOV = 0.035f

// Risalita automatica: se la vista resta troppo a lungo dentro una zona
// monocromatica (interno solido dell'insieme, senza alcun dettaglio da
// rivelare — e da cui lo sterzo non basta più a uscire, perché la sua
// sensibilità si restringe proporzionalmente allo zoom), la discesa si
// inverte da sola finché non riappare dettaglio a sufficienza.
private const val SOGLIA_ZONA_VUOTA = 0.02f
private const val SOGLIA_TEMPO_VUOTO = 0.6f
private const val SOGLIA_USCITA_RISALITA = 0.12f
private const val VELOCITA_RISALITA = 1.3f
private const val VELOCITA_FUGA_LATERALE = 0.9f

/**
 * Guida un'immersione continua nel mare frattale: lo sterzo a schermo
 * imposta la velocità di discesa (zoom verso l'interno, asse principale) e
 * una lieve deriva laterale; un loop continuo ricalcola ad ogni fotogramma
 * la finestra sul piano complesso e la passa a [FractalField]. La colonna
 * sonora segue [livelloZoom] con continuità ad ogni fotogramma; ogni tot
 * livelli di zoom (non troppo spesso, per non spezzare il flusso) si apre
 * un breve evento bonus — lo stesso enigma "trova la dissonanza" (nucleo +
 * anello di nodi) delle versioni precedenti — per stabilizzare la discesa.
 */
class ExplorationViewModel : ViewModel() {

    private val random = Random(System.nanoTime())
    val soundEngine = SoundEngine()

    private val _state = MutableStateFlow(ImmersioneState())
    val state: StateFlow<ImmersioneState> = _state.asStateFlow()

    @Volatile private var derivaInput = 0f
    @Volatile private var discesaInput = 0f
    private var cicloAvviato = false

    @Volatile private var larghezzaRender = LARGHEZZA_RENDER_DEFAULT
    @Volatile private var altezzaRender = ALTEZZA_RENDER_DEFAULT

    // Stato dell'inerzia della camera: valori effettivi che inseguono il
    // target impostato dal joystick, più il tempo totale per il respiro idle.
    private var velocitaZoomEffettiva = VELOCITA_ZOOM_BASE
    private var derivaEffettiva = 0f
    private var tempoTotale = 0f

    // Stato della risalita automatica dalle zone monocromatiche.
    private var tempoInZonaVuota = 0f
    private var inRisalita = false
    private var angoloFuga = 0f

    fun avvia() {
        soundEngine.start()
        soundEngine.aggiornaProfondita(_state.value.livelloZoom.toInt())
        if (cicloAvviato) return
        cicloAvviato = true
        viewModelScope.launch(Dispatchers.Default) {
            var ultimoTick = System.nanoTime()
            while (true) {
                delay(40L)
                val ora = System.nanoTime()
                val dt = ((ora - ultimoTick) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.15f)
                ultimoTick = ora
                aggiorna(dt)
            }
        }
    }

    fun ferma() = soundEngine.stop()

    /**
     * Chiamato dalla UI non appena conosce la dimensione reale (in pixel)
     * del canvas: la risoluzione di rendering si adatta di conseguenza
     * (entro limiti di sicurezza per le prestazioni), invece di restare
     * fissa e sempre uguale a qualunque profondità.
     */
    fun impostaRisoluzione(larghezzaSchermoPx: Int, altezzaSchermoPx: Int) {
        if (larghezzaSchermoPx <= 0 || altezzaSchermoPx <= 0) return
        larghezzaRender = (larghezzaSchermoPx / DIVISORE_RISOLUZIONE)
            .coerceIn(LARGHEZZA_RENDER_MINIMA, LARGHEZZA_RENDER_MASSIMA)
        altezzaRender = (altezzaSchermoPx / DIVISORE_RISOLUZIONE)
            .coerceIn(ALTEZZA_RENDER_MINIMA, ALTEZZA_RENDER_MASSIMA)
    }

    /** Chiamato dal joystick a schermo: x/y in [-1, 1] (sinistra/destra, giù/su). */
    fun impostaSterzo(x: Float, y: Float) {
        derivaInput = x.coerceIn(-1f, 1f)
        discesaInput = (-y).coerceIn(-1f, 1f)
    }

    fun toccaBonus(indice: Int) {
        val corrente = _state.value
        val camera = corrente.cameraBonus ?: return
        if (corrente.esitoBonus != Esito.NESSUNO) return

        val risolto = indice == camera.indiceDissonante
        if (risolto) {
            soundEngine.onRisolto(corrente.livelloZoom.toInt())
        } else {
            soundEngine.onRottura()
        }

        _state.value = corrente.copy(
            punteggio = corrente.punteggio + if (risolto) 40 + corrente.livelloZoom.toInt() * 8 else 0,
            indiceSelezionatoBonus = indice,
            esitoBonus = if (risolto) Esito.RISOLTO else Esito.ROTTURA
        )

        viewModelScope.launch {
            delay(if (risolto) 900L else 700L)
            concludiEventoBonus()
        }
    }

    private fun aggiorna(dt: Float) {
        val corrente = _state.value
        if (corrente.fase == Fase.EVENTO_BONUS) return

        val larghezza = larghezzaRender
        val altezza = altezzaRender

        tempoTotale += dt

        val velocitaZoomTarget = VELOCITA_ZOOM_BASE + (VELOCITA_ZOOM_MASSIMA - VELOCITA_ZOOM_BASE) *
            ((discesaInput + 1f) / 2f)
        val derivaTarget = derivaInput * DERIVA_LATERALE

        // Inerzia: la velocità e la deriva effettive inseguono il target con
        // uno smorzamento esponenziale, mai un salto istantaneo.
        val fattoreInerzia = 1f - exp(-COSTANTE_INERZIA * dt)
        velocitaZoomEffettiva += (velocitaZoomTarget - velocitaZoomEffettiva) * fattoreInerzia
        derivaEffettiva += (derivaTarget - derivaEffettiva) * fattoreInerzia

        // Respiro idle: un lieve drift continuo che si somma alla deriva
        // effettiva, presente anche a joystick fermo.
        val derivaRespiro = AMPIEZZA_RESPIRO_DERIVA * sin(tempoTotale * FREQUENZA_RESPIRO_DERIVA)

        // Durante la risalita automatica la velocità di zoom viene invertita
        // (si esce, non si scende) indipendentemente dall'input del joystick.
        val velocitaZoomApplicata = if (inRisalita) -VELOCITA_RISALITA else velocitaZoomEffettiva
        val fattoreZoom = exp(-velocitaZoomApplicata * dt)
        val nuovaSemiAmpiezza = (corrente.semiAmpiezza * fattoreZoom)
            .coerceIn(SEMIAMPIEZZA_MINIMA, SEMIAMPIEZZA_INIZIALE)

        // In risalita si aggiunge anche una spinta laterale decisa, in una
        // direzione scelta una sola volta all'inizio dell'episodio, per non
        // riemergere esattamente nello stesso punto vuoto da cui si è entrati.
        val perturbazioneX = if (inRisalita) {
            cos(angoloFuga) * corrente.semiAmpiezza * VELOCITA_FUGA_LATERALE * dt
        } else {
            0.0
        }
        val perturbazioneY = if (inRisalita) {
            sin(angoloFuga) * corrente.semiAmpiezza * VELOCITA_FUGA_LATERALE * dt
        } else {
            0.0
        }

        val derivaX = (derivaEffettiva + derivaRespiro) * corrente.semiAmpiezza * dt + perturbazioneX
        val nuovoCentroX = corrente.centroX + derivaX
        val nuovoCentroY = corrente.centroY + perturbazioneY

        val nuovoLivelloZoom = (-ln(nuovaSemiAmpiezza / SEMIAMPIEZZA_INIZIALE) / ln(2.0))
            .toFloat()
            .coerceAtLeast(0f)
        val maxIterazioni = (60 + nuovoLivelloZoom * 4f).toInt().coerceAtMost(400)
        val faseColore = (nuovoLivelloZoom * 14f) % 360f

        // "FOV" che respira con la velocità: variazione puramente visiva
        // della finestra inquadrata (nuovaSemiAmpiezza, quella "vera" che
        // guida profondità/audio/traguardi, resta pulita da questo effetto).
        val respiroFov = 1f + AMPIEZZA_RESPIRO_FOV *
            sin(tempoTotale * FREQUENZA_RESPIRO_FOV) *
            (velocitaZoomEffettiva / VELOCITA_ZOOM_MASSIMA).coerceIn(0f, 1f)
        val semiAmpiezzaResa = nuovaSemiAmpiezza * respiroFov

        val risultato = FractalField.renderizza(
            centroX = nuovoCentroX,
            centroY = nuovoCentroY,
            semiAmpiezza = semiAmpiezzaResa,
            larghezza = larghezza,
            altezza = altezza,
            maxIterazioni = maxIterazioni,
            faseColore = faseColore
        )

        // Rileva le zone monocromatiche (interno solido, senza dettaglio) e,
        // se durano troppo, inverte automaticamente la rotta finché non
        // riappare dettaglio a sufficienza: senza questa correzione, una
        // volta dentro non c'è più nulla da fare (nemmeno lo sterzo basta,
        // perché la sua sensibilità si restringe con lo zoom).
        if (risultato.frazioneEscape < SOGLIA_ZONA_VUOTA) {
            tempoInZonaVuota += dt
        } else {
            tempoInZonaVuota = 0f
        }
        val nuovoInRisalita = when {
            inRisalita && risultato.frazioneEscape > SOGLIA_USCITA_RISALITA -> false
            !inRisalita && tempoInZonaVuota > SOGLIA_TEMPO_VUOTO -> true
            else -> inRisalita
        }
        if (nuovoInRisalita && !inRisalita) {
            angoloFuga = random.nextFloat() * (2f * PI.toFloat())
            soundEngine.onRottura()
        } else if (!nuovoInRisalita && inRisalita) {
            soundEngine.onRisolto(nuovoLivelloZoom.toInt())
        }
        inRisalita = nuovoInRisalita

        soundEngine.aggiornaProfondita(nuovoLivelloZoom.toInt())

        val traguardoRaggiunto = (nuovoLivelloZoom / LIVELLI_ZOOM_PER_EVENTO_BONUS).toInt()
        val traguardoPrecedente = (corrente.livelloZoom / LIVELLI_ZOOM_PER_EVENTO_BONUS).toInt()
        if (traguardoRaggiunto > traguardoPrecedente && !inRisalita) {
            avviaEventoBonus(
                corrente, nuovoCentroX, nuovoCentroY, nuovaSemiAmpiezza, nuovoLivelloZoom,
                risultato.pixel, larghezza, altezza
            )
            return
        }

        _state.value = corrente.copy(
            centroX = nuovoCentroX,
            centroY = nuovoCentroY,
            semiAmpiezza = nuovaSemiAmpiezza,
            livelloZoom = nuovoLivelloZoom,
            pixel = risultato.pixel,
            larghezzaPixel = larghezza,
            altezzaPixel = altezza
        )
    }

    private fun avviaEventoBonus(
        corrente: ImmersioneState,
        centroX: Double,
        centroY: Double,
        semiAmpiezza: Double,
        livelloZoom: Float,
        pixel: IntArray,
        larghezzaPixel: Int,
        altezzaPixel: Int
    ) {
        _state.value = corrente.copy(
            centroX = centroX,
            centroY = centroY,
            semiAmpiezza = semiAmpiezza,
            livelloZoom = livelloZoom,
            pixel = pixel,
            larghezzaPixel = larghezzaPixel,
            altezzaPixel = altezzaPixel,
            fase = Fase.EVENTO_BONUS,
            cameraBonus = DiveEngine.generaCamera(livelloZoom.toInt(), random),
            indiceSelezionatoBonus = null,
            esitoBonus = Esito.NESSUNO
        )
    }

    private fun concludiEventoBonus() {
        val corrente = _state.value
        _state.value = corrente.copy(
            fase = Fase.IMMERSIONE,
            cameraBonus = null,
            indiceSelezionatoBonus = null,
            esitoBonus = Esito.NESSUNO
        )
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.stop()
    }
}
