# Free Games

App Android (Kotlin + Jetpack Compose) pensata per bambini di 6/7 anni: è
una **suite di giochi**. All'avvio si apre una home ("Free Games") da cui si
sceglie a quale gioco giocare; per ora ci sono **Monster Restaurant**,
**Monster Panino** e **Monster Parking**, gli altri sono caselle "presto
disponibile" (vedi [Idee per estensioni future](#idee-per-estensioni-future)).
Tutti i testi sono in MAIUSCOLO e con parole semplici, pensati per essere
letti da bambini che stanno imparando a leggere.

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

Le due fette di pane ci sono sempre (sopra e sotto) ma **non sono un
ingrediente scelto**: non compaiono nel banco e non contano nel
quantitativo del livello, sono solo la "confezione" del panino.

La schermata di gioco è divisa in due colonne: a sinistra il banco
ingredienti da toccare, a destra il panino che si costruisce **in
verticale** (pane, ingredienti impilati uno sull'altro nell'ordine in cui
sono stati scelti, pane), così si vede subito come viene il panino finale.

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
4. Si toccano gli ingredienti dal banco a sinistra fino a riempire tutti
   gli "slot" del panino per il livello corrente; toccando di nuovo un
   ingrediente già scelto lo si toglie. Gli ingredienti non selezionabili
   (perché il panino è già pieno) appaiono in grigio. A destra si vede il
   panino crescere ingrediente dopo ingrediente, tra le due fette di pane.
5. Con **"Fai il panino!"** si scopre il punteggio della manche (0-100), poi
   si passa al prossimo commensale.
6. Dopo 4 commensali si sale automaticamente di livello (un ingrediente in
   più), fino al livello 5; poi si può ricominciare con "Nuova partita".

## Monster Parking

Un puzzle logico, una via di mezzo tra il gioco del 15 e Rush Hour: in un
parcheggio selvaggio, su una griglia quadrata, alcune auto bloccano la
strada alla **macchina rossa**. Ogni auto si muove solo avanti e indietro
lungo il proprio orientamento (mai di lato): bisogna spostare le auto
giuste per aprire un corridoio e far uscire la macchina rossa dal bordo
destro della griglia.

**3 livelli sequenziali**, con griglia via via più grande e più auto da
spostare:

| Livello | Griglia | Auto totali |
| --- | --- | --- |
| 1 | 6×6 | 6 |
| 2 | 7×7 | 8 |
| 3 | 8×8 | 9 |

Ogni parcheggio è costruito a mano partendo dalla soluzione e "scomponendolo"
all'indietro con mosse valide, quindi è sempre risolvibile con spostamenti
singoli e diretti (nessuna manovra a incastro nascosta).

### Come si gioca

1. Dalla home "Free Games" si tocca la casella **"Monster Parking"**, poi
   **"Gioca!"**. La freccia ⬅️ in alto a sinistra torna alla home.
2. Si tocca un'auto per selezionarla (appare un bordo nero); compaiono due
   pulsanti freccia per muoverla di una cella alla volta nella sua unica
   direzione possibile (◀️▶️ se orizzontale, 🔼🔽 se verticale). Una mossa
   verso una cella occupata o fuori dalla griglia non fa nulla.
3. La bandiera 🏁 sul bordo destro segna dove deve arrivare la macchina
   rossa 🚗 (le altre auto sono 🚙).
4. Appena la macchina rossa raggiunge il bordo, il livello è risolto: si
   passa automaticamente al livello successivo (griglia più grande). Dopo il
   livello 3 si può ricominciare con "Nuova partita".

A differenza di Monster Restaurant e Monster Panino non c'è un punteggio:
si mostra solo il numero di mosse usate, come sfida personale a risolverlo
nel minor numero di tocchi possibile.

## File principali

- **`MainActivity.kt`** — contiene la suite (`AppSuite`, `SchermataHub`,
  l'enum `Gioco` e l'elenco `elencoGiochi` dei giochi disponibili/in
  arrivo), l'estensione `String.maiuscolo()` condivisa da tutta la suite, e
  tutte le schermate Compose di Monster Restaurant e Monster Panino (questi
  due giochi condividono `Piatto`, `Commensale`, `RichiestaMostro`,
  `CartaRichiesta`, `CartaPiattoMenu`, `RisultatoOverlay`, quindi per ora
  restano nello stesso file). Un gioco futuro andrà aggiunto come un nuovo
  ramo del `when` in `AppSuite`; se non condivide meccaniche con i giochi
  esistenti conviene dargli subito un file proprio, come fatto per Monster
  Parking.
- **`FoodData.kt`** — dati di Monster Restaurant e Monster Panino: per il
  primo le 6 portate e i loro menù, i 6 livelli di difficoltà; per il
  secondo il banco ingredienti (`elencoIngredientiPanino`) e i suoi 5
  livelli (`elencoLivelliPanino`); condivisi da entrambi l'elenco dei 4
  commensali e le 10 richieste possibili (ognuna con la propria funzione di
  valutazione).
- **`GameLogic.kt`** — calcolo del punteggio (0-100) ed estrazione casuale di
  richieste e commensali, usato da Monster Restaurant e Monster Panino.
- **`ParkingData.kt`** — modello dati e logica pura di Monster Parking:
  `Auto`, `Orientamento`, la funzione `provaSpostamento` (unica fonte di
  verità su quali mosse sono valide) e i 3 `LivelloParcheggio` con le auto
  già posizionate.
- **`ParkingGame.kt`** — le schermate Compose di Monster Parking
  (`AppMonsterParking`, Home, Gioco con la griglia disegnata cella per
  cella, Fine), completamente separato dagli altri due giochi.
- **`ui/theme/`** — tema con palette allegra e colorata fissa, pensata per
  bambini (non segue il tema scuro di sistema), condiviso da tutta la suite.
- **`res/drawable-nodpi/sfondo_taverna_mostri.png`** — illustrazione usata
  come sfondo della schermata iniziale di Monster Restaurant.
- **`res/drawable-nodpi/sfondo_panino_mostri.jpg`** — illustrazione usata
  come sfondo della schermata iniziale di Monster Panino.

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
