package it.example.ripassofoto.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import it.example.ripassofoto.camera.ottieniCameraProvider
import java.io.File

@Composable
fun SchermataFotocamera(
    onFotoScattata: (File) -> Unit,
    onAnnulla: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permessoConcesso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcherPermesso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concesso -> permessoConcesso = concesso }

    LaunchedEffect(Unit) {
        if (!permessoConcesso) launcherPermesso.launch(Manifest.permission.CAMERA)
    }

    if (!permessoConcesso) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                "Per fotografare la pagina serve il permesso della fotocamera.",
                style = MaterialTheme.typography.bodyLarge
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Button(onClick = { launcherPermesso.launch(Manifest.permission.CAMERA) }) {
                Text("Concedi il permesso")
            }
            Button(onClick = onAnnulla) { Text("Annulla") }
        }
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProvider = context.ottieniCameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onAnnulla,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Annulla", tint = Color.White)
        }

        FloatingActionButton(
            onClick = {
                val cartella = File(context.filesDir, "pagine").apply { mkdirs() }
                val file = File(cartella, "pagina_${System.currentTimeMillis()}.jpg")
                val opzioniOutput = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    opzioniOutput,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onFotoScattata(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // Lo scatto non è riuscito: l'utente rimane sulla schermata e può riprovare.
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Scatta foto")
        }
    }
}
