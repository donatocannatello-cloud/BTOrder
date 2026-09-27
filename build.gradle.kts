// File di build a livello di progetto: qui vengono dichiarati solo i plugin,
// la loro applicazione effettiva avviene nel modulo app.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Da Kotlin 2.0 il compilatore di Compose è un plugin Kotlin
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
}
