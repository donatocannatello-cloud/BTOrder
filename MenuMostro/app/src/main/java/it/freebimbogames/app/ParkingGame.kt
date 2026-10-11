package it.freebimbogames.app

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.freebimbogames.app.ui.theme.SfondoChiaro
import it.freebimbogames.app.ui.theme.palettePiatti
import kotlin.math.roundToInt

private const val LARGHEZZA_GRIGLIA = 320

private enum class SchermataParcheggio { HOME, GIOCO, FINE }

@Composable
fun AppMonsterParking(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataParcheggio.HOME) }
    var numeroLivello by remember { mutableStateOf(1) }
    var mosseUltimoLivello by remember { mutableStateOf(0) }

    fun nuovaPartita() {
        numeroLivello = 1
        schermata = SchermataParcheggio.GIOCO
    }

    when (schermata) {
        SchermataParcheggio.HOME -> SchermataHomeParcheggio(
            onGioca = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataParcheggio.GIOCO -> SchermataGiocoParcheggio(
            livello = elencoLivelliParcheggio[numeroLivello - 1],
            onRisolto = { mosse ->
                mosseUltimoLivello = mosse
                schermata = SchermataParcheggio.FINE
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataParcheggio.FINE -> SchermataFineParcheggio(
            numeroLivello = numeroLivello,
            mosse = mosseUltimoLivello,
            ultimoLivello = numeroLivello >= elencoLivelliParcheggio.size,
            onProssimoLivello = {
                numeroLivello += 1
                schermata = SchermataParcheggio.GIOCO
            },
            onNuovaPartita = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHomeParcheggio(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sfondo_parcheggio_mostri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Sfumatura scura solo nella metà inferiore: l'illustrazione resta ben
        // visibile in alto, il testo resta leggibile in basso.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1.0f to Color(0xE6000000)
                    )
                )
        )
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            sfondo = Color(0x99000000)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Monster Parking".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Un mostro ha parcheggiato tutto male! Libera la macchina rossa e falla uscire!".maiuscolo(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onGioca,
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(width = 220.dp, height = 64.dp)
            ) {
                Text(text = "🎮 Gioca!".maiuscolo(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SchermataGiocoParcheggio(livello: LivelloParcheggio, onRisolto: (Int) -> Unit, onTornaAiGiochi: () -> Unit) {
    var auto by remember(livello) { mutableStateOf(livello.autoIniziali) }
    var selezionataId by remember(livello) { mutableStateOf<Int?>(null) }
    var mosse by remember(livello) { mutableStateOf(0) }
    // Durante il trascinamento: quale auto si sta spostando e di quante celle (anche frazionarie)
    var trascinataId by remember(livello) { mutableStateOf<Int?>(null) }
    var offsetTrascinamento by remember(livello) { mutableStateOf(0f) }

    val cellSizeDp = (LARGHEZZA_GRIGLIA / livello.dimensione).dp
    val cellSizePx = with(LocalDensity.current) { cellSizeDp.toPx() }
    val latoGrigliaDp = cellSizeDp * livello.dimensione

    // Riparte dall'inizio della schermata ad ogni nuovo livello (griglia diversa).
    val statoScroll = rememberScrollState()
    LaunchedEffect(livello) { statoScroll.scrollTo(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .verticalScroll(statoScroll)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BottoneTornaAiGiochi(onClick = onTornaAiGiochi)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Livello ${livello.numero}".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            Text(text = "🔧 $mosse".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Trascina la macchina rossa per liberarla!".maiuscolo(),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .size(latoGrigliaDp)
                .pointerInput(livello) {
                    awaitEachGesture {
                        val giu = awaitFirstDown()
                        val rigaTocco = (giu.position.y / cellSizePx).toInt().coerceIn(0, livello.dimensione - 1)
                        val colTocco = (giu.position.x / cellSizePx).toInt().coerceIn(0, livello.dimensione - 1)
                        val occupante = auto.firstOrNull { (rigaTocco to colTocco) in it.celle() }
                        if (occupante == null) {
                            selezionataId = null
                            return@awaitEachGesture
                        }
                        val limiti = limiteTrascinamento(auto, occupante.id, livello.dimensione)
                        var offset = 0f
                        var ultimaPosizione = giu.position
                        trascinataId = occupante.id
                        do {
                            val evento = awaitPointerEvent()
                            val dito = evento.changes.firstOrNull { it.id == giu.id }
                            if (dito != null && dito.pressed) {
                                val delta = dito.position - ultimaPosizione
                                ultimaPosizione = dito.position
                                val deltaCelle = if (occupante.orientamento == Orientamento.ORIZZONTALE) {
                                    delta.x / cellSizePx
                                } else {
                                    delta.y / cellSizePx
                                }
                                offset = (offset + deltaCelle).coerceIn(limiti.start, limiti.endInclusive)
                                offsetTrascinamento = offset
                                dito.consume()
                            }
                        } while (dito != null && dito.pressed)
                        trascinataId = null
                        offsetTrascinamento = 0f
                        val delta = offset.roundToInt()
                        if (delta == 0) {
                            selezionataId = if (selezionataId == occupante.id) null else occupante.id
                        } else {
                            val nuove = provaSpostamento(auto, occupante.id, delta, livello.dimensione)
                            if (nuove != null) {
                                SuoniGioco.tocco()
                                auto = nuove
                                selezionataId = occupante.id
                                mosse += 1
                                if (livello.risolto(nuove)) onRisolto(mosse)
                            }
                        }
                    }
                }
        ) {
            disegnaGrigliaParcheggio(
                livello = livello,
                auto = auto,
                selezionataId = selezionataId,
                trascinataId = trascinataId,
                offsetTrascinamento = offsetTrascinamento,
                cellSizePx = cellSizePx,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Tocca un'auto per sceglierla, poi trascinala".maiuscolo(), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Colore stabile per ogni auto: rosso fisso per l'auto da liberare, palette a rotazione per le altre. */
private fun coloreAuto(tutteLeAuto: List<Auto>, auto: Auto): Color {
    if (auto.rossa) return Color(0xFFE53935)
    val indice = tutteLeAuto.filter { !it.rossa }.indexOfFirst { it.id == auto.id }
    return palettePiatti[indice % palettePiatti.size]
}

private val pennelloBandiera = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
}

/** La griglia del parcheggio: celle vuote, bandiera del traguardo e, sopra, le auto vere e proprie. */
private fun DrawScope.disegnaGrigliaParcheggio(
    livello: LivelloParcheggio,
    auto: List<Auto>,
    selezionataId: Int?,
    trascinataId: Int?,
    offsetTrascinamento: Float,
    cellSizePx: Float,
) {
    // Le celle si disegnano tutte, anche sotto le auto: durante un trascinamento l'auto si
    // sposta ma la sua posizione "ufficiale" (e quindi le celle libere) cambia solo al rilascio,
    // altrimenti la cella da cui l'auto sta uscendo resterebbe vuota finché non si lascia.
    val margineCella = cellSizePx * 0.04f
    for (riga in 0 until livello.dimensione) {
        for (colonna in 0 until livello.dimensione) {
            drawRoundRect(
                Color(0xFFE0E0E0),
                topLeft = Offset(colonna * cellSizePx + margineCella, riga * cellSizePx + margineCella),
                size = Size(cellSizePx - margineCella * 2, cellSizePx - margineCella * 2),
                cornerRadius = CornerRadius(cellSizePx * 0.12f),
            )
            if (riga == livello.rigaUscita && colonna == livello.dimensione - 1) {
                pennelloBandiera.textSize = cellSizePx * 0.55f
                drawContext.canvas.nativeCanvas.drawText(
                    "🏁",
                    colonna * cellSizePx + cellSizePx / 2f,
                    riga * cellSizePx + cellSizePx * 0.68f,
                    pennelloBandiera,
                )
            }
        }
    }
    // L'auto trascinata si disegna per ultima, sopra alle altre: è l'unica che può sfiorarle.
    val ordinate = auto.sortedBy { if (it.id == trascinataId) 1 else 0 }
    for (a in ordinate) {
        val extra = if (a.id == trascinataId) offsetTrascinamento else 0f
        disegnaAuto(
            auto = a,
            colore = coloreAuto(livello.autoIniziali, a),
            selezionata = a.id == selezionataId || a.id == trascinataId,
            extra = extra,
            cellSizePx = cellSizePx,
        )
    }
}

/**
 * Un'auto lunga vera e propria (non più auto piccole in fila): un corpo a capsula colorato, con
 * finestrino, quattro ruote e un musetto a mostriciattolo per restare nel tema della suite.
 */
private fun DrawScope.disegnaAuto(auto: Auto, colore: Color, selezionata: Boolean, extra: Float, cellSizePx: Float) {
    val orizzontale = auto.orientamento == Orientamento.ORIZZONTALE
    val rigaPx = (auto.riga + if (orizzontale) 0f else extra) * cellSizePx
    val colPx = (auto.colonna + if (orizzontale) extra else 0f) * cellSizePx
    val larghezzaPx = (if (orizzontale) auto.lunghezza * cellSizePx else cellSizePx)
    val altezzaPx = (if (orizzontale) cellSizePx else auto.lunghezza * cellSizePx)
    val margine = cellSizePx * 0.1f
    val left = colPx + margine
    val top = rigaPx + margine
    val w = larghezzaPx - margine * 2
    val h = altezzaPx - margine * 2
    val corpo = Offset(left, top)
    val dimensioneCorpo = Size(w, h)
    val raggioCorpo = (if (orizzontale) h else w) / 2f

    drawRoundRect(colore, corpo, dimensioneCorpo, CornerRadius(raggioCorpo))

    // Finestrino: una fascia più chiara lungo il centro del corpo
    val spessoreFinestrino = (if (orizzontale) h else w) * 0.42f
    val finestrino = Color.White.copy(alpha = 0.5f)
    if (orizzontale) {
        drawRoundRect(
            finestrino,
            topLeft = Offset(left + w * 0.14f, top + (h - spessoreFinestrino) / 2f),
            size = Size(w * 0.72f, spessoreFinestrino),
            cornerRadius = CornerRadius(spessoreFinestrino * 0.4f),
        )
    } else {
        drawRoundRect(
            finestrino,
            topLeft = Offset(left + (w - spessoreFinestrino) / 2f, top + h * 0.14f),
            size = Size(spessoreFinestrino, h * 0.72f),
            cornerRadius = CornerRadius(spessoreFinestrino * 0.4f),
        )
    }

    // Ruote: due coppie, vicino alle due estremità lungo la lunghezza
    val raggioRuota = cellSizePx * 0.085f
    val ruotaScostamento = raggioCorpo * 0.92f
    val posizioniRuote = listOf(0.26f, 0.74f)
    val ruotaColore = Color(0xFF263238)
    for (f in posizioniRuote) {
        if (orizzontale) {
            val cx = left + w * f
            drawCircle(ruotaColore, raggioRuota, Offset(cx, top + ruotaScostamento))
            drawCircle(ruotaColore, raggioRuota, Offset(cx, top + h - ruotaScostamento))
        } else {
            val cy = top + h * f
            drawCircle(ruotaColore, raggioRuota, Offset(left + ruotaScostamento, cy))
            drawCircle(ruotaColore, raggioRuota, Offset(left + w - ruotaScostamento, cy))
        }
    }

    // Occhietti da mostro verso un'estremità, per restare nel tema della suite
    val raggioOcchio = cellSizePx * 0.07f
    val centroCorto = if (orizzontale) top + h / 2f else left + w / 2f
    val bordoLungo = (if (orizzontale) w else h)
    val posizioneOcchi = (if (orizzontale) left else top) + bordoLungo * 0.84f
    val scartoOcchi = raggioOcchio * 1.6f
    val occhi = if (orizzontale) {
        listOf(Offset(posizioneOcchi, centroCorto - scartoOcchi), Offset(posizioneOcchi, centroCorto + scartoOcchi))
    } else {
        listOf(Offset(centroCorto - scartoOcchi, posizioneOcchi), Offset(centroCorto + scartoOcchi, posizioneOcchi))
    }
    for (o in occhi) {
        drawCircle(Color.White, raggioOcchio, o)
        drawCircle(Color.Black, raggioOcchio * 0.5f, o)
    }

    if (selezionata) {
        drawRoundRect(
            Color.Black,
            corpo,
            dimensioneCorpo,
            CornerRadius(raggioCorpo),
            style = Stroke(cellSizePx * 0.05f),
        )
    }
}

@Composable
fun SchermataFineParcheggio(
    numeroLivello: Int,
    mosse: Int,
    ultimoLivello: Boolean,
    onProssimoLivello: () -> Unit,
    onNuovaPartita: () -> Unit,
    onTornaAiGiochi: () -> Unit
) {
    LaunchedEffect(Unit) { SuoniGioco.vittoria() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SfondoChiaro)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (ultimoLivello) "🏆" else "🎉", fontSize = 96.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (
                    if (ultimoLivello) {
                        "Hai liberato tutte le auto! Sei un campione!"
                    } else {
                        "Livello $numeroLivello superato!"
                    }
                    ).maiuscolo(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mosse usate: $mosse".maiuscolo(),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (ultimoLivello) {
                Button(
                    onClick = onNuovaPartita,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(width = 240.dp, height = 64.dp)
                ) {
                    Text(text = "🔁 Nuova partita".maiuscolo(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onProssimoLivello,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(width = 240.dp, height = 64.dp)
                ) {
                    Text(text = "➡️ Prossimo livello".maiuscolo(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}
