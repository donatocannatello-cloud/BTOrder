package it.example.frattalogic.engine

enum class Fase { IMMERSIONE, EVENTO_BONUS }

/**
 * Stato della discesa: [profondita] è una misura continua di quanto si è
 * avanzati nel tunnel frattale (cresce solo mentre il joystick di discesa è
 * azionato — a riposo resta ferma), [offsetX]/[offsetY] lo spostamento
 * laterale del punto di fuga impostato dal joystick di direzione.
 */
data class ImmersioneState(
    val profondita: Double = 0.0,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val punteggio: Int = 0,
    val fase: Fase = Fase.IMMERSIONE,
    val cameraBonus: Camera? = null,
    val indiceSelezionatoBonus: Int? = null,
    val esitoBonus: Esito = Esito.NESSUNO
)
