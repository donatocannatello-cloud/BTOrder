package it.example.ripassofoto.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Estrae il testo da una foto tramite ML Kit Text Recognition, che gira interamente
 * sul dispositivo (nessun dato lascia il telefono). Il modello viene scaricato da
 * Google Play Services al primo utilizzo; dopodiché funziona anche offline.
 */
object RiconoscimentoTesto {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun riconosciDaFile(context: Context, file: File): String =
        suspendCancellableCoroutine { continuazione ->
            val immagine = InputImage.fromFilePath(context, Uri.fromFile(file))
            recognizer.process(immagine)
                .addOnSuccessListener { testoRiconosciuto ->
                    continuazione.resume(testoRiconosciuto.text)
                }
                .addOnFailureListener { errore ->
                    continuazione.resumeWithException(errore)
                }
        }
}
