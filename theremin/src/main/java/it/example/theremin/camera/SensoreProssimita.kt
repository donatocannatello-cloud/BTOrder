package it.example.theremin.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Sensore di prossimità (quello che spegne lo schermo durante le chiamate), usato come
 * interruttore: sulla quasi totalità dei telefoni distingue solo "vicino" (entro ~5 cm) e
 * "lontano", quindi non misura la posizione ma è ottimo per zittire il suono all'istante.
 */
class SensoreProssimita(context: Context, private val suCambio: (vicino: Boolean) -> Unit) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensore: Sensor? = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var attivo = false

    val disponibile: Boolean get() = sensore != null

    fun attiva(acceso: Boolean) {
        val s = sensore ?: return
        if (acceso && !attivo) {
            manager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST)
        } else if (!acceso && attivo) {
            manager.unregisterListener(this)
            suCambio(false)
        }
        attivo = acceso
    }

    override fun onSensorChanged(event: SensorEvent) {
        val s = sensore ?: return
        // "Vicino" se sotto la portata massima (sensori binari) e comunque entro 5 cm
        val vicino = event.values[0] < minOf(5f, s.maximumRange)
        suCambio(vicino)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
