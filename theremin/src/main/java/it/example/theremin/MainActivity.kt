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
    private var dimostrazione: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        esecutoreAnalisi = Executors.newSingleThreadExecutor()

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

    /** Estensione della tastiera "invisibile": intera in Suona, ristretta al brano in Impara. */
    private fun estensione(): Pair<Float, Float> =
        if (modalita == Modalita.IMPARA) lezione.brano.estensioneMin to lezione.brano.estensioneMax
        else Note.MIDI_MIN to Note.MIDI_MAX

    /** In Impara le note si agganciano sempre ai semitoni, così è chiaro quando si è intonati. */
    private fun scalaEffettiva(): Scala = if (modalita == Modalita.IMPARA) Scala.CROMATICA else scala

    private fun midiDaPosizione(x: Float): Float {
        val (min, max) = estensione()
        return Note.posizioneToMidi(x, scalaEffettiva(), min, max)
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

        val midi = midiDaPosizione(p.x)
        val volume = if (inPausa) 0f else p.presenza * volumeDaAltezza(p.y)
        if (!inDimostrazione) {
            engine.synth.frequenzaBersaglio = Note.midiToHz(midi)
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
                    synth.frequenzaBersaglio = Note.midiToHz(nota.midi.toFloat())
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
        val (min, max) = if (modalitaUi == Modalita.IMPARA) branoUi.estensioneMin to branoUi.estensioneMax
        else Note.MIDI_MIN to Note.MIDI_MAX
        val scalaMostrata = if (modalitaUi == Modalita.IMPARA) Scala.CROMATICA else scalaUi
        val midi = Note.posizioneToMidi(p.x, scalaMostrata, min, max)
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
                            InfoSuona(suona, midi)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Modalita.entries) {
                FilterChip(
                    selected = modalitaUi == m,
                    onClick = { cambiaModalita(m) },
                    label = { Text(m.etichetta) },
                )
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

    /** Piccola anteprima specchiata della fotocamera anteriore, con il punto rilevato come mano. */
    @Composable
    private fun AnteprimaFotocamera(
        p: PosizioneMano,
        guida: Nota?,
        prossima: Nota?,
        estensione: Pair<Float, Float>,
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
                if (brano != null) {
                    // Una fascia verticale per ogni nota usata dal brano, nel suo colore
                    val larghezzaNota = size.width / (max - min)
                    for (n in brano.note.map { it.midi }.distinct()) {
                        val x = Note.midiToPosizione(n.toFloat(), min, max) * size.width
                        drawLine(
                            Color.hsv(Note.tinta(n.toFloat()), 0.7f, 1f, 0.35f),
                            Offset(x, 0f), Offset(x, size.height),
                            strokeWidth = (larghezzaNota * 0.08f).coerceAtLeast(1.dp.toPx()),
                        )
                    }
                }
                if (prossima != null) {
                    val c = Offset(Note.midiToPosizione(prossima.midi.toFloat(), min, max) * size.width, yGuida)
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                }
                if (guida != null) {
                    val c = Offset(Note.midiToPosizione(guida.midi.toFloat(), min, max) * size.width, yGuida)
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
