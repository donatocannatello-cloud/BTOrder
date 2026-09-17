package it.example.btorder.ui.theme

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
    primary = VerdeSecondario,
    secondary = VerdePrimario
)

private val SchemaChiaro = lightColorScheme(
    primary = VerdePrimario,
    secondary = VerdeSecondario,
    background = GrigioSfondo
)

@Composable
fun BTOrderTheme(
    usaColoriDinamici: Boolean = true,
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
