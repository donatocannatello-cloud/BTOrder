# Free Bimbo Games

App Android (Kotlin + Jetpack Compose) pensata per bambini di 6/7 anni: è
una **suite di giochi**. All'avvio si apre una home ("Free Bimbo Games") da
cui si sceglie a quale gioco giocare: **Monster Restaurant**, **Monster
Panino**, **Monster Parking**, **Memory dei Mostri**, **Vesti il Mostro**,
**Ritmo Mostruoso**, **Spara ai Mostri** e **Il Mostro Cerca**. Tutti i
testi sono in MAIUSCOLO e con parole semplici,
pensati per essere letti da bambini che stanno imparando a leggere. Ogni
schermata di ogni gioco — non solo la home, ma anche partita in corso e
fine partita — ha una freccia ⬅️ in alto a sinistra che torna subito alla
home "Free Bimbo Games": si può sempre uscire da un minigioco senza dover
prima finire la manche o il livello. Ogni tocco, risposta giusta/sbagliata
e fine livello ha anche un piccolo suono di riscontro (un tocco secco per
le selezioni, un arpeggio per i successi, un suono morbido per gli errori,
una fanfara per fine livello/partita), per dare un feedback immediato
anche a chi non legge ancora bene.

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
Gradle e proprio `applicationId` (`it.freebimbogames.app`), così i due
progetti non condividono nulla e possono essere aperti/compilati
separatamente.

- **Package**: `it.freebimbogames.app`
- **minSdk**: 26 — **targetSdk / compileSdk**: 34
- Nessun permesso richiesto: il gioco non usa rete, Bluetooth né telefono.

### Come si gioca

1. Dalla home "Free Bimbo Games" si tocca la casella **"Monster Restaurant"**.
2. Nella schermata iniziale del gioco (con l'illustrazione della taverna dei
   mostri come sfondo) si preme **"Gioca!"**: si parte sempre dal **livello
   1**. La freccia ⬅️ in alto a sinistra torna alla home "Free Bimbo Games".
3. Ad ogni manche arriva un commensale diverso (Mostro, Mostra, Mostrina o
   Mostretta) con una richiesta (es. "NIENTE CARNE E NIENTE PESCE!" oppure
   "VOGLIO SOLO CIBI ROSSI!").
4. Si scorre la schermata e si sceglie **un piatto per ciascuna portata del
   livello** (da 1 a 6 a seconda del livello raggiunto): ogni portata mescola
   piatti normali e schifezze, senza un'icona a distinguerle (bisogna
   guardare nome ed emoji per capire di cosa si tratta). Toccando di nuovo
   il piatto già scelto lo si deseleziona per cambiare idea.
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

1. Dalla home "Free Bimbo Games" si tocca la casella **"Monster Panino"**.
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

**20 livelli sequenziali**, con griglia via via più grande e sempre più auto
da spostare:

| Livelli | Griglia | Auto totali |
| --- | --- | --- |
| 1 | 6×6 | 6 |
| 2 | 7×7 | 8 |
| 3–5 | 8×8 | 9–11 |
| 6–10 | 9×9 | 12–16 |
| 11–20 | 10×10 | 14–21 |

I livelli 1-3 sono stati pensati e verificati a mano. Dal livello 4 in poi
sono generati partendo dall'auto rossa già "risolta" (attaccata al bordo
destro) e mescolando la griglia con una sequenza di mosse singole valide:
risolvere il livello equivale a ripercorrere quella sequenza al contrario,
il che garantisce per costruzione che ogni livello sia risolvibile — e ogni
livello generato è stato anche verificato in modo indipendente (rigiocando
la sequenza di soluzione fino a controllare che la macchina rossa esca
davvero dal bordo).

### Come si gioca

1. Dalla home "Free Bimbo Games" si tocca la casella **"Monster Parking"**, poi
   **"Gioca!"**. La freccia ⬅️ in alto a sinistra torna alla home.
2. Si tocca un'auto per selezionarla (appare un bordo nero); compaiono due
   pulsanti freccia per muoverla di una cella alla volta nella sua unica
   direzione possibile (◀️▶️ se orizzontale, 🔼🔽 se verticale). Una mossa
   verso una cella occupata o fuori dalla griglia non fa nulla.
3. La bandiera 🏁 sul bordo destro segna dove deve arrivare la macchina
   rossa 🚗 (le altre auto sono 🚙).
4. Appena la macchina rossa raggiunge il bordo, il livello è risolto: si
   passa automaticamente al livello successivo (griglia più grande o più
   auto). Dopo il livello 20 si può ricominciare con "Nuova partita".

A differenza di Monster Restaurant e Monster Panino non c'è un punteggio:
si mostra solo il numero di mosse usate, come sfida personale a risolverlo
nel minor numero di tocchi possibile.

## Memory dei Mostri

Il classico gioco delle coppie: una griglia di carte a faccia in giù, tutte
con un simbolo mostruoso ripetuto due volte (👹👻🧌👽🎃🐙👾🦄🐲🦇🕷️🐉🧟🧛🦖🐍).
Si toccano due carte alla volta: se il simbolo combacia restano scoperte per
sempre, altrimenti dopo un attimo si girano di nuovo a faccia in giù e si
riprova.

**6 livelli sequenziali**, con sempre più coppie da trovare (da 4 a 15).

### Come si gioca

1. Dalla home si tocca **"Memory dei Mostri"**, poi **"Gioca!"**.
2. Si tocca una prima carta per scoprirla, poi una seconda: se sono uguali
   restano scoperte, altrimenti si rigirano dopo una breve pausa.
3. Ogni coppia tentata (giusta o sbagliata) conta come un tentativo, mostrato
   in alto: l'obiettivo è trovare tutte le coppie nel minor numero di
   tentativi possibile.
4. Trovate tutte le coppie si passa automaticamente al livello successivo
   (più coppie). Dopo il livello 6 si può ricominciare con "Nuova partita".

## Vesti il Mostro

Stesso concept di Monster Restaurant (una richiesta da soddisfare per fare
punti), applicato a un vestito invece che a un pasto: si scelgono cappello,
occhiali, vestito, scarpe e oggetto magico per accontentare il tema chiesto
dal mostro di turno (es. "vestimi da pirata!", "voglio essere tutto rosso!",
"voglio essere stravagante!" per un mostro che ama gli accessori più
assurdi). Un'anteprima in cima alla schermata mostra il mostro via via
vestito con gli accessori scelti.

**5 livelli sequenziali**: si parte scegliendo solo il cappello e si
aggiunge una categoria di accessori per volta, fino a vestirlo al completo.

Riusa `Piatto`, `Commensale`, `RichiestaMostro`, `CartaRichiesta`,
`CartaPiattoMenu` e `RisultatoOverlay` di Monster Restaurant: solo i banchi
di accessori e le richieste (temi da costume invece che categorie di cibo)
sono dedicati a questo gioco.

### Come si gioca

1. Dalla home si tocca **"Vesti il Mostro"**, poi **"Gioca!"**.
2. Arriva un commensale con un tema da rispettare, come negli altri giochi
   di richieste.
3. Si sceglie un accessorio per ciascuna categoria attiva nel livello
   corrente; toccando di nuovo l'accessorio scelto lo si toglie.
4. Con **"Vesti il mostro!"** si scopre il punteggio della manche (0-100).
5. Dopo 4 commensali si sale automaticamente di livello (una categoria di
   accessori in più), fino al livello 5; poi si può ricominciare con "Nuova
   partita".

## Ritmo Mostruoso

Un "Simon Says" con 4 tasti mostruosi colorati (👹👻🧌👽): il gioco mostra una
sequenza di tasti che si accendono uno alla volta, sempre più lunga, e il
giocatore deve ripeterla toccando i tasti nello stesso ordine.

A differenza degli altri giochi della suite **non ha livelli fissi**: la
sequenza si allunga di un passo ogni volta che viene ripetuta tutta giusta,
come nel gioco originale, ed è una sfida a mano libera invece che una lista
di livelli precotti — qui il "livello" è semplicemente quanto lontano si
arriva prima di sbagliare.

### Come si gioca

1. Dalla home si tocca **"Ritmo Mostruoso"**, poi **"Gioca!"**.
2. Si osserva la sequenza di mostri che si accendono uno alla volta.
3. Si toccano i 4 mostri nello stesso ordine mostrato: se si sbaglia anche
   un solo tocco, la partita finisce lì.
4. Se la sequenza viene ripetuta giusta fino in fondo, se ne aggiunge un
   passo e si ricomincia a guardare (sequenza più lunga di uno).
5. Alla fine si vede a che lunghezza si è arrivati e il record più alto
   raggiunto in questa sessione di gioco; si può riprovare subito con
   "Riprova".

## Spara ai Mostri

Un mini sparatutto da luna park, pensato per essere non violento: un razzo
in basso spara stelline verso l'alto per far scoppiare "palloncini mostro"
che scendono dal cielo, come un classico tiro a segno. Si tocca lo schermo
per muovere il razzo nel punto toccato e sparare una stellina in un solo
gesto — nessun controllo separato da imparare.

Come Ritmo Mostruoso **non ha livelli fissi**, ma la sfida cresce in 4
fasi via via che il punteggio sale (mostrate in alto come "Livello 1-4"):
velocità di caduta e frequenza dei mostri aumentano di continuo, e a certe
soglie di punteggio compaiono elementi nuovi:

| Livello | Punteggio | Cosa cambia |
| --- | --- | --- |
| 1 | 0-7 | Solo i classici mostri-palloncino da far scoppiare. |
| 2 | 8-15 | Compaiono anche amici travestiti da mostro (😇🐰🦋🐣👼): **non vanno colpiti**, o si perde una vita. |
| 3 | 16-29 | Compare anche un mostro speciale 🌟: colpirlo **raddoppia i punti** per qualche secondo. |
| 4 | 30+ | Tutto insieme, ancora più veloce e frequente. |

Gli amici travestiti che non vengono colpiti e arrivano in fondo allo
schermo non tolgono vite: l'unico modo di perderne una è colpirli per
sbaglio, oppure lasciar scappare un vero mostro.

### Come si gioca

1. Dalla home si tocca **"Spara ai Mostri"**, poi **"Gioca!"**.
2. Si tocca un punto dello schermo di gioco: il razzo 🚀 si sposta lì e
   spara subito una stellina ⭐ verso l'alto.
3. Ogni mostro colpito da una stellina scoppia (✨) e vale un punto; i cuori
   ❤️ in alto mostrano quante vite restano.
4. Attenzione agli amici travestiti (😇🐰🦋): colpirli per sbaglio (💥) fa
   perdere una vita invece di fare punti. Il mostro speciale 🌟, se
   colpito, attiva per qualche secondo un moltiplicatore "🌟x2" che
   raddoppia i punti dei mostri veri colpiti.
5. Un vero mostro non colpito che arriva in fondo allo schermo toglie una
   vita e sparisce (gli amici travestiti no).
6. Quando le vite finiscono si vede il punteggio finale e il record più
   alto raggiunto in questa sessione; si può riprovare subito con
   "Riprova".

## Il Mostro Cerca

Un gioco di osservazione: la griglia si riempie di icone tutte uguali
tranne una, il "bersaglio" mostrato in alto, da trovare toccandolo prima
che scada il tempo. Come Memory e Monster Parking ha **livelli fissi**: la
griglia cresce di livello in livello, da 3×3 a 8×8, e ogni livello richiede
di trovare il bersaglio **4 volte di seguito** (ogni volta con una griglia
nuova). Il tempo a disposizione cresce insieme alla griglia (da 14 a 24
secondi), pensato apposta per essere generoso con un bambino di 6/7 anni.

**6 livelli sequenziali**, con griglia via via più grande:

| Livello | Griglia |
| --- | --- |
| 1 | 3×3 |
| 2 | 4×4 |
| 3 | 5×5 |
| 4 | 6×6 |
| 5 | 7×7 |
| 6 | 8×8 |

### Come si gioca

1. Dalla home si tocca **"Il Mostro Cerca"**, poi **"Gioca!"**.
2. In alto è indicata l'icona da trovare (es. "Trova: 👻") insieme al tempo
   rimanente in secondi.
3. Si tocca l'icona giusta tra tutte le altre: se è quella giusta si sente
   un suono di successo, altrimenti un suono morbido di errore (ma si può
   riprovare subito, il tocco sbagliato non fa perdere la ricerca).
4. Se il tempo scade prima di trovarla, il bersaglio viene rivelato con un
   bordo colorato prima di passare alla ricerca successiva.
5. Dopo 4 ricerche superate si passa automaticamente al livello successivo
   (griglia più grande). Dopo il livello 6 si può ricominciare con "Nuova
   partita".

## File principali

- **`MainActivity.kt`** — contiene la suite (`AppSuite`, `SchermataHub`,
  l'enum `Gioco` e l'elenco `elencoGiochi` degli 8 giochi disponibili),
  l'estensione `String.maiuscolo()` e il composable `BottoneTornaAiGiochi`
  (la freccia ⬅️ per uscire, riusata da ogni schermata di ogni gioco)
  condivisi da tutta la suite, e tutte le schermate Compose di Monster
  Restaurant e Monster Panino (questi due giochi condividono `Piatto`,
  `Commensale`, `RichiestaMostro`, `CartaRichiesta`, `CartaPiattoMenu`,
  `RisultatoOverlay`, riusati anche da Vesti il Mostro, quindi restano
  pubblici qui). Chiama anche `SuoniGioco.inizializza()` all'avvio
  dell'activity. Un gioco futuro va aggiunto come un nuovo ramo del `when`
  in `AppSuite`; se non condivide meccaniche con i giochi esistenti
  conviene dargli subito un file proprio, come fatto per Monster Parking,
  Memory, Vesti il Mostro, Ritmo, Spara ai Mostri e Il Mostro Cerca.
- **`SuoniGioco.kt`** — oggetto singleton che gestisce un `SoundPool`
  condiviso da tutta la suite, con quattro suoni brevi (`tocco`, `successo`,
  `errore`, `vittoria`) richiamati da ogni gioco ad ogni interazione
  rilevante; i 4 file `.wav` in `res/raw/` sono toni sintetizzati (nessun
  asset audio esterno).
- **`FoodData.kt`** — dati di Monster Restaurant e Monster Panino: per il
  primo le 6 portate e i loro menù, i 6 livelli di difficoltà; per il
  secondo il banco ingredienti (`elencoIngredientiPanino`) e i suoi 5
  livelli (`elencoLivelliPanino`); condivisi da entrambi (e da Vesti il
  Mostro) l'elenco dei 4 commensali e le 10 richieste possibili.
- **`GameLogic.kt`** — calcolo del punteggio (0-100) ed estrazione casuale di
  richieste e commensali, usato da Monster Restaurant e Monster Panino.
- **`ParkingData.kt`** — modello dati e logica pura di Monster Parking:
  `Auto`, `Orientamento`, la funzione `provaSpostamento` (unica fonte di
  verità su quali mosse sono valide) e i 20 `LivelloParcheggio` con le auto
  già posizionate.
- **`ParkingGame.kt`** — le schermate Compose di Monster Parking
  (`AppMonsterParking`, Home, Gioco con la griglia disegnata cella per
  cella, Fine), completamente separato dagli altri giochi.
- **`MemoryData.kt`** / **`MemoryGame.kt`** — dati (`CartaMemory`,
  `LivelloMemory`, generazione del mazzo) e schermate Compose di Memory dei
  Mostri.
- **`VestitiData.kt`** / **`VestitiGame.kt`** — dati (banchi di accessori
  per categoria, `elencoRichiesteVestiti`, `LivelloVestiti`) e schermate
  Compose di Vesti il Mostro; riusa i tipi di Monster Restaurant descritti
  sopra.
- **`RitmoData.kt`** / **`RitmoGame.kt`** — dati (`TastoRitmo`) e schermate
  Compose di Ritmo Mostruoso, senza una lista di livelli fissi (la sequenza
  cresce finché non si sbaglia).
- **`SparaData.kt`** / **`SparaGame.kt`** — dati (`MostroVolante` con il suo
  `TipoMostro` — normale, da evitare o moltiplicatore —, `Proiettile`,
  generazione dei mostri e delle 4 fasi di difficoltà via `livelloSpara`) e
  schermate Compose di Spara ai Mostri; l'unico gioco della suite con un
  loop grafico in tempo reale (`LaunchedEffect` + `withFrameNanos`, invece
  delle interazioni a turni degli altri giochi), riusa `elencoSimboliMemory`
  di Memory dei Mostri per le emoji dei mostri-palloncino veri.
- **`CercaData.kt`** / **`CercaGame.kt`** — dati (`LivelloCerca`,
  `GrigliaCerca`, generazione della griglia con un solo bersaglio) e
  schermate Compose de Il Mostro Cerca; riusa anch'esso
  `elencoSimboliMemory` di Memory dei Mostri come pool di icone.
- **`ui/theme/`** — tema con palette allegra e colorata fissa, pensata per
  bambini (non segue il tema scuro di sistema), condiviso da tutta la suite.
- **`res/drawable-nodpi/sfondo_taverna_mostri.png`** — illustrazione usata
  come sfondo della schermata iniziale di Monster Restaurant.
- **`res/drawable-nodpi/sfondo_panino_mostri.jpg`** — illustrazione usata
  come sfondo della schermata iniziale di Monster Panino.
- **`res/drawable-nodpi/sfondo_vestiti_mostri.jpg`** — illustrazione usata
  come sfondo della schermata iniziale di Vesti il Mostro.
- **`res/drawable-nodpi/sfondo_parcheggio_mostri.jpg`** — illustrazione usata
  come sfondo della schermata iniziale di Monster Parking.
- **`res/drawable-nodpi/sfondo_memory_mostri.jpg`** — illustrazione usata
  come sfondo della schermata iniziale di Memory dei Mostri.
- **`res/raw/suono_tocco.wav`, `suono_successo.wav`, `suono_errore.wav`,
  `suono_vittoria.wav`** — i 4 suoni di interazione condivisi da tutta la
  suite, caricati da `SuoniGioco.kt`.

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

## Pubblicazione su Google Play

Package: **`it.freebimbogames.app`** — una volta pubblicato per la prima
volta su Play Console questo identificativo non si può più cambiare, quindi
è stato scelto prima di procedere (invece del precedente `it.example.*`,
chiaramente segnaposto).

### Build firmata (.aab)

Il workflow **"Build MenuMostro Release AAB"** (`.github/workflows/release-menumostro.yml`)
genera l'Android App Bundle firmato da caricare su Play Console. Va avviato
a mano dalla tab *Actions* di GitHub ("Run workflow"), non ad ogni push come
la build di debug, e richiede 4 secret del repository (**Settings > Secrets
and variables > Actions**):

| Secret | Contenuto |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | il keystore di release codificato in base64 |
| `RELEASE_STORE_PASSWORD` | password del keystore |
| `RELEASE_KEY_ALIAS` | alias della chiave |
| `RELEASE_KEY_PASSWORD` | password della chiave (per un keystore PKCS12 è la stessa di store) |

Il keystore di release **non è nel repository** (a differenza di
`debug.keystore`, che è pubblico apposta solo per firmare in modo coerente
le build di test): va conservato al sicuro fuori da GitHub, perché se si
perde non è più possibile pubblicare aggiornamenti della stessa scheda app.
Ad ogni run il workflow pubblica l'`.aab` come artifact scaricabile da
GitHub Actions, pronto per l'upload manuale su Play Console.

### Cosa serve ancora prima della prima pubblicazione

- **Icona dell'app**: quella attuale (`ic_launcher_foreground.xml`) è
  esplicitamente un placeholder ("un mostriciattolo buffo, da sostituire
  con un'icona definitiva"); Play Console richiede anche un'icona 512×512
  a parte per la scheda store.
- **Materiali della scheda store**: screenshot, feature graphic, titolo,
  descrizione breve/estesa.
- **Privacy policy**: obbligatoria su Play Console anche per un'app senza
  permessi/rete/raccolta dati (specialmente per un'app rivolta a bambini).
- **Questionario contenuti e pubblico di destinazione**: essendo un'app per
  bambini di 6/7 anni, in Play Console vanno compilate le sezioni sul
  pubblico di destinazione e le *Families Policies* (dichiarazione dati,
  niente pubblicità comportamentale verso minori).

## Idee per estensioni future

Tutte le caselle della home sono ora giocabili e ogni gioco ha già suoni di
interazione. Idee non ancora implementate:

- **Conta i Mostri** — mini-gioco di conteggio/matematica per bambini
  piccoli: quanti mostri di un certo tipo compaiono a schermo, si tocca il
  numero giusto.

Altre idee per la suite in generale:

- Salvare la partita in corso (livello e punteggio) per poterla riprendere
  riaprendo l'app, con la home "Free Bimbo Games" che offra "Continua" oltre a
  scegliere un gioco nuovo.
- Più richieste e più piatti per portata, per aumentare la varietà.
- Animazioni di masticazione/coriandoli quando si ottengono 100 punti.
