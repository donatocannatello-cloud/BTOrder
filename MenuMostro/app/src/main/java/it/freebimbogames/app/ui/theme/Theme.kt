package it.freebimbogames.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette fissa e sempre chiara e colorata: per un gioco pensato per bambini
// preferiamo un aspetto allegro costante, senza adattarci al tema scuro di sistema.
private val SchemaColoriMostro = lightColorScheme(
    primary = ViolaMostro,
    onPrimary = Color.White,
    secondary = RosaMostro,
    tertiary = GialloMostro,
    background = SfondoChiaro,
    surface = Color.White
)

@Composable
fun MenuMostroTheme(contenuto: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SchemaColoriMostro,
        typography = Typography,
        content = contenuto
    )
}
