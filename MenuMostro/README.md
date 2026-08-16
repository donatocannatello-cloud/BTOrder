# Il Menù del Mostro

Gioco Android (Kotlin + Jetpack Compose) pensato per bambini di 6/7 anni: si
compone un menù di 3 portate (Antipasto, Primo, Dolce) scegliendo tra piatti
"normali" e piatti "folli" da servire a **Papà Mostro**, che ad ogni turno ha
voglie diverse (dolce, salato, piccante, verde, pazzo...).

Questo è un progetto Gradle **completamente indipendente** da ChiamateBT
(nella cartella `../app`): ha proprio `settings.gradle.kts`, proprio wrapper
Gradle e proprio `applicationId` (`it.example.menumostro`), così i due
progetti non condividono nulla e possono essere aperti/compilati
separatamente.

- **Package**: `it.example.menumostro`
- **minSdk**: 26 — **targetSdk / compileSdk**: 34
- Nessun permesso richiesto: il gioco non usa rete, Bluetooth né telefono.

## Come si gioca

1. Nella schermata iniziale si preme **"Gioca!"**.
2. Ad ogni turno il Mostro esprime una voglia (es. "Voglio qualcosa di
   CROCCANTE e SALATO!").
3. Si toccano 3 piatti dalla griglia colorata in basso per riempire i tre
   cerchi del menù (Antipasto/Primo/Dolce). Toccando un cerchio già pieno lo
   si svuota per cambiare idea.
4. Con **"Servi al Mostro!"** si scopre quante stelle (1-3) si sono
   guadagnate, in base a quanti piatti scelti soddisfano la voglia del
   Mostro. Il minimo è sempre 1 stella: anche il tentativo più stravagante
   viene premiato, per restare divertente e mai frustrante.
5. Dopo 6 clienti serviti si arriva alla schermata finale con il punteggio
   totale, e si può ricominciare con **"Gioca ancora"**.

## File principali

- **`FoodData.kt`** — elenco dei piatti (con le loro categorie) e delle
  possibili richieste del Mostro.
- **`GameLogic.kt`** — calcolo delle stelle e scelta della prossima richiesta.
- **`MainActivity.kt`** — tutte le schermate Compose (Home, Gioco, Fine) e lo
  stato della partita.
- **`ui/theme/`** — tema con palette allegra e colorata fissa, pensata per
  bambini (non segue il tema scuro di sistema).

## Come compilare

```bash
cd MenuMostro
./gradlew assembleDebug
```

L'APK di debug viene generato in `app/build/outputs/apk/debug/`.

### Nota sulla verifica automatica della build in questo ambiente

Come già documentato nel README principale del repository, in questo
ambiente di sviluppo la rete verso `dl.google.com` / `maven.google.com` è
bloccata dal proxy di sandbox, quindi Gradle non riesce a scaricare
l'Android Gradle Plugin né le librerie AndroidX/Compose. La build non è
stata quindi verificata end-to-end qui: va compilata con Android Studio o
una CI con accesso al repository Maven di Google.

## Idee per estensioni future

- Suoni ed effetti sonori quando il Mostro mangia.
- Più clienti mostro con personalità diverse (non solo Papà Mostro).
- Animazioni di masticazione/coriandoli quando si ottengono 3 stelle.
