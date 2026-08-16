# Il Menù del Mostro

Gioco Android (Kotlin + Jetpack Compose) pensato per bambini di 6/7 anni: si
compongono i pasti scegliendo tra piatti "normali" e piatti "folli"
(schifezze mostruose) da servire a 4 commensali mostruosi — **Mostro,
Mostra, Mostrina e Mostretta** — che ad ogni manche hanno una richiesta
diversa (es. "cibi rossi", "niente carne o pesce", "mi piace l'insalata"...).
Tutti i testi sono in MAIUSCOLO e con parole semplici, pensati per essere
letti da bambini che stanno imparando a leggere.

Il gioco ha **6 livelli di difficoltà**, scelti prima di iniziare: più si
sale, più portate bisogna comporre per ogni commensale.

| Livello | Portate da comporre |
| --- | --- |
| 1 | Primo |
| 2 | Primo + Secondo |
| 3 | Primo + Secondo + Bibita |
| 4 | Primo + Secondo + Bibita + Pozione Magica |
| 5 | Primo + Secondo + Bibita + Pozione Magica + Dolce |
| 6 | Primo + Secondo + Bibita + Pozione Magica + Dolce + Caffè |

Nota: al posto di "amari" e "vino" (alcolici, non adatti a un gioco per
bambini) ci sono **pozioni magiche** dei mostri e bibite analcoliche
(acqua, succhi, aranciata...).

Questo è un progetto Gradle **completamente indipendente** da ChiamateBT
(nella cartella `../app`): ha proprio `settings.gradle.kts`, proprio wrapper
Gradle e proprio `applicationId` (`it.example.menumostro`), così i due
progetti non condividono nulla e possono essere aperti/compilati
separatamente.

- **Package**: `it.example.menumostro`
- **minSdk**: 26 — **targetSdk / compileSdk**: 34
- Nessun permesso richiesto: il gioco non usa rete, Bluetooth né telefono.

## Come si gioca

1. Nella schermata iniziale si preme **"Gioca!"**, poi si sceglie un
   **livello** (1-6): ogni riga mostra quante e quali portate servono.
2. Ad ogni manche arriva un commensale diverso (Mostro, Mostra, Mostrina o
   Mostretta) con una richiesta (es. "NIENTE CARNE E NIENTE PESCE!" oppure
   "VOGLIO SOLO CIBI ROSSI!").
3. Si scorre la schermata e si sceglie **un piatto per ciascuna portata del
   livello** (da 1 a 6 a seconda del livello scelto): ogni portata ha il
   proprio menù di 8 piatti, mescolando piatti normali e schifezze
   (contrassegnate con 🤪). Toccando di nuovo il piatto già scelto lo si
   deseleziona per cambiare idea.
4. Con **"Dai da mangiare!"** si scopre il punteggio della manche, da 0 a
   100: è la percentuale dei piatti scelti che soddisfa la richiesta del
   commensale. Una schifezza non è automaticamente "sbagliata": a volte è
   proprio quello che il commensale vuole (es. una schifezza rossa soddisfa
   "cibi rossi" tanto quanto un piatto normale).
5. Dopo 4 commensali serviti si arriva alla schermata finale con il
   punteggio totale su 400: si può rigiocare lo stesso livello con **"Gioca
   ancora"** oppure tornare alla scelta del livello con **"Cambia
   livello"**.

## File principali

- **`FoodData.kt`** — le 6 portate e i loro menù (con le categorie usate
  dalle richieste), i 6 livelli di difficoltà, l'elenco dei 4 commensali e
  le richieste possibili (ognuna con la propria funzione di valutazione del
  pasto).
- **`GameLogic.kt`** — calcolo del punteggio (0-100) ed estrazione casuale di
  richieste e commensali per la partita.
- **`MainActivity.kt`** — tutte le schermate Compose (Home, Livelli, Gioco,
  Fine) e lo stato della partita.
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
