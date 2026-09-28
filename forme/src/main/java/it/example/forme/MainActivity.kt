package it.example.forme

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import it.example.forme.audio.Suoni
import it.example.forme.camera.AnalizzatoreMani
import it.example.forme.camera.FotogrammaMani
import it.example.forme.camera.TrackerMani
import it.example.forme.gioco.Evento
import it.example.forme.gioco.Gesti
import it.example.forme.gioco.InseguitoreMani
import it.example.forme.gioco.ManoGioco
import it.example.forme.gioco.MappaturaSchermo
import it.example.forme.gioco.Partita
import it.example.forme.gioco.Punto
import it.example.forme.ui.Particelle
import it.example.forme.ui.disegnaPezzo
import it.example.forme.ui.disegnaSlot
import it.example.forme.ui.offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Incastra le Forme: la fotocamera anteriore riempie lo schermo e sopra l'immagine compaiono
 * forme colorate e i loro incavi. Si prendono le forme con il punto fra pollice e indice,
 * unendo le due dita (o chiudendo la mano), si portano nell'incavo giusto e si lasciano aprendo le dita.
 * Le due mani vengono riconosciute insieme: si possono spostare due forme alla volta, e le
 * forme pesanti si sollevano solo con entrambe.
 */
class MainActivity : ComponentActivity() {

    private enum class Schermata { MENU, GIOCO, PAUSA }

    private data class Hud(val livello: Int, val punteggio: Int, val secondi: Int, val maniViste: Int)
    private data class Messaggio(val testo: String, val id: Long = System.nanoTime())
    private class Galleggiante(val testo: String, var pos: Offset, var vita: Float, val colore: Color)

    private lateinit var esecutoreAnalisi: ExecutorService
    @Volatile private var tracker: TrackerMani? = null
    @Volatile private var ultimoFotogramma: FotogrammaMani? = null
    private var erroreTracker by mutableStateOf<String?>(null)
    private lateinit var suoni: Suoni
    private val preferenze by lazy { getSharedPreferences("forme", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        suoni = Suoni(this)
        esecutoreAnalisi = Executors.newSingleThreadExecutor()
        // Il modello IA si carica sul thread di analisi, prima del primo fotogramma
        esecutoreAnalisi.execute {
            tracker = try {
                TrackerMani(applicationContext)
            } catch (e: Throwable) {
                erroreTracker = "Riconoscimento delle mani non disponibile su questo telefono: " +
                    "puoi comunque giocare trascinando le forme con il dito."
                null
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var permesso by remember { mutableStateOf(haPermessoCamera()) }
                val richiesta = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { concesso -> permesso = concesso }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (permesso) {
                        Gioco()
                    } else {
                        RichiestaPermesso { richiesta.launch(Manifest.permission.CAMERA) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        esecutoreAnalisi.execute { tracker?.close() }
        esecutoreAnalisi.shutdown()
        suoni.rilascia()
        super.onDestroy()
    }

    private fun haPermessoCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Gioco() {
        val dp = LocalDensity.current.density
        var dimensioni by remember { mutableStateOf(IntSize.Zero) }
        var schermata by remember { mutableStateOf(Schermata.MENU) }
        val inseguitore = remember { InseguitoreMani() }
        val particelle = remember { Particelle() }
        val galleggianti = remember { ArrayList<Galleggiante>() }
        var partita by remember { mutableStateOf<Partita?>(null) }
        // Cambia a ogni fotogramma dello schermo: fa ridisegnare la scena
        var tick by remember { mutableLongStateOf(0L) }
        var hud by remember { mutableStateOf(Hud(1, 0, 0, 0)) }
        var messaggio by remember { mutableStateOf<Messaggio?>(null) }
        var livelloFinito by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var record by remember { mutableIntStateOf(preferenze.getInt(CHIAVE_RECORD, 0)) }
        // Il dito sullo schermo fa da "terza mano", utile anche senza riconoscimento IA
        var tocco by remember { mutableStateOf<Offset?>(null) }

        fun gestisci(e: Evento, p: Partita) {
            when (e) {
                is Evento.Presa -> suoni.suona(Suoni.Effetto.PRESA)
                is Evento.Incastro -> {
                    suoni.suona(Suoni.Effetto.INCASTRO)
                    val centro = p.slotDi(e.pezzo).centro.offset()
                    val colore = Color(e.pezzo.tipo.colore)
                    particelle.esplodi(centro, colore, if (e.pezzo.pesante) 60 else 32, e.pezzo.raggio * 5f, 5f * dp)
                    galleggianti += Galleggiante("+${e.punti}", centro, 1.2f, colore)
                    if (e.pezzo.pesante) messaggio = Messaggio("Gran lavoro di squadra, mani!")
                }
                is Evento.Sbagliato -> {
                    suoni.suona(Suoni.Effetto.SBAGLIATO)
                    messaggio = Messaggio("Non è il posto del ${e.pezzo.tipo.nome.lowercase()}!")
                }
                is Evento.ServonoDueMani -> {
                    suoni.suona(Suoni.Effetto.PESANTE)
                    messaggio = Messaggio("✋✋ È pesante: afferrala con tutte e due le mani!")
                }
                is Evento.LivelloCompletato -> {
                    suoni.suona(Suoni.Effetto.LIVELLO)
                    livelloFinito = e.livello to e.bonus
                    val w = p.larghezza
                    val h = p.altezza
                    for (k in 0 until 5) {
                        particelle.esplodi(Offset(w * (0.15f + 0.175f * k), h * 0.45f), Color.hsv(k * 72f, 0.7f, 1f), 30, w * 0.6f, 6f * dp)
                    }
                    if (p.punteggio > record) {
                        record = p.punteggio
                        preferenze.edit().putInt(CHIAVE_RECORD, record).apply()
                    }
                }
                is Evento.NuovoLivello -> {
                    livelloFinito = null
                    messaggio = Messaggio("Livello ${e.livello}: ${p.pezzi.size} forme")
                }
            }
        }

        LaunchedEffect(messaggio) {
            if (messaggio != null) {
                delay(2200)
                messaggio = null
            }
        }

        // Ciclo di gioco, sincronizzato con lo schermo
        LaunchedEffect(dimensioni) {
            if (dimensioni == IntSize.Zero) return@LaunchedEffect
            val w = dimensioni.width.toFloat()
            val h = dimensioni.height.toFloat()
            val p = Partita(w, h).also { partita = it }
            var ultimoNs = 0L
            var ultimoNumero = 0L
            var ultimoTsCamera = 0L
            while (isActive) {
                withFrameNanos { ns ->
                    val dt = if (ultimoNs == 0L) 0f else ((ns - ultimoNs) / 1e9f).coerceAtMost(0.1f)
                    ultimoNs = ns

                    val f = ultimoFotogramma
                    if (f != null && f.numero != ultimoNumero) {
                        val dtCamera = if (ultimoTsCamera == 0L) 1f / 30f
                        else ((f.timestampMs - ultimoTsCamera) / 1000f).coerceIn(0.005f, 0.2f)
                        ultimoNumero = f.numero
                        ultimoTsCamera = f.timestampMs
                        val mappa = MappaturaSchermo(w, h, f.aspetto)
                        inseguitore.aggiorna(f.mani.map { mano -> mano.map(mappa::inSchermo) }, dtCamera)
                    }

                    val dito = tocco
                    val mani = inseguitore.mani() + ManoGioco(
                        ID_TOCCO,
                        dito?.let { Punto(it.x, it.y) } ?: Punto.ZERO,
                        presente = dito != null,
                        afferra = dito != null,
                    )
                    if (schermata == Schermata.GIOCO) {
                        for (e in p.aggiorna(dt, mani)) gestisci(e, p)
                    }
                    particelle.aggiorna(dt, gravita = h * 0.9f)
                    galleggianti.removeAll { g ->
                        g.vita -= dt
                        g.pos += Offset(0f, -60f * dp * dt)
                        g.vita <= 0f
                    }
                    hud = Hud(p.livello, p.punteggio, p.tempoLivello.toInt(), inseguitore.posti.count { it.presente })
                    tick++
                }
            }
        }

        Box(Modifier.fillMaxSize().onSizeChanged { dimensioni = it }) {
            AnteprimaFotocamera(Modifier.fillMaxSize())
            // Velo leggero: le forme risaltano anche su uno sfondo chiaro
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val giu = awaitFirstDown()
                            tocco = giu.position
                            do {
                                val evento = awaitPointerEvent()
                                val dito = evento.changes.firstOrNull { it.id == giu.id }
                                if (dito != null) {
                                    tocco = dito.position
                                    dito.consume()
                                }
                            } while (dito != null && dito.pressed)
                            tocco = null
                        }
                    }
            ) {
                tick
                val p = partita ?: return@Canvas
                if (schermata != Schermata.MENU) {
                    for (s in p.slot) disegnaSlot(s, aiutoColore = p.livello <= 2, dp = dp)
                    val tenuti = p.pezzi.filter { p.tenutoDa(it).isNotEmpty() }
                    val ordine = p.pezzi.filter { it.incastrato } +
                        p.pezzi.filter { !it.incastrato && it !in tenuti } + tenuti
                    for (pezzo in ordine) {
                        disegnaPezzo(pezzo, tenuto = pezzo in tenuti, evidenziato = pezzo.id in p.evidenziati, dp = dp)
                    }
                }
                with(particelle) { disegna() }
                for (g in galleggianti) testo(g.testo, g.pos, 26f * dp, g.colore.copy(alpha = g.vita.coerceIn(0f, 1f)))
                for (posto in inseguitore.posti) {
                    if (posto.presente) disegnaMano(posto, COLORI_MANI[posto.id % COLORI_MANI.size], dp)
                }
            }

            when (schermata) {
                Schermata.MENU -> Menu(record, hud.maniViste) {
                    partita?.ricomincia()
                    livelloFinito = null
                    schermata = Schermata.GIOCO
                    messaggio = Messaggio("Livello 1: prendi una forma con pollice e indice")
                }
                Schermata.GIOCO -> Intestazione(hud, record) { schermata = Schermata.PAUSA }
                Schermata.PAUSA -> Pausa(
                    onRiprendi = { schermata = Schermata.GIOCO },
                    onRicomincia = {
                        partita?.ricomincia()
                        livelloFinito = null
                        schermata = Schermata.GIOCO
                    },
                    onMenu = { schermata = Schermata.MENU },
                )
            }

            if (schermata == Schermata.GIOCO) {
                livelloFinito?.let { (livello, bonus) ->
                    Column(
                        Modifier.align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Livello $livello completato!", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        if (bonus > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("Bonus velocità +$bonus", color = Color(0xFFFFD54F), fontSize = 20.sp)
                        }
                    }
                }
                messaggio?.let { m ->
                    Text(
                        m.testo,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Intestazione(hud: Hud, record: Int, onPausa: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Etichetta("Livello ${hud.livello}")
            Etichetta("★ ${hud.punteggio}")
            Etichetta("⏱ %d:%02d".format(hud.secondi / 60, hud.secondi % 60))
            Spacer(Modifier.weight(1f))
            Etichetta(if (hud.maniViste == 0) "nessuna mano" else "✋".repeat(hud.maniViste))
            Button(onClick = onPausa) { Text("❚❚") }
        }
        if (record > 0 && hud.punteggio > record) {
            // Il record viene aggiornato a fine livello; qui lo si festeggia in anticipo
            Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
                Etichetta("Nuovo record!")
            }
        }
    }

    @Composable
    private fun Etichetta(testo: String) {
        Text(
            testo,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    @Composable
    private fun Menu(record: Int, maniViste: Int, onGioca: () -> Unit) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Incastra le Forme", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                val regole = listOf(
                    "Mettiti davanti al telefono e mostra le mani alla fotocamera.",
                    "Porta il punto fra pollice e indice sopra una forma e unisci le due dita (o chiudi la mano) per prenderla.",
                    "Portala nell'incavo con la stessa sagoma e apri le dita per lasciarla.",
                    "Con due mani puoi spostare due forme insieme. Le forme ✋✋ sono pesanti: " +
                        "servono tutte e due le mani!",
                )
                for (r in regole) {
                    Text("• $r", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (maniViste) {
                        0 -> "Non vedo ancora le tue mani"
                        1 -> "Vedo una mano ✋"
                        else -> "Vedo due mani ✋✋"
                    },
                    color = if (maniViste > 0) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.7f),
                )
                erroreTracker?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFFAB91), textAlign = TextAlign.Center)
                }
                if (record > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Record: $record", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onGioca, modifier = Modifier.width(200.dp).height(52.dp)) {
                    Text("Gioca", fontSize = 20.sp)
                }
            }
        }
    }

    @Composable
    private fun Pausa(onRiprendi: () -> Unit, onRicomincia: () -> Unit, onMenu: () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pausa", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onRiprendi, modifier = Modifier.width(220.dp)) { Text("Riprendi") }
                OutlinedButton(onClick = onRicomincia, modifier = Modifier.width(220.dp)) { Text("Ricomincia dal livello 1") }
                OutlinedButton(onClick = onMenu, modifier = Modifier.width(220.dp)) { Text("Menu") }
            }
        }
    }

    /** Anteprima della fotocamera anteriore a tutto schermo, specchiata, con l'analisi delle mani. */
    @Composable
    private fun AnteprimaFotocamera(modifier: Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
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
                // 16:9 come quasi tutti i telefoni in verticale: si taglia poco dell'immagine.
                // Anteprima e analisi hanno la stessa proporzione, così i punti coincidono con l'immagine.
                fun selettore(dimensione: Size) = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(dimensione, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
                val anteprima = Preview.Builder()
                    .setResolutionSelector(selettore(Size(1280, 720)))
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analisi = ImageAnalysis.Builder()
                    .setResolutionSelector(selettore(Size(640, 360)))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            esecutoreAnalisi,
                            AnalizzatoreMani(tracker = { tracker }, suFotogramma = { f -> ultimoFotogramma = f }),
                        )
                    }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, anteprima, analisi)
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                if (futuro.isDone) futuro.get().unbindAll()
            }
        }

        AndroidView({ previewView }, modifier)
    }

    /** Scheletro della mano riconosciuta e, fra pollice e indice, il cerchio che prende le forme. */
    private fun DrawScope.disegnaMano(posto: InseguitoreMani.Posto, colore: Color, dp: Float) {
        val punti = posto.punti
        if (punti.size >= Gesti.PUNTI_MANO) {
            for ((a, b) in Gesti.OSSA) {
                drawLine(Color.White.copy(alpha = 0.5f), punti[a].offset(), punti[b].offset(), 2.5f * dp, StrokeCap.Round)
            }
            for (pt in punti) drawCircle(colore.copy(alpha = 0.85f), 3.5f * dp, pt.offset())
        }
        val c = posto.posizione.offset()
        if (posto.afferra) {
            drawCircle(colore.copy(alpha = 0.45f), 20f * dp, c)
            drawCircle(colore, 20f * dp, c, style = Stroke(4f * dp))
        } else {
            drawCircle(colore.copy(alpha = 0.15f), 28f * dp, c)
            drawCircle(colore, 28f * dp, c, style = Stroke(3f * dp))
        }
    }

    private val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
    }

    private fun DrawScope.testo(testo: String, pos: Offset, dimensione: Float, colore: Color) {
        pennello.textSize = dimensione
        pennello.color = colore.toArgb()
        drawContext.canvas.nativeCanvas.drawText(testo, pos.x, pos.y, pennello)
    }

    @Composable
    private fun RichiestaPermesso(onRichiedi: () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Incastra le Forme", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "Per giocare con le mani serve la fotocamera anteriore: l'immagine viene analizzata " +
                    "solo sul dispositivo e non viene salvata né inviata.",
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRichiedi) { Text("Consenti fotocamera") }
        }
    }

    private companion object {
        const val ID_TOCCO = 99
        const val CHIAVE_RECORD = "record"
        val COLORI_MANI = listOf(Color(0xFFFFD54F), Color(0xFF40C4FF))
    }
}
