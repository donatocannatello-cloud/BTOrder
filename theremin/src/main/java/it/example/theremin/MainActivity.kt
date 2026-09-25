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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val engine by lazy { AudioEngine() }
    private val tracker = MotionTracker()
    private lateinit var esecutoreAnalisi: ExecutorService

    /** Scala corrente, letta dal thread di analisi a ogni fotogramma. */
    @Volatile private var scala = Scala.CONTINUA

    // Stato mostrato dalla UI
    private var posizioneUi by mutableStateOf(PosizioneMano(0.5f, 0.5f, 0f))
    private var scalaUi by mutableStateOf(Scala.CONTINUA)
    private var inPausa by mutableStateOf(false)

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
     * Mappatura mano → suono, come in un theremin:
     * - orizzontale (sinistra → destra) = altezza, da Do3 a Do6;
     * - verticale (basso → alto) = volume; senza mano in campo il suono sfuma nel silenzio.
     */
    private fun suPosizione(p: PosizioneMano) {
        val synth = engine.synth
        synth.frequenzaBersaglio = Note.midiToHz(Note.posizioneToMidi(p.x, scala))
        val altezzaMano = ((0.92f - p.y) / 0.84f).coerceIn(0f, 1f)
        synth.volumeBersaglio = if (inPausa) 0f else p.presenza * altezzaMano
        runOnUiThread { posizioneUi = p }
    }

    @Composable
    private fun SchermataTheremin() {
        val p = posizioneUi
        val midi = Note.posizioneToMidi(p.x, scalaUi)
        val suona = p.presenza > 0.3f && !inPausa

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
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
                AnteprimaFotocamera(p, Modifier.width(120.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "← grave · acuto →      ↑ forte · piano ↓",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            inPausa = !inPausa
                            if (inPausa) engine.synth.volumeBersaglio = 0f
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    ) { Text(if (inPausa) "Suona" else "Muto", color = Color.White) }
                    OutlinedButton(onClick = {
                        scala = scala.successiva()
                        scalaUi = scala
                    }) { Text("Scala: ${scalaUi.etichetta}", color = Color.White) }
                    OutlinedButton(onClick = { tracker.ricalibra() }) {
                        Text("Ricalibra", color = Color.White)
                    }
                }
            }
        }
    }

    /** Piccola anteprima specchiata della fotocamera anteriore, con il punto rilevato come mano. */
    @Composable
    private fun AnteprimaFotocamera(p: PosizioneMano, modifier: Modifier) {
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
}
