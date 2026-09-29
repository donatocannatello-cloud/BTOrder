package it.freebimbogames.app.acchiappamostri

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Size
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import it.freebimbogames.app.BottoneTornaAiGiochi
import it.freebimbogames.app.maiuscolo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

// ---------------------------------------------------------------------------------
// L'Acchiappamostri: la fotocamera anteriore riempie lo schermo e sopra l'immagine
// compaiono mostriciattoli colorati e le loro tane. Si prendono i mostri con il punto fra
// pollice e indice (o chiudendo la mano), li si porta nella tana giusta e si lasciano
// aprendo le dita. Le due mani vengono riconosciute insieme: si possono spostare due
// mostri alla volta, e i mostri pesanti si sollevano solo con entrambe. A differenza
// degli altri giochi della suite usa la fotocamera (analizzata solo sul telefono, mai
// salvata né inviata) invece del tocco sullo schermo, e ha un suo tema scuro perché
// il video della fotocamera deve restare ben visibile.
// ---------------------------------------------------------------------------------

private enum class SchermataAcchiappa { MENU, GIOCO, PAUSA }

private data class Hud(val livello: Int, val punteggio: Int, val secondi: Int, val maniViste: Int)
private data class Messaggio(val testo: String, val id: Long = System.nanoTime())
private class Galleggiante(val testo: String, var pos: Offset, var vita: Float, val colore: Color)

private const val ID_TOCCO = 99
private const val CHIAVE_RECORD_UNA = "record_una_mano"
private const val CHIAVE_RECORD_DUE = "record_due_mani"
private const val CHIAVE_DUE_MANI = "due_mani"
private val COLORI_MANI = listOf(Color(0xFFFFD54F), Color(0xFF40C4FF))

private fun haPermessoCamera(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun chiaveRecord(dueMani: Boolean) = if (dueMani) CHIAVE_RECORD_DUE else CHIAVE_RECORD_UNA
private fun leggiRecord(preferenze: SharedPreferences, dueMani: Boolean) = preferenze.getInt(chiaveRecord(dueMani), 0)

@Composable
fun AppAcchiappamostri(onTornaAiGiochi: () -> Unit) {
    val context = LocalContext.current
    val esecutoreAnalisi = remember { Executors.newSingleThreadExecutor() }
    val trackerRef = remember { AtomicReference<TrackerMani?>() }
    val fotogrammaRef = remember { AtomicReference<FotogrammaMani?>() }
    var erroreTracker by remember { mutableStateOf<String?>(null) }
    val suoni = remember { Suoni(context) }
    val preferenze = remember { context.getSharedPreferences("acchiappamostri", Context.MODE_PRIVATE) }

    // Il modello IA si carica sul thread di analisi, prima del primo fotogramma
    DisposableEffect(Unit) {
        esecutoreAnalisi.execute {
            trackerRef.set(
                try {
                    TrackerMani(context.applicationContext)
                } catch (e: Throwable) {
                    erroreTracker = "Riconoscimento delle mani non disponibile su questo telefono: " +
                        "puoi comunque giocare trascinando i mostri con il dito."
                    null
                }
            )
        }
        onDispose {
            esecutoreAnalisi.execute { trackerRef.get()?.close() }
            esecutoreAnalisi.shutdown()
            suoni.rilascia()
        }
    }

    // Schermo sempre acceso durante il gioco: le mani sono impegnate ad acchiappare mostri,
    // non a toccare lo schermo. Si toglie il flag appena si esce da questo gioco.
    val attivita = context as? Activity
    DisposableEffect(attivita) {
        attivita?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { attivita?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        var permesso by remember { mutableStateOf(haPermessoCamera(context)) }
        val richiesta = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concesso -> permesso = concesso }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (permesso) {
                Gioco(esecutoreAnalisi, trackerRef, fotogrammaRef, erroreTracker, suoni, preferenze, onTornaAiGiochi)
            } else {
                RichiestaPermesso(onTornaAiGiochi) { richiesta.launch(Manifest.permission.CAMERA) }
            }
        }
    }
}

@Composable
private fun Gioco(
    esecutoreAnalisi: ExecutorService,
    trackerRef: AtomicReference<TrackerMani?>,
    fotogrammaRef: AtomicReference<FotogrammaMani?>,
    erroreTracker: String?,
    suoni: Suoni,
    preferenze: SharedPreferences,
    onTornaAiGiochi: () -> Unit,
) {
    val dp = LocalDensity.current.density
    var dimensioni by remember { mutableStateOf(IntSize.Zero) }
    var schermata by remember { mutableStateOf(SchermataAcchiappa.MENU) }
    val inseguitore = remember { InseguitoreMani() }
    val particelle = remember { Particelle() }
    val galleggianti = remember { ArrayList<Galleggiante>() }
    var partita by remember { mutableStateOf<Partita?>(null) }
    // Cambia a ogni fotogramma dello schermo: fa ridisegnare la scena
    var tick by remember { mutableLongStateOf(0L) }
    var hud by remember { mutableStateOf(Hud(1, 0, 0, 0)) }
    var messaggio by remember { mutableStateOf<Messaggio?>(null) }
    var livelloFinito by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Modalità scelta nel menu: a una mano (l'altra tiene il telefono) o a due mani
    var dueMani by remember { mutableStateOf(preferenze.getBoolean(CHIAVE_DUE_MANI, true)) }
    // Un record per ciascuna modalità
    var record by remember(dueMani) { mutableIntStateOf(leggiRecord(preferenze, dueMani)) }
    // Il dito sullo schermo fa da "terza mano", utile anche senza riconoscimento IA
    var tocco by remember { mutableStateOf<Offset?>(null) }

    fun gestisci(e: Evento, p: Partita) {
        when (e) {
            is Evento.Presa -> suoni.suona(Suoni.Effetto.PRESA)
            is Evento.Incastro -> {
                suoni.suona(Suoni.Effetto.INCASTRO)
                val centro = p.slotDi(e.pezzo).centro.offset()
                val colore = Color(e.pezzo.tipo.colore)
                particelle.esplodi(centro, colore, if (e.pezzo.pesante) 60 else 32, p.larghezza * 0.35f, 4f * dp)
                galleggianti += Galleggiante("+${e.punti}", centro, 1.2f, colore)
                if (e.pezzo.pesante) messaggio = Messaggio("Gran lavoro di squadra, mani!".maiuscolo())
            }
            is Evento.Sbagliato -> {
                suoni.suona(Suoni.Effetto.SBAGLIATO)
                messaggio = Messaggio("Non è la tana giusta per questo mostro!".maiuscolo())
            }
            is Evento.ServonoDueMani -> {
                suoni.suona(Suoni.Effetto.PESANTE)
                messaggio = Messaggio("✋✋ È un mostro grosso: prendilo con tutte e due le mani!".maiuscolo())
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
                    preferenze.edit().putInt(chiaveRecord(dueMani), record).apply()
                }
            }
            is Evento.NuovoLivello -> {
                livelloFinito = null
                val novita = when {
                    p.movimento && p.trasformazione -> "si muovono e si trasformano!"
                    p.trasformazione -> "i mostri si trasformano, sbrigati!"
                    p.movimento -> "i mostri si muovono!"
                    else -> "${p.pezzi.size} mostri"
                }
                messaggio = Messaggio("Livello ${e.livello}: $novita".maiuscolo())
            }
            // Si sente solo per il mostro che si ha in mano: è lui che cambia destinazione
            is Evento.Cambio -> if (e.tenuto) suoni.suona(Suoni.Effetto.CAMBIO)
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

                val f = fotogrammaRef.get()
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
                if (schermata == SchermataAcchiappa.GIOCO) {
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
        AnteprimaFotocamera(Modifier.fillMaxSize(), esecutoreAnalisi, trackerRef, fotogrammaRef)
        // Velo leggero: i mostri risaltano anche su uno sfondo chiaro
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
            if (schermata != SchermataAcchiappa.MENU) {
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
            SchermataAcchiappa.MENU -> Menu(hud.maniViste, dueMani, erroreTracker, preferenze, onTornaAiGiochi) { scelta ->
                dueMani = scelta
                preferenze.edit().putBoolean(CHIAVE_DUE_MANI, scelta).apply()
                inseguitore.maniAttive = if (scelta) 2 else 1
                partita?.ricomincia(scelta)
                livelloFinito = null
                schermata = SchermataAcchiappa.GIOCO
                messaggio = Messaggio("Livello 1: prendi un mostro con pollice e indice".maiuscolo())
            }
            SchermataAcchiappa.GIOCO -> Intestazione(hud, record, onTornaAiGiochi) { schermata = SchermataAcchiappa.PAUSA }
            SchermataAcchiappa.PAUSA -> Pausa(
                onRiprendi = { schermata = SchermataAcchiappa.GIOCO },
                onRicomincia = {
                    partita?.ricomincia()
                    livelloFinito = null
                    schermata = SchermataAcchiappa.GIOCO
                },
                onMenu = {
                    // Nel menu si guardano sempre tutte e due le mani, per la scelta della modalità
                    inseguitore.maniAttive = 2
                    schermata = SchermataAcchiappa.MENU
                },
                onTornaAiGiochi = onTornaAiGiochi,
            )
        }

        if (schermata == SchermataAcchiappa.GIOCO) {
            livelloFinito?.let { (livello, bonus) ->
                Column(
                    Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Livello $livello completato!".maiuscolo(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (bonus > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Bonus velocità +$bonus".maiuscolo(), color = Color(0xFFFFD54F), fontSize = 20.sp)
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
private fun Intestazione(hud: Hud, record: Int, onTornaAiGiochi: () -> Unit, onPausa: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottoneTornaAiGiochi(onClick = onTornaAiGiochi, sfondo = Color(0x99000000))
        Etichetta("Livello ${hud.livello}".maiuscolo())
        Etichetta("★ ${hud.punteggio}")
        Etichetta("⏱ %d:%02d".format(hud.secondi / 60, hud.secondi % 60))
        Spacer(Modifier.weight(1f))
        Etichetta(if (hud.maniViste == 0) "nessuna mano".maiuscolo() else "✋".repeat(hud.maniViste))
        Button(onClick = onPausa) { Text("❚❚") }
    }
    if (record > 0 && hud.punteggio > record) {
        // Il record viene aggiornato a fine livello; qui lo si festeggia in anticipo
        Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
            Etichetta("Nuovo record!".maiuscolo())
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
private fun Menu(
    maniViste: Int,
    ultimaDueMani: Boolean,
    erroreTracker: String?,
    preferenze: SharedPreferences,
    onTornaAiGiochi: () -> Unit,
    onGioca: (dueMani: Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            sfondo = Color(0x99000000),
        )
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("L'Acchiappamostri".maiuscolo(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                val regole = listOf(
                    "Mettiti davanti al telefono e mostra le mani alla fotocamera.",
                    "Porta il punto fra pollice e indice sopra un mostro e unisci le due dita (o chiudi la mano) per prenderlo.",
                    "Portalo nella tana con la stessa faccia e apri le dita per lasciarlo.",
                    "Scegli la modalità: con una mano, se con l'altra tieni il telefono, oppure con " +
                        "due mani (telefono appoggiato): sposti due mostri insieme e ci sono i mostri " +
                        "grossi ✋✋, che si sollevano solo con tutte e due le mani.",
                    "Livello 2: i mostri si muovono. Livello 3: si trasformano quando il cerchio " +
                        "intorno si svuota, anche in mano! Dal livello 4: tutte e due le cose.",
                )
                for (r in regole) {
                    Text("• $r".maiuscolo(), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (maniViste) {
                        0 -> "Non vedo ancora le tue mani"
                        1 -> "Vedo una mano ✋"
                        else -> "Vedo due mani ✋✋"
                    }.maiuscolo(),
                    color = if (maniViste > 0) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.7f),
                )
                erroreTracker?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.maiuscolo(), color = Color(0xFFFFAB91), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))
                // L'ultima modalità usata è il pulsante pieno, l'altra quello a contorno
                for (due in listOf(false, true)) {
                    val testo = if (due) "✋✋  Due mani" else "✋  Una mano"
                    val mod = Modifier.fillMaxWidth().height(56.dp)
                    if (due == ultimaDueMani) {
                        Button(onClick = { onGioca(due) }, modifier = mod) { Text(testo.maiuscolo(), fontSize = 20.sp) }
                    } else {
                        OutlinedButton(onClick = { onGioca(due) }, modifier = mod) { Text(testo.maiuscolo(), fontSize = 20.sp) }
                    }
                    val r = leggiRecord(preferenze, due)
                    Text(
                        (if (r > 0) "Record: $r" else "Nessun record").maiuscolo(),
                        color = Color(0xFFFFD54F).copy(alpha = if (r > 0) 1f else 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Pausa(onRiprendi: () -> Unit, onRicomincia: () -> Unit, onMenu: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            sfondo = Color(0x99000000),
        )
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pausa".maiuscolo(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onRiprendi, modifier = Modifier.width(220.dp)) { Text("Riprendi".maiuscolo()) }
            OutlinedButton(onClick = onRicomincia, modifier = Modifier.width(220.dp)) { Text("Ricomincia dal livello 1".maiuscolo()) }
            OutlinedButton(onClick = onMenu, modifier = Modifier.width(220.dp)) { Text("Menu".maiuscolo()) }
        }
    }
}

/** Anteprima della fotocamera anteriore a tutto schermo, specchiata, con l'analisi delle mani. */
@Composable
private fun AnteprimaFotocamera(
    modifier: Modifier,
    esecutoreAnalisi: ExecutorService,
    trackerRef: AtomicReference<TrackerMani?>,
    fotogrammaRef: AtomicReference<FotogrammaMani?>,
) {
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
                        AnalizzatoreMani(tracker = { trackerRef.get() }, suFotogramma = { f -> fotogrammaRef.set(f) }),
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

/** Scheletro della mano riconosciuta e, fra pollice e indice, il cerchio che prende i mostri. */
private fun DrawScope.disegnaMano(posto: InseguitoreMani.Posto, colore: Color, dp: Float) {
    val punti = posto.punti
    if (punti.size >= Gesti.PUNTI_MANO) {
        for ((a, b) in Gesti.OSSA) {
            drawLine(Color.White.copy(alpha = 0.5f), punti[a].offset(), punti[b].offset(), 2.5f * dp, StrokeCap.Round)
        }
        for (pt in punti) drawCircle(colore.copy(alpha = 0.85f), 3.5f * dp, pt.offset())
        // Le due dita che prendono, evidenziate
        drawLine(colore.copy(alpha = 0.7f), punti[Gesti.PUNTA_POLLICE].offset(), punti[Gesti.PUNTA_INDICE].offset(), 3f * dp, StrokeCap.Round)
        drawCircle(colore, 6f * dp, punti[Gesti.PUNTA_POLLICE].offset())
        drawCircle(colore, 6f * dp, punti[Gesti.PUNTA_INDICE].offset())
    }
    val c = posto.posizione.offset()
    if (posto.afferra) {
        drawCircle(colore.copy(alpha = 0.55f), 18f * dp, c)
        drawCircle(colore, 18f * dp, c, style = Stroke(4f * dp))
    } else {
        // Il cerchio si stringe mentre pollice e indice si avvicinano: a 18 dp scatta la presa
        val t = ((posto.pizzico - Gesti.PIZZICO_PRESA) / (1f - Gesti.PIZZICO_PRESA)).coerceIn(0f, 1f)
        val r = (18f + 16f * t) * dp
        drawCircle(colore.copy(alpha = 0.15f), r, c)
        drawCircle(colore, r, c, style = Stroke(3f * dp))
        drawCircle(colore, 3f * dp, c)
    }
}

private val pennelloGalleggiante = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
    setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
}

private fun DrawScope.testo(testo: String, pos: Offset, dimensione: Float, colore: Color) {
    pennelloGalleggiante.textSize = dimensione
    pennelloGalleggiante.color = colore.toArgb()
    drawContext.canvas.nativeCanvas.drawText(testo, pos.x, pos.y, pennelloGalleggiante)
}

@Composable
private fun RichiestaPermesso(onTornaAiGiochi: () -> Unit, onRichiedi: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            sfondo = Color(0x99000000),
        )
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("L'Acchiappamostri".maiuscolo(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                ("Per giocare con le mani serve la fotocamera anteriore: l'immagine viene analizzata " +
                    "solo sul telefono e non viene salvata né inviata.").maiuscolo(),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRichiedi) { Text("Consenti fotocamera".maiuscolo()) }
        }
    }
}
