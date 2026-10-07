# Gestione Condomini

Applicazione web personale per gestire spese e incassi di più condomini.
PHP 8 puro (nessun framework, nessun database SQL), pensata per l'hosting
condiviso Aruba (Apache + PHP). Un solo utente, protetto da password.

> Stato: **Fase 2** — fase 1 (struttura, archivio JSON, login, backup, ZIP) + condomini,
> unità e tabelle millesimali. Prossime fasi: uscite, incassi e riparto → report ed export.
> Interfaccia pensata per il desktop, utilizzabile anche da telefono.

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
