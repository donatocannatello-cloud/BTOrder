# Gestione Condomini

Applicazione web personale per gestire spese e incassi di più condomini.
PHP 8 puro (nessun framework, nessun database SQL), pensata per l'hosting
condiviso Aruba (Apache + PHP). Un solo utente, protetto da password.

Interfaccia pensata per il desktop, utilizzabile anche da telefono.

## Funzioni

- **Condomini**: nome, indirizzo, codice fiscale, il tuo ruolo (amministratore / proprietario),
  saldo di cassa iniziale con data, note. Eliminazione protetta dalla digitazione del nome
  (una copia resta in `data/backup`).
- **Unità**: interno, scala, piano, descrizione, proprietario e inquilino con telefono ed email,
  note, millesimi per ogni tabella.
- **Tabelle millesimali**: ogni nuovo condominio parte con Generale, Scale e Riscaldamento;
  se ne possono aggiungere altre (es. "Scala B", "Ascensore"), rinominarle o eliminarle.
  La griglia unità × tabelle permette di inserire tutti i millesimi in una volta e mostra
  i totali per colonna aggiornati mentre si digita (verde se quadrano a 1000).
- **Tipologie di spesa**: ognuna indica se la spesa è *ordinaria* o *straordinaria*, la tabella
  millesimale proposta e la **percentuale a carico dell'inquilino** (il resto al proprietario).
  Ogni condominio parte con 13 tipologie precompilate secondo la prassi della L. 392/1978
  (es. pulizia scale, luce, ascensore, riscaldamento 100% inquilino; portierato 90%;
  amministratore, assicurazione, lavori straordinari 0%). Sono modificabili.
- **Spese**: data, tipologia, fornitore, descrizione, importo, tabella, % inquilino, scadenza
  delle quote, stato pagata/da pagare, allegati PDF o foto. Alla registrazione il sistema
  calcola il **riparto**: quota di ogni unità secondo i millesimi (metodo dei resti maggiori,
  la somma torna sempre al centesimo) e divisione tra proprietario e inquilino. Senza inquilino
  paga tutto il proprietario. Il riparto resta "fotografato" sulla spesa: cambiare millesimi o
  inquilini non altera le spese passate, salvo "Ricalcola riparto".
- **Scadenze trimestrali**: le quote di ogni spesa scadono a fine trimestre (31/03, 30/06,
  30/09, 31/12) del trimestre della spesa; si può indicare una scadenza diversa sulla singola spesa.
- **Rate trimestrali**: per ogni condòmino e trimestre importo dovuto e stato (pagata, parziale,
  da pagare, scaduta). "Segna pagata" registra un versamento del residuo destinato a quella rata.
- **Incassi**: versamenti di proprietari e inquilini (data, importo, metodo, rata di riferimento,
  note). Un versamento con rata di riferimento copre quel trimestre; senza riferimento copre prima
  le quote più vecchie.
- **Affitti e altre entrate**: entrate a cadenza regolare (mensile, bimestrale, trimestrale,
  semestrale, annuale) o una tantum, con debitore, importo, prima e ultima scadenza. Lo
  scadenziario mostra ogni scadenza come incassata, da incassare o scaduta; si segna
  incassata (con data, importo e metodo) o di nuovo non incassata. Le entrate personali possono
  essere escluse dalla cassa del condominio.
- **Situazione e morosità**: per ogni proprietario/inquilino addebitato (ordinarie e
  straordinarie), versato, saldo e quota **scaduta**.
- **Dashboard**: saldo di cassa per condominio (saldo iniziale + versamenti + entrate incassate
  − spese pagate), spese da pagare, quote scadute con i morosi, affitti/entrate scaduti.
- **Allegati**: salvati in `uploads/<condominio>/`, accettati solo se il contenuto è davvero
  PDF o immagine, serviti solo dopo il login. Il limite di dimensione dipende anche da PHP
  (`upload_max_filesize`, visibile in *Impostazioni → Verifica installazione*).
- **Report** (scheda *Report* di ogni condominio), per anno o per tutti gli anni:
  - *Estratto conto* per condòmino o per intera unità: addebiti e versamenti in ordine di data
    con saldo progressivo e saldo iniziale dell'anno; "Tutti" produce un estratto per pagina.
  - *Rendiconto annuale*: spese ordinarie e straordinarie per tipologia (totale, pagato, da
    pagare), movimenti di cassa (saldo al 01/01, entrate, spese pagate, saldo al 31/12) e
    riparto consuntivo per condòmino.
  - *Affitti e altre entrate*: previsto, incassato e scaduto per entrata, con il dettaglio scadenze.
  - *Elenco spese*, *Elenco versamenti*, *Situazione e morosità*.
  - Ogni report si esporta in **CSV** (separatore `;`, virgola decimale, si apre direttamente
    in Excel italiano) e ha una **versione stampabile**: dal browser scegli *Stampa → Salva come
    PDF*. I pulsanti CSV/Stampa sono anche negli elenchi di spese, incassi, situazione ed entrate.
- Le date si inseriscono come `gg/mm/aaaa` (anche `gg-mm-aaaa` o `gg.mm.aaaa`).

## Struttura

```
condomini/
├── index.php          unico punto d'ingresso (index.php?p=<pagina>)
├── .htaccess          niente elenco cartelle, blocco di .json/.md/file nascosti
├── assets/            style.css, app.js (nessuna dipendenza esterna)
├── includes/          configurazione, archivio JSON, login, CSRF, layout   (accesso web negato)
├── pages/             una pagina per file                                   (accesso web negato)
├── data/              config.json, un file per condominio, backup, sessioni (accesso web negato)
│   ├── backup/        copie datate a ogni salvataggio (ultime 30 per file)
│   ├── locks/         file di lock per flock()
│   ├── sessions/      sessioni PHP private
│   └── logs/          php_errors.log
└── uploads/           allegati (fatture), serviti solo tramite script autenticato (accesso web negato)
```

Le cartelle sotto `data/` e `uploads/` vengono create automaticamente al primo avvio,
insieme ai rispettivi `.htaccess` se il client FTP non li avesse caricati.

## Installazione su Aruba via FTP

1. **Imposta la chiave di installazione.** Prima di caricare i file apri
   `includes/config.php` e sostituisci `CAMBIAMI` con una chiave a tua scelta
   (almeno 8 caratteri), ad esempio:
   ```php
   const SETUP_KEY = 'una-frase-lunga-che-conosci-solo-tu';
   ```
   Serve solo una volta, per impostare la password iniziale.

2. **Versione PHP.** Nel pannello di controllo Aruba (Hosting Linux →
   Pannello di controllo → *Gestione PHP* / *Versione PHP*) scegli PHP 8.0 o superiore
   e verifica che le estensioni **zip** e **fileinfo** siano attive (di solito lo sono).

3. **Carica i file.** Collegati via FTP (es. FileZilla) con i dati ricevuti da Aruba
   (host `ftp.tuodominio.it`). Carica **il contenuto** della cartella `condomini/`
   in una sottocartella del sito, ad esempio `/www.tuodominio.it/condomini/`.
   - In FileZilla attiva *Server → Forza la visualizzazione dei file nascosti*,
     così vengono caricati anche i file `.htaccess`.
   - Usa la modalità di trasferimento **binaria** o automatica.

4. **Permessi di scrittura.** Le cartelle `data/` e `uploads/` devono essere scrivibili
   da PHP. Su Aruba normalmente lo sono già; se la pagina *Impostazioni → Verifica
   installazione* segnala il contrario, da FileZilla clic destro sulla cartella →
   *Permessi file* → `755` (oppure `775`).

5. **Imposta la password.** Apri `https://www.tuodominio.it/condomini/`: verrai portato
   alla pagina *Prima configurazione*. Inserisci la chiave di installazione e scegli
   la password (almeno 10 caratteri). La password viene salvata in `data/config.json`
   come hash (`password_hash`); da quel momento la pagina di installazione si disattiva.

6. **Verifica la protezione.** Apri nel browser
   `https://www.tuodominio.it/condomini/data/config.json`: deve comparire
   **403 Forbidden**. Se invece vedi il contenuto del file, i `.htaccess` non sono
   stati caricati: ricaricali (vedi punto 3).

7. **HTTPS (consigliato).** Attiva il certificato SSL dal pannello Aruba, poi togli il
   commento alle tre righe `RewriteEngine/RewriteCond/RewriteRule` nel `.htaccess`
   principale per forzare HTTPS.

## Provare l'app senza Aruba

**Dati dimostrativi.** `php strumenti/demo.php` crea due condomini di prova (nomi inventati) con spese,
rate, incassi e affitti, e imposta la password `demo-condomini` se non ne esiste una. Con `--force`
sostituisce i condomini già presenti. Lo script funziona solo da riga di comando ed è escluso dalla
pubblicazione automatica: non usarlo sul server vero.

**Sul tuo computer** (serve PHP 8):
```
cd condomini
php strumenti/demo.php
php -S 127.0.0.1:8000
```
poi apri http://127.0.0.1:8000 e accedi con `demo-condomini`. Su Windows, se non hai PHP, puoi
installare [XAMPP](https://www.apachefriends.org/it/) e copiare la cartella `condomini` in
`C:\xampp\htdocs\`, poi aprire http://localhost/condomini (per i dati di prova:
`C:\xampp\php\php.exe strumenti\demo.php` dalla cartella `condomini`).

**Nel browser con GitHub Codespaces** (nessuna installazione): sulla pagina del repository
*Code → Codespaces → … → New with options*, scegli il branch e la configurazione
**"Gestione Condomini (PHP)"**, poi *Create codespace*. Dopo l'avvio si apre l'app con i dati di
prova (password `demo-condomini`). Il piano gratuito di GitHub include alcune ore al mese; ricordati
di fermare il codespace quando hai finito.

## Pubblicazione automatica da GitHub (alternativa all'FTP manuale)

Il file `.github/workflows/deploy-aruba.yml` (nella radice del repository) carica la cartella
`condomini/` su Aruba via FTP con GitHub Actions. Carica solo i file modificati e **non tocca mai
`data/` e `uploads/`** sul server: i dati e gli allegati restano al sicuro.

**Configurazione (una volta sola)** su GitHub → repository → *Settings → Secrets and variables → Actions*:

| Tipo | Nome | Valore |
|---|---|---|
| Secret | `FTP_SERVER` | `ftp.tuodominio.it` |
| Secret | `FTP_USERNAME` | utente FTP di Aruba |
| Secret | `FTP_PASSWORD` | password FTP di Aruba |
| Secret | `FTP_SERVER_DIR` | cartella di destinazione con la `/` finale, es. `/www.tuodominio.it/condomini/` |
| Secret (facoltativo) | `SETUP_KEY` | la chiave di installazione: viene scritta in `includes/config.php` al momento del caricamento, così nel repository resta `CAMBIAMI` |
| Variable (facoltativa) | `FTP_PROTOCOL` | `ftps` (predefinito, connessione cifrata) oppure `ftp` se Aruba rifiuta FTPS |

**Come si pubblica:** porta il codice sul branch **`aruba`** (es. crea il branch `aruba` da quello di
sviluppo, oppure fai merge/push su `aruba`). Ogni push su `aruba` che modifica `condomini/` avvia la
pubblicazione; l'esito si vede nella scheda *Actions* del repository. Prima del caricamento viene
controllata la sintassi di tutti i file PHP: se c'è un errore non si carica nulla.

Il pulsante *Run workflow* (con l'opzione "prova senza caricare nulla") compare nella scheda Actions
solo se il file del workflow è presente anche nel branch predefinito del repository.

Note:
- La prima pubblicazione carica tutti i file; le successive solo quelli cambiati (lo stato è nel file
  `.ftp-deploy-sync-state.json` sul server, non accessibile dal web grazie al `.htaccess`).
- Le cartelle `data/` e `uploads/` vengono create dall'app al primo accesso, con i loro `.htaccess`.
- La password di accesso all'app non passa mai da GitHub: si imposta dal sito come al punto 5.

### Password dimenticata

Via FTP scarica `data/config.json`, cancella il valore di `password_hash`
(lascia `"password_hash": null`) e ricaricalo: alla visita successiva ricompare la
pagina di prima configurazione (serve sempre la `SETUP_KEY` di `includes/config.php`).

In alternativa, se hai PHP sul tuo computer, puoi generare l'hash direttamente:
```
php -r "echo password_hash('la-nuova-password', PASSWORD_DEFAULT), PHP_EOL;"
```
e incollarlo in `password_hash` dentro `data/config.json`.

## Sicurezza

- Password unica con `password_hash()`/`password_verify()`; rehash automatico se l'algoritmo cambia.
- Sessione PHP con cookie `HttpOnly`, `SameSite=Lax`, `Secure` sotto HTTPS, ID rigenerato al login.
- Disconnessione automatica dopo inattività (predefinito 30 minuti, modificabile in *Impostazioni*).
- Cambiando password si invalidano le altre sessioni aperte.
- Limite ai tentativi: dopo 5 password errate in 15 minuti l'indirizzo IP viene bloccato
  per 15 minuti; il blocco raddoppia a ogni recidiva (massimo 24 ore).
- Token CSRF su tutti i form (incluso il logout), verificato centralmente in `index.php`.
- Tutto l'output passa da `e()` (`htmlspecialchars`); intestazioni CSP, `X-Frame-Options`, `nosniff`.
- `data/`, `uploads/`, `includes/`, `pages/` negati da `.htaccess` (`Require all denied`).

## Archiviazione

- Un file JSON per documento in `data/`: `config.json` e (dalla fase 2) `condominio_<id>.json`.
- Ogni lettura/scrittura è protetta da `flock()`; la scrittura avviene su un file temporaneo
  e poi `rename()` atomico, quindi il file non è mai visibile a metà.
- A ogni salvataggio il nuovo contenuto viene copiato in `data/backup/<nome>__<data-ora>.json`;
  si conservano gli ultimi 30 backup per ciascun file.
- Per ripristinare un backup: scaricalo via FTP, rinominalo (es. `config.json`) e
  sovrascrivi il file in `data/`.
- Gli importi sono salvati in centesimi (interi) per evitare errori di arrotondamento;
  le date in formato `aaaa-mm-gg` e mostrate come `gg/mm/aaaa`.

## Esporta tutto

*Impostazioni → Esporta tutto* scarica uno ZIP con tutti i file JSON di `data/` e tutti gli
allegati di `uploads/` (opzionalmente anche i backup automatici).

## Prova in locale

```
cd condomini
php -S 127.0.0.1:8000
```
e apri http://127.0.0.1:8000 (il server integrato di PHP ignora i `.htaccess`:
le protezioni delle cartelle valgono solo su Apache).
