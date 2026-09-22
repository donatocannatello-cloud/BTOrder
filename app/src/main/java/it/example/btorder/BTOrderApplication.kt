package it.example.btorder

import android.app.Application
import android.util.Log

/**
 * Intercetta ogni eccezione non gestita PRIMA che il sistema termini il processo, così da
 * poterne salvare la traccia con [RegistroCrash]: senza accesso al logcat del dispositivo
 * dell'utente, era impossibile capire il motivo reale di un crash all'avvio riportato "a
 * parole". Non sostituisce la gestione di sistema (viene comunque richiamata subito dopo),
 * quindi il comportamento del crash stesso non cambia: cambia solo che la prossima apertura
 * dell'app potrà mostrarne il messaggio.
 */
class BTOrderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val gestoreDiSistema = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                RegistroCrash.salva(this, Log.getStackTraceString(throwable))
            } catch (e: Exception) {
                // Non deve mai impedire la normale gestione del crash da parte del sistema.
            }
            gestoreDiSistema?.uncaughtException(thread, throwable)
        }
    }
}
