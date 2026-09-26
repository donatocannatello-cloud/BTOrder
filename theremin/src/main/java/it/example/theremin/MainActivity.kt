package it.example.theremin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import it.example.theremin.audio.AudioEngine
import it.example.theremin.audio.Note
import it.example.theremin.audio.Scala
import it.example.theremin.camera.HandAnalyzer
import it.example.theremin.camera.MotionTracker
import it.example.theremin.camera.PosizioneMano
import it.example.theremin.ui.ChromaticWaves
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import it.example.theremin.learn.Brano
import it.example.theremin.learn.Lezione
import it.example.theremin.learn.Nota
import it.example.theremin.learn.Repertorio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import it.example.theremin.audio.Impostazioni
import it.example.theremin.audio.ImpostazioniStore
import it.example.theremin.audio.Timbro
import it.example.theremin.camera.PuntoMano
import kotlin.math.roundToInt
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Modalità dell'app: suono libero oppure lezione guidata su un brano. */
enum class Modalita(val etichetta: String) { SUONA("Suona"), IMPARA("Impara") }

class MainActivity : ComponentActivity() {

    private val engine by lazy { AudioEngine() }
    private val tracker = MotionTracker()
    private lateinit var esecutoreAnalisi: ExecutorService

    // Letti dal thread di analisi a ogni fotogramma
    @Volatile private var scala = Scala.CONTINUA
    @Volatile private var impostazioni = Impostazioni()
    @Volatile private var modalita = Modalita.SUONA
    @Volatile private var lezione = Lezione(Repertorio.brani.first())
    /** Durante l'ascolto dimostrativo è il brano, non la mano, a comandare il synth. */
    @Volatile private var inDimostrazione = false
    private var ultimoFotogrammaNs = 0L

    // Stato mostrato dalla UI
    private var posizioneUi by mutableStateOf(PosizioneMano(0.5f, 0.5f, 0f))
    private var scalaUi by mutableStateOf(Scala.CONTINUA)
    private var modalitaUi by mutableStateOf(Modalita.SUONA)
    private var branoUi by mutableStateOf(Repertorio.brani.first())
    private var indiceUi by mutableIntStateOf(0)
    private var progressoNotaUi by mutableFloatStateOf(0f)
    private var indiceDimostrazione by mutableIntStateOf(-1)
    private var sceltaBranoAperta by mutableStateOf(false)
    private var inPausa by mutableStateOf(false)
    private var impostazioniUi by mutableStateOf(Impostazioni())
    private var pannelloSuonoAperto by mutableStateOf(false)
    private var dimostrazione: Job? = null
    private val store by lazy { ImpostazioniStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        esecutoreAnalisi = Executors.newSingleThreadExecutor()
        aggiornaImpostazioni(store.leggi(), salva = false)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var permesso by remember { mutableStateOf(haPermessoCamera()) }
                val richiesta = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { concesso -> permesso = concesso }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ChromaticWaves(engine, Modifier.fillMaxSize())
                    if (permesso) {
                        SchermataTheremin()
                        if (sceltaBranoAperta) SceltaBrano()
                        if (pannelloSuonoAperto) PannelloSuono()
                    } else {
                        RichiestaPermesso { richiesta.launch(Manifest.permission.CAMERA) }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        engine.avvia()
    }

    override fun onStop() {
        fermaDimostrazione()
        engine.ferma()
        super.onStop()
    }

    override fun onDestroy() {
        esecutoreAnalisi.shutdown()
        super.onDestroy()
    }

    private fun haPermessoCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Posizione orizzontale della mano nell'inquadratura → nota MIDI (prima della trasposizione d'ottava).
     * - i margini laterali sono esclusi, così le note estreme si raggiungono senza uscire dal campo;
     * - in Suona la tastiera copre le ottave scelte, con scala e tonalità scelte;
     * - in Impara copre solo l'estensione del brano e le note si agganciano ai semitoni.
     */
    private fun midiDa(x: Float, mod: Modalita, brano: Brano, scala: Scala, imp: Impostazioni): Float {
        val t = imp.tastieraDaCamera(x)
        return if (mod == Modalita.IMPARA) {
            Note.posizioneToMidi(t, Scala.CROMATICA, brano.estensioneMin, brano.estensioneMax)
        } else {
            Note.posizioneToMidi(t, scala, imp.midiMin, imp.midiMax, imp.tonica)
        }
    }

    /** Frequenza effettivamente suonata: la nota trasposta dell'ottava scelta. */
    private fun hzSuonati(midi: Float) = Note.midiToHz(midi + 12f * impostazioni.ottava)

    private fun aggiornaImpostazioni(nuove: Impostazioni, salva: Boolean = true) {
        impostazioni = nuove
        impostazioniUi = nuove
        engine.synth.applica(nuove)
        tracker.punto = nuove.puntoMano
        if (salva) store.salva(nuove)
    }

    /**
     * Mappatura mano → suono, come in un theremin:
     * - orizzontale (sinistra → destra) = altezza;
     * - verticale (basso → alto) = volume; senza mano in campo il suono sfuma nel silenzio.
     *
     * In modalità Impara la stessa nota fa anche avanzare la lezione.
     */
    private fun suPosizione(p: PosizioneMano) {
        val ora = System.nanoTime()
        val dtMs = if (ultimoFotogrammaNs == 0L) 0f else ((ora - ultimoFotogrammaNs) / 1e6f).coerceAtMost(200f)
        ultimoFotogrammaNs = ora

        val midi = midiDa(p.x, modalita, lezione.brano, scala, impostazioni)
        val volume = if (inPausa) 0f else p.presenza * volumeDaAltezza(p.y)
        if (!inDimostrazione) {
            engine.synth.frequenzaBersaglio = hzSuonati(midi)
            engine.synth.volumeBersaglio = volume
        }

        val l = lezione
        if (modalita == Modalita.IMPARA && !inDimostrazione) {
            l.aggiorna(if (volume > 0.15f) midi else null, dtMs)
        }
        val indice = l.indice
        val progresso = l.progressoNota
        runOnUiThread {
            posizioneUi = p
            indiceUi = indice
            progressoNotaUi = progresso
        }
    }

    private fun volumeDaAltezza(y: Float) = ((0.92f - y) / 0.84f).coerceIn(0f, 1f)

    private fun cambiaModalita(nuova: Modalita) {
        fermaDimostrazione()
        modalita = nuova
        modalitaUi = nuova
    }

    private fun scegliBrano(brano: Brano) {
        fermaDimostrazione()
        lezione = Lezione(brano)
        branoUi = brano
        indiceUi = 0
        sceltaBranoAperta = false
    }

    private fun ricominciaLezione() {
        fermaDimostrazione()
        lezione.ricomincia()
        indiceUi = 0
    }

    /** Suona il brano da solo, con il cursore guida che si sposta nota per nota. */
    private fun avviaDimostrazione() {
        fermaDimostrazione()
        val brano = lezione.brano
        inDimostrazione = true
        dimostrazione = lifecycleScope.launch {
            try {
                val synth = engine.synth
                for ((i, nota) in brano.note.withIndex()) {
                    indiceDimostrazione = i
                    val durata = brano.durataMs(nota)
                    synth.frequenzaBersaglio = hzSuonati(nota.midi.toFloat())
                    synth.volumeBersaglio = 0.75f
                    delay((durata * 0.85f).toLong())
                    // Breve calo di volume per articolare le note, anche quelle ripetute
                    synth.volumeBersaglio = 0.2f
                    delay((durata * 0.15f).toLong())
                }
            } finally {
                engine.synth.volumeBersaglio = 0f
                indiceDimostrazione = -1
                inDimostrazione = false
            }
        }
    }

    private fun fermaDimostrazione() {
        dimostrazione?.cancel()
        dimostrazione = null
    }

    @Composable
    private fun SchermataTheremin() {
        val p = posizioneUi
        val imp = impostazioniUi
        val (min, max) = if (modalitaUi == Modalita.IMPARA) branoUi.estensioneMin to branoUi.estensioneMax
        else imp.midiMin to imp.midiMax
        val midi = midiDa(p.x, modalitaUi, branoUi, scalaUi, imp)
        val suona = p.presenza > 0.3f && !inPausa

        // Nota guida: quella della dimostrazione in corso, altrimenti quella da suonare
        val indiceGuida = if (indiceDimostrazione >= 0) indiceDimostrazione else indiceUi
        val guida = if (modalitaUi == Modalita.IMPARA) branoUi.note.getOrNull(indiceGuida) else null
        val prossima = if (modalitaUi == Modalita.IMPARA) branoUi.note.getOrNull(indiceGuida + 1) else null

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                SelettoreModalita()
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        if (modalitaUi == Modalita.SUONA) {
                            InfoSuona(suona, midi + 12f * imp.ottava)
                        } else {
                            InfoImpara(suona, midi, guida)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    AnteprimaFotocamera(
                        p = p,
                        guida = guida,
                        prossima = prossima,
                        estensione = min to max,
                        imp = imp,
                        brano = if (modalitaUi == Modalita.IMPARA) branoUi else null,
                        intonata = guida != null && suona && Lezione.intonata(midi, guida),
                        modifier = Modifier.width(if (modalitaUi == Modalita.IMPARA) 150.dp else 120.dp),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (modalitaUi == Modalita.IMPARA) "Porta il punto bianco dentro il cerchio colorato"
                    else "← grave · acuto →      ↑ forte · piano ↓",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                if (modalitaUi == Modalita.IMPARA) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(contentPadding = PADDING_PULSANTI, onClick = { sceltaBranoAperta = true }) {
                            Text("♫ Brano", color = Color.White)
                        }
                        OutlinedButton(contentPadding = PADDING_PULSANTI, onClick = {
                            if (indiceDimostrazione >= 0) fermaDimostrazione() else avviaDimostrazione()
                        }) {
                            Text(if (indiceDimostrazione >= 0) "■ Stop" else "▶ Ascolta", color = Color.White)
                        }
                        OutlinedButton(contentPadding = PADDING_PULSANTI, onClick = ::ricominciaLezione) {
                            Text("↺ Da capo", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        contentPadding = PADDING_PULSANTI,
                        onClick = {
                            inPausa = !inPausa
                            if (inPausa) engine.synth.volumeBersaglio = 0f
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    ) { Text(if (inPausa) "Suona" else "Muto", color = Color.White) }
                    if (modalitaUi == Modalita.SUONA) {
                        OutlinedButton(contentPadding = PADDING_PULSANTI, onClick = {
                            scala = scala.successiva()
                            scalaUi = scala
                        }) { Text("Scala: ${scalaUi.etichetta}", color = Color.White) }
                    }
                    OutlinedButton(contentPadding = PADDING_PULSANTI, onClick = { tracker.ricalibra() }) {
                        Text("Ricalibra", color = Color.White)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SelettoreModalita() {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (m in Modalita.entries) {
                FilterChip(
                    selected = modalitaUi == m,
                    onClick = { cambiaModalita(m) },
                    label = { Text(m.etichetta) },
                )
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(contentPadding = PADDING_PULSANTI, onClick = { pannelloSuonoAperto = true }) {
                Text("🎛 Suono", color = Color.White)
            }
        }
    }

    @Composable
    private fun InfoSuona(suona: Boolean, midi: Float) {
        Text(
            if (suona) Note.nome(midi) else "—",
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            if (suona) "%.1f Hz".format(Note.midiToHz(midi)) else "Muovi la mano davanti alla fotocamera",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
    }

    @Composable
    private fun InfoImpara(suona: Boolean, midi: Float, guida: Nota?) {
        val brano = branoUi
        Text(brano.titolo, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(brano.autore, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (guida == null) {
            Text("Bravo! 🎉", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text("Brano completato", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        } else {
            val intonata = suona && Lezione.intonata(midi, guida)
            Text(
                Note.nome(guida.midi.toFloat()),
                color = if (intonata) Color(0xFF69F0AE) else Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                if (suona) "Stai suonando: ${Note.nome(midi)}" else "Alza la mano nell'inquadratura",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
            )
            val indice = if (indiceDimostrazione >= 0) indiceDimostrazione else indiceUi
            val seguenti = brano.note.drop(indice + 1).take(4)
            if (seguenti.isNotEmpty()) {
                Text(
                    "Poi: " + seguenti.joinToString(" · ") { Note.nomeBreve(it.midi.toFloat()) },
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { indice.toFloat() / brano.note.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Nota ${indice + 1} di ${brano.note.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }

    @Composable
    private fun SceltaBrano() {
        AlertDialog(
            onDismissRequest = { sceltaBranoAperta = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sceltaBranoAperta = false }) { Text("Chiudi") }
            },
            title = { Text("Scegli un brano") },
            text = {
                LazyColumn {
                    items(Repertorio.brani) { brano ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { scegliBrano(brano) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                brano.titolo,
                                fontWeight = if (brano == branoUi) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                "${brano.autore} · ${brano.note.size} note",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    private fun PannelloSuono() {
        val imp = impostazioniUi
        fun cambia(nuove: Impostazioni) = aggiornaImpostazioni(nuove)

        ModalBottomSheet(onDismissRequest = { pannelloSuonoAperto = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Suono", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Timbro", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in Timbro.entries) {
                        FilterChip(
                            selected = imp.timbro == t,
                            onClick = { cambia(imp.copy(timbro = t)) },
                            label = { Text(t.etichetta) },
                        )
                    }
                }
                Regolazione(
                    "Ottava", if (imp.ottava > 0) "+${imp.ottava}" else "${imp.ottava}",
                    imp.ottava.toFloat(), Impostazioni.OTTAVA_MIN.toFloat()..Impostazioni.OTTAVA_MAX.toFloat(),
                    passi = Impostazioni.OTTAVA_MAX - Impostazioni.OTTAVA_MIN - 1,
                ) { cambia(imp.copy(ottava = it.roundToInt())) }
                Regolazione(
                    "Tonalità (per le scale)", Note.nomeClasse(imp.tonica),
                    imp.tonica.toFloat(), 0f..11f, passi = 10,
                ) { cambia(imp.copy(tonica = it.roundToInt())) }
                Regolazione(
                    "Estensione in Suona", "${imp.estensioneOttave} ottav${if (imp.estensioneOttave == 1) "a" else "e"}",
                    imp.estensioneOttave.toFloat(), 1f..4f, passi = 2,
                ) { cambia(imp.copy(estensioneOttave = it.roundToInt())) }
                Regolazione("Vibrato", "${(imp.vibrato * 100).roundToInt()}%", imp.vibrato, 0f..1f) {
                    cambia(imp.copy(vibrato = it))
                }
                Regolazione("Velocità vibrato", "%.1f Hz".format(imp.vibratoHz), imp.vibratoHz, 2f..9f) {
                    cambia(imp.copy(vibratoHz = it))
                }
                Regolazione("Portamento (glissando)", "${imp.portamentoMs.roundToInt()} ms", imp.portamentoMs, 5f..400f) {
                    cambia(imp.copy(portamentoMs = it))
                }
                Regolazione("Eco", "${(imp.eco * 100).roundToInt()}%", imp.eco, 0f..1f) {
                    cambia(imp.copy(eco = it))
                }
                Regolazione("Calore (saturazione)", "${(imp.calore * 100).roundToInt()}%", imp.calore, 0f..1f) {
                    cambia(imp.copy(calore = it))
                }

                Spacer(Modifier.height(16.dp))
                Text("Lettura della mano", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pm in PuntoMano.entries) {
                        FilterChip(
                            selected = imp.puntoMano == pm,
                            onClick = { cambia(imp.copy(puntoMano = pm)) },
                            label = { Text(pm.etichetta) },
                        )
                    }
                }
                Regolazione(
                    "Margine ai bordi", "${(imp.margine * 100).roundToInt()}%",
                    imp.margine, 0f..0.3f,
                ) { cambia(imp.copy(margine = it)) }
                Text(
                    "Le fasce scure ai lati dell'anteprima sono fuori tastiera: le note estreme si " +
                        "suonano prima del bordo, dove la fotocamera vede ancora bene la mano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { cambia(Impostazioni()) }) { Text("Ripristina predefiniti") }
            }
        }
    }

    @Composable
    private fun Regolazione(
        titolo: String,
        valore: String,
        attuale: Float,
        intervallo: ClosedFloatingPointRange<Float>,
        passi: Int = 0,
        onCambia: (Float) -> Unit,
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(titolo, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valore, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = attuale, onValueChange = onCambia, valueRange = intervallo, steps = passi)
    }

    /** Piccola anteprima specchiata della fotocamera anteriore, con il punto rilevato come mano. */
    @Composable
    private fun AnteprimaFotocamera(
        p: PosizioneMano,
        guida: Nota?,
        prossima: Nota?,
        estensione: Pair<Float, Float>,
        imp: Impostazioni,
        brano: Brano?,
        intonata: Boolean,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val forma = RoundedCornerShape(16.dp)
        // Stessa proporzione (3:4 in verticale) per anteprima e analisi, così il marcatore coincide con l'immagine
        val selettore = remember {
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        }
        val previewView = remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

        DisposableEffect(lifecycleOwner) {
            val futuro = ProcessCameraProvider.getInstance(context)
            futuro.addListener({
                val provider = futuro.get()
                val anteprima = Preview.Builder()
                    .setResolutionSelector(selettore)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analisi = ImageAnalysis.Builder()
                    .setResolutionSelector(selettore)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(esecutoreAnalisi, HandAnalyzer(tracker, ::suPosizione)) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, anteprima, analisi)
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                if (futuro.isDone) futuro.get().unbindAll()
            }
        }

        Box(
            modifier
                .aspectRatio(3f / 4f)
                .clip(forma)
                .border(1.dp, Color.White.copy(alpha = 0.4f), forma)
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val (min, max) = estensione
                val yGuida = ALTEZZA_GUIDA * size.height
                // Dove va la mano, in pixel dell'anteprima, per suonare una certa nota
                fun xNota(midi: Int) = imp.cameraDaTastiera(Note.midiToPosizione(midi.toFloat(), min, max)) * size.width

                // Margini laterali fuori tastiera, oscurati: lì la nota resta quella estrema
                val m = imp.margine * size.width
                drawRect(Color.Black.copy(alpha = 0.45f), Offset.Zero, androidx.compose.ui.geometry.Size(m, size.height))
                drawRect(Color.Black.copy(alpha = 0.45f), Offset(size.width - m, 0f), androidx.compose.ui.geometry.Size(m, size.height))
                if (brano != null) {
                    // Una fascia verticale per ogni nota usata dal brano, nel suo colore
                    val larghezzaNota = size.width * (1f - 2f * imp.margine) / (max - min)
                    for (n in brano.note.map { it.midi }.distinct()) {
                        val x = xNota(n)
                        drawLine(
                            Color.hsv(Note.tinta(n.toFloat()), 0.7f, 1f, 0.35f),
                            Offset(x, 0f), Offset(x, size.height),
                            strokeWidth = (larghezzaNota * 0.08f).coerceAtLeast(1.dp.toPx()),
                        )
                    }
                }
                if (prossima != null) {
                    val c = Offset(xNota(prossima.midi), yGuida)
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                }
                if (guida != null) {
                    val c = Offset(xNota(guida.midi), yGuida)
                    val colore = if (intonata) Color(0xFF69F0AE) else Color.hsv(Note.tinta(guida.midi.toFloat()), 0.8f, 1f)
                    if (p.presenza > 0.3f) {
                        drawLine(colore.copy(alpha = 0.6f), Offset(p.x * size.width, p.y * size.height), c, strokeWidth = 2.dp.toPx())
                    }
                    drawCircle(colore.copy(alpha = 0.3f), radius = 16.dp.toPx(), center = c)
                    drawCircle(colore, radius = 16.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                    // Arco che si riempie mentre si tiene la nota
                    drawArc(
                        Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progressoNotaUi,
                        useCenter = false,
                        topLeft = c - Offset(20.dp.toPx(), 20.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(40.dp.toPx(), 40.dp.toPx()),
                        style = Stroke(3.dp.toPx()),
                    )
                }
                if (p.presenza > 0.3f) {
                    val centro = Offset(p.x * size.width, p.y * size.height)
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = centro, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = centro)
                }
            }
        }
    }

    @Composable
    private fun RichiestaPermesso(onRichiedi: () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Theremin Cromatico",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Per suonare con la mano serve la fotocamera anteriore: l'immagine viene analizzata " +
                    "solo sul dispositivo e non viene salvata né inviata.",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRichiedi) { Text("Consenti fotocamera") }
        }
    }

    private companion object {
        /** Altezza (0 = in alto) a cui la guida invita a tenere la mano: volume pieno ma comodo. */
        const val ALTEZZA_GUIDA = 0.3f
        val PADDING_PULSANTI = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    }
}
