# Free Games

App Android (Kotlin + Jetpack Compose) pensata per bambini di 6/7 anni: è
una **suite di giochi**. All'avvio si apre una home ("Free Games") da cui si
sceglie a quale gioco giocare; per ora ci sono **Monster Restaurant** e
**Monster Panino**, gli altri sono caselle "presto disponibile" (vedi [Idee
per estensioni future](#idee-per-estensioni-future)). Tutti i testi sono in
MAIUSCOLO e con parole semplici, pensati per essere letti da bambini che
stanno imparando a leggere.

## Monster Restaurant

Si compongono i pasti scegliendo tra piatti "normali" e piatti "folli"
(schifezze mostruose) da servire a 4 commensali mostruosi — **Mostro,
Mostra, Mostrina e Mostretta** — che ad ogni manche hanno una richiesta
diversa (es. "cibi rossi", "niente carne o pesce", "mi piace l'insalata"...).

Il gioco ha **6 livelli di difficoltà** ed è sequenziale: si parte sempre
dal livello 1 e, completandolo, si sale automaticamente al successivo (non
c'è una schermata di scelta del livello). Più si sale, più portate bisogna
comporre per ogni commensale.

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

### Come si gioca

1. Dalla home "Free Games" si tocca la casella **"Monster Restaurant"**.
2. Nella schermata iniziale del gioco (con l'illustrazione della taverna dei
   mostri come sfondo) si preme **"Gioca!"**: si parte sempre dal **livello
   1**. La freccia ⬅️ in alto a sinistra torna alla home "Free Games".
3. Ad ogni manche arriva un commensale diverso (Mostro, Mostra, Mostrina o
   Mostretta) con una richiesta (es. "NIENTE CARNE E NIENTE PESCE!" oppure
   "VOGLIO SOLO CIBI ROSSI!").
4. Si scorre la schermata e si sceglie **un piatto per ciascuna portata del
   livello** (da 1 a 6 a seconda del livello raggiunto): ogni portata ha il
   proprio menù di 8 piatti, mescolando piatti normali e schifezze
   (contrassegnate con 🤪). Toccando di nuovo il piatto già scelto lo si
   deseleziona per cambiare idea.
5. Con **"Dai da mangiare!"** si scopre il punteggio della manche, da 0 a
   100: è la percentuale dei piatti scelti che soddisfa la richiesta del
   commensale. Una schifezza non è automaticamente "sbagliata": a volte è
   proprio quello che il commensale vuole (es. una schifezza rossa soddisfa
   "cibi rossi" tanto quanto un piatto normale).
6. Dopo 4 commensali serviti si arriva alla schermata di fine livello: con
   **"Prossimo livello"** si sale automaticamente al livello successivo
   (una portata in più). Dopo aver completato il livello 6 compare invece
   **"Nuova partita"**, che ricomincia da capo dal livello 1.

## Monster Panino

Stesso principio di Monster Restaurant, applicato a un panino: si scelgono
ingredienti (normali o folli 🤪) da un unico banco, invece di comporre più
portate separate, per accontentare la richiesta del commensale di turno
(es. "voglio solo cibi rossi!" → tutti ingredienti rossi). Anche qui **5
livelli di difficoltà sequenziali**: si parte da un panino di 2 ingredienti
e via via se ne aggiunge uno in più, fino a 6.

Riusa quasi tutto da Monster Restaurant (stessi 4 commensali, stesse 10
richieste, stesso calcolo del punteggio 0-100): solo il banco ingredienti e
il modo di comporli sono diversi, così i due giochi restano coerenti tra
loro pur essendo distinti.

### Come si gioca

1. Dalla home "Free Games" si tocca la casella **"Monster Panino"**.
2. Si preme **"Gioca!"**: si parte sempre dal **livello 1** (2
   ingredienti). La freccia ⬅️ in alto a sinistra torna alla home.
3. Arriva un commensale con una richiesta, esattamente come in Monster
   Restaurant.
4. Si toccano gli ingredienti dal banco (unico, non diviso in portate) fino
   a riempire tutti gli "slot" del panino per il livello corrente; toccando
   di nuovo un ingrediente già scelto lo si toglie. Gli ingredienti non
   selezionabili (perché il panino è già pieno) appaiono in grigio.
5. Con **"Fai il panino!"** si scopre il punteggio della manche (0-100), poi
   si passa al prossimo commensale.
6. Dopo 4 commensali si sale automaticamente di livello (un ingrediente in
   più), fino al livello 5; poi si può ricominciare con "Nuova partita".

## File principali

- **`MainActivity.kt`** — contiene la suite (`AppSuite`, `SchermataHub` e
  l'elenco `elencoGiochi` dei giochi disponibili/in arrivo), tutte le
  schermate Compose di Monster Restaurant (Home, Gioco, Fine) e, in fondo al
  file, quelle di Monster Panino (`AppMonsterPanino` e schermate correlate).
  Monster Panino riusa direttamente `Piatto`, `Commensale`, `RichiestaMostro`,
  `CartaRichiesta`, `CartaPiattoMenu` e `RisultatoOverlay` già definiti per
  Monster Restaurant. Un gioco futuro andrà aggiunto qui come un nuovo ramo
  del `when` in `AppSuite`, idealmente spostando ogni gioco nel proprio file
  dedicato quando la suite cresce ancora.
- **`FoodData.kt`** — dati di entrambi i giochi: per Monster Restaurant le 6
  portate e i loro menù, i 6 livelli di difficoltà; per Monster Panino il
  banco ingredienti (`elencoIngredientiPanino`) e i suoi 5 livelli
  (`elencoLivelliPanino`); condivisi da entrambi l'elenco dei 4 commensali e
  le 10 richieste possibili (ognuna con la propria funzione di valutazione).
- **`GameLogic.kt`** — calcolo del punteggio (0-100) ed estrazione casuale di
  richieste e commensali, usato da entrambi i giochi.
- **`ui/theme/`** — tema con palette allegra e colorata fissa, pensata per
  bambini (non segue il tema scuro di sistema), condiviso da tutta la suite.
- **`res/drawable-nodpi/sfondo_taverna_mostri.png`** — illustrazione usata
  come sfondo della schermata iniziale di Monster Restaurant.

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

Prossimi giochi da aggiungere alla suite (già presenti come caselle "presto
disponibile" nella home, non ancora implementati):

- **Memory dei Mostri** — il classico gioco delle coppie, con carte a tema
  mostro: alleno la memoria, meccanica semplice (griglia di carte che si
  girano) e già coerente con lo stile grafico esistente.
- **Vesti il Mostro** — si veste un mostro (cappello, occhi, bocca,
  accessori) per accontentare una richiesta del cliente, stessa meccanica di
  "richiesta + scelta che matcha" già usata in Monster Restaurant ma con un
  risultato visivo invece che un punteggio.
- **Ritmo Mostruoso** — un "Simon Says" con pulsanti colorati a tema mostro:
  si ripete una sequenza sempre più lunga, buon esercizio di memoria a breve
  termine.
- **Conta i Mostri** — mini-gioco di conteggio/matematica per bambini
  piccoli: quanti mostri di un certo tipo compaiono a schermo, si tocca il
  numero giusto.

Altre idee per Monster Restaurant e per la suite in generale:

- Salvare la partita in corso (livello e punteggio) per poterla riprendere
  riaprendo l'app, con la home "Free Games" che offra "Continua" oltre a
  scegliere un gioco nuovo.
- Suoni ed effetti sonori quando il commensale mangia.
- Più richieste e più piatti per portata, per aumentare la varietà.
- Animazioni di masticazione/coriandoli quando si ottengono 100 punti.
