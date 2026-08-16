# Il Menù del Mostro

Gioco Android (Kotlin + Jetpack Compose) pensato per bambini di 6/7 anni: si
compone un menù di 3 portate (Antipasto, Primo, Dolce), ognuna con il proprio
menù di piatti "normali" e piatti "folli" (schifezze mostruose), da servire a
4 commensali mostruosi — **Mostro, Mostra, Mostrina e Mostretta** — che ad
ogni manche hanno una richiesta diversa (es. "cibi rossi", "sono
vegetariano", "sono carnivoro", "mi piacciono le insalate"...).

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
2. Ad ogni manche arriva un commensale diverso (Mostro, Mostra, Mostrina o
   Mostretta) con una richiesta (es. "Sono VEGETARIANO, niente carne o
   pesce!" oppure "Oggi voglio solo CIBI ROSSI!").
3. Si scorre la schermata e si sceglie **un piatto per ciascuna delle 3
   sezioni** (Antipasto, Primo, Dolce): ogni sezione ha il proprio menù di 8
   piatti, mescolando piatti normali e schifezze (contrassegnate con 🤪).
   Toccando di nuovo il piatto già scelto lo si deseleziona per cambiare
   idea.
4. Con **"Servi il pasto!"** si scopre il punteggio della manche, da 0 a 100:
   è la percentuale dei 3 piatti scelti che soddisfa la richiesta del
   commensale (0, 33, 67 o 100). Una schifezza non è automaticamente
   "sbagliata": a volte è proprio quello che il commensale vuole (es. una
   schifezza rossa soddisfa "cibi rossi" tanto quanto un piatto normale).
5. Dopo 4 commensali serviti si arriva alla schermata finale con il
   punteggio totale su 400, e si può ricominciare con **"Gioca ancora"**.

## File principali

- **`FoodData.kt`** — i tre menù (antipasti/primi/dolci) con le loro
  categorie, l'elenco dei 4 commensali e le richieste possibili (ognuna con
  la propria funzione di valutazione del pasto).
- **`GameLogic.kt`** — calcolo del punteggio (0-100) ed estrazione casuale di
  richieste e commensali per la partita.
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

- Suoni ed effetti sonori quando il commensale mangia.
- Più richieste e più piatti per portata, per aumentare la varietà.
- Animazioni di masticazione/coriandoli quando si ottengono 100 punti.
