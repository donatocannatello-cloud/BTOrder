package it.example.ripassofoto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.example.ripassofoto.quiz.Domanda
import it.example.ripassofoto.quiz.DomandaScelta
import it.example.ripassofoto.quiz.DomandaVeroFalso

private val VerdeCorretto = Color(0xFF2E7D32)
private val RossoErrato = Color(0xFFC62828)

@Composable
fun SchermataQuiz(
    domande: List<Domanda>,
    onQuizCompletato: (punteggio: Int, totale: Int) -> Unit
) {
    if (domande.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Non è stato possibile generare domande da questo testo: prova con una " +
                    "pagina più ricca di contenuto (frasi complete e non troppo brevi)."
            )
        }
        return
    }

    var indice by remember { mutableStateOf(0) }
    var indiceSelezionato by remember(indice) { mutableStateOf<Int?>(null) }
    var selezioneVeroFalso by remember(indice) { mutableStateOf<Boolean?>(null) }
    var risposteCorrette by remember { mutableStateOf(0) }

    val domanda = domande[indice]
    val hasRisposto = indiceSelezionato != null || selezioneVeroFalso != null

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        LinearProgressIndicator(
            progress = { (indice + 1f) / domande.size },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Domanda ${indice + 1} di ${domande.size}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        when (domanda) {
            is DomandaScelta -> DomandaSceltaContenuto(
                domanda = domanda,
                indiceSelezionato = indiceSelezionato,
                onSeleziona = { if (!hasRisposto) indiceSelezionato = it }
            )
            is DomandaVeroFalso -> DomandaVeroFalsoContenuto(
                domanda = domanda,
                selezione = selezioneVeroFalso,
                onSeleziona = { if (!hasRisposto) selezioneVeroFalso = it }
            )
        }

        if (hasRisposto && domanda.spiegazione != null) {
            Text(
                domanda.spiegazione.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                enabled = hasRisposto,
                onClick = {
                    val corretta = when (domanda) {
                        is DomandaScelta -> indiceSelezionato == domanda.indiceCorretto
                        is DomandaVeroFalso -> selezioneVeroFalso == domanda.corretta
                    }
                    val nuovoPunteggio = if (corretta) risposteCorrette + 1 else risposteCorrette

                    if (indice == domande.lastIndex) {
                        onQuizCompletato(nuovoPunteggio, domande.size)
                    } else {
                        risposteCorrette = nuovoPunteggio
                        indice += 1
                    }
                }
            ) {
                Text(if (indice == domande.lastIndex) "Termina il quiz" else "Avanti")
            }
        }
    }
}

@Composable
private fun DomandaSceltaContenuto(
    domanda: DomandaScelta,
    indiceSelezionato: Int?,
    onSeleziona: (Int) -> Unit
) {
    Text(domanda.testo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
    domanda.opzioni.forEachIndexed { indice, opzione ->
        val statoRisposta = statoOpzione(indiceSelezionato, indice, domanda.indiceCorretto)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onSeleziona(indice) },
            colors = if (statoRisposta == StatoOpzione.NonSelezionata) {
                CardDefaults.cardColors()
            } else {
                CardDefaults.cardColors(containerColor = statoRisposta.coloreSfondo())
            }
        ) {
            Text(
                opzione,
                modifier = Modifier.padding(16.dp),
                color = statoRisposta.coloreTesto()
            )
        }
    }
}

@Composable
private fun DomandaVeroFalsoContenuto(
    domanda: DomandaVeroFalso,
    selezione: Boolean?,
    onSeleziona: (Boolean) -> Unit
) {
    Text(
        domanda.affermazione,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true, false).forEach { opzione ->
            val statoRisposta = statoOpzione(
                rispostaData = selezione,
                opzioneCorrente = opzione,
                corretta = domanda.corretta
            )
            Card(
                modifier = Modifier.clickable { onSeleziona(opzione) },
                colors = if (statoRisposta == StatoOpzione.NonSelezionata) {
                    CardDefaults.cardColors()
                } else {
                    CardDefaults.cardColors(containerColor = statoRisposta.coloreSfondo())
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (opzione) "Vero" else "Falso",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    color = statoRisposta.coloreTesto(),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private enum class StatoOpzione { NonSelezionata, Corretta, Errata }

private fun statoOpzione(indiceSelezionato: Int?, indice: Int, indiceCorretto: Int): StatoOpzione =
    when {
        indiceSelezionato == null -> StatoOpzione.NonSelezionata
        indice == indiceCorretto -> StatoOpzione.Corretta
        indice == indiceSelezionato -> StatoOpzione.Errata
        else -> StatoOpzione.NonSelezionata
    }

private fun statoOpzione(rispostaData: Boolean?, opzioneCorrente: Boolean, corretta: Boolean): StatoOpzione =
    when {
        rispostaData == null -> StatoOpzione.NonSelezionata
        opzioneCorrente == corretta -> StatoOpzione.Corretta
        opzioneCorrente == rispostaData -> StatoOpzione.Errata
        else -> StatoOpzione.NonSelezionata
    }

private fun StatoOpzione.coloreSfondo(): Color = when (this) {
    StatoOpzione.NonSelezionata -> Color.Unspecified
    StatoOpzione.Corretta -> VerdeCorretto.copy(alpha = 0.18f)
    StatoOpzione.Errata -> RossoErrato.copy(alpha = 0.18f)
}

private fun StatoOpzione.coloreTesto(): Color = when (this) {
    StatoOpzione.NonSelezionata -> Color.Unspecified
    StatoOpzione.Corretta -> VerdeCorretto
    StatoOpzione.Errata -> RossoErrato
}
