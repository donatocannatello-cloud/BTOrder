package it.example.ripassofoto.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Ottiene il [ProcessCameraProvider] senza bloccare il thread principale. */
suspend fun Context.ottieniCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuazione ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { continuazione.resume(future.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }
