package it.example.ripassofoto.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SchemaScuro = darkColorScheme(
    primary = VerdeChiaroSecondario,
    secondary = AmbraAccento
)

private val SchemaChiaro = lightColorScheme(
    primary = VerdeScuroPrimario,
    secondary = AmbraAccento,
    background = GrigioSfondo
)

@Composable
fun RipassoFotoTheme(
    usaColoriDinamici: Boolean = false,
    contenuto: @Composable () -> Unit
) {
    val temaScuro = isSystemInDarkTheme()
    val schemaColori = when {
        usaColoriDinamici && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contesto = LocalContext.current
            if (temaScuro) dynamicDarkColorScheme(contesto) else dynamicLightColorScheme(contesto)
        }
        temaScuro -> SchemaScuro
        else -> SchemaChiaro
    }

    MaterialTheme(
        colorScheme = schemaColori,
        typography = Typography,
        content = contenuto
    )
}
