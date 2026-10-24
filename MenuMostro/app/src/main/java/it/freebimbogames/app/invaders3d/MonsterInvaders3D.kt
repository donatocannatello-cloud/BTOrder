package it.freebimbogames.app.invaders3d

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import it.freebimbogames.app.BottoneTornaAiGiochi
import it.freebimbogames.app.R
import it.freebimbogames.app.maiuscolo

/**
 * Gioco 3D vero e proprio (OpenGL ES 2.0), non Compose: GLView + HudView vengono incapsulati
 * in un AndroidView, montato solo dopo la schermata iniziale Compose (stesso schema delle
 * altre schermate "home" della suite). Il ciclo di vita va replicato due volte: quello di
 * Compose (si entra/esce da questo schermo dentro la stessa Activity, senza onResume/onPause
 * reali) e quello reale dell'Activity (l'app va in background), perché GLSurfaceView richiede
 * onResume/onPause accoppiati correttamente per il suo contesto EGL in entrambi i casi.
 */
@Composable
fun AppMonsterInvaders3D(onTornaAiGiochi: () -> Unit) {
    var avviato by remember { mutableStateOf(false) }

    if (!avviato) {
        SchermataHomeInvaders3D(onGioca = { avviato = true }, onTornaAiGiochi = onTornaAiGiochi)
    } else {
        SchermataGiocoInvaders3D(onTornaAiGiochi = onTornaAiGiochi)
    }
}

@Composable
private fun SchermataHomeInvaders3D(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.copertina_invasori_mostri),
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
                text = "Monster Invaders 3D".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Vola fra gli alieni e salva la galassia!".maiuscolo(),
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
private fun SchermataGiocoInvaders3D(onTornaAiGiochi: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val glView = remember { GLView(context) }
    val hudView = remember { HudView(context, glView.game, glView) }

    val attivita = context as? Activity
    DisposableEffect(attivita) {
        attivita?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { attivita?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glView.onResumeView()
                    hudView.startTicking()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    hudView.stopTicking()
                    glView.onPauseView()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hudView.stopTicking()
            glView.onPauseView()
            glView.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                FrameLayout(context).apply {
                    addView(
                        glView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    )
                    addView(
                        hudView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            sfondo = Color(0x99000000),
        )
    }
}
