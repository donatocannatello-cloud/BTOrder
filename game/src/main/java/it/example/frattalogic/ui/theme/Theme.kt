package it.example.frattalogic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SchemaGioco = darkColorScheme(
    primary = AccentoTeal,
    secondary = AccentoAmbra,
    tertiary = AccentoViola,
    background = SfondoProfondo,
    surface = SfondoPannello,
    onBackground = TestoChiaro,
    onSurface = TestoChiaro,
    error = Sbagliato
)

@Composable
fun FrattaLogicTheme(contenuto: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SchemaGioco,
        typography = Typography,
        content = contenuto
    )
}
