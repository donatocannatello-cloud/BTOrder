package it.freebimbogames.app.invaders3d

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import it.freebimbogames.app.BottoneTornaAiGiochi

/**
 * Gioco 3D vero e proprio (OpenGL ES 2.0), non Compose: GLView + HudView vengono incapsulati
 * in un AndroidView. Il ciclo di vita va replicato due volte: quello di Compose (si entra/esce
 * da questo schermo dentro la stessa Activity, senza onResume/onPause reali) e quello reale
 * dell'Activity (l'app va in background), perché GLSurfaceView richiede onResume/onPause
 * accoppiati correttamente per il suo contesto EGL in entrambi i casi.
 */
@Composable
fun AppMonsterInvaders3D(onTornaAiGiochi: () -> Unit) {
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
