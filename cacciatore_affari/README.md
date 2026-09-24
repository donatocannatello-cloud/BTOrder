# Cacciatore Affari

Bot in Python 3.11 che scansiona i siti di aste giudiziarie e di annunci per
trovare affari. Per ora cerca **capannoni** in **Lombardia** sul
[Portale delle Vendite Pubbliche](https://pvp.giustizia.it) (PVP). La
struttura è già pronta per aggiungere in seguito **auto** e **moto** e altri siti.

Ogni ciclo esegue: **fetch → normalizzazione → deduplica → salvataggio →
punteggio → notifica Telegram**.

Non usa database: i dati sono salvati in file JSON locali, con scrittura
atomica (file temporaneo + `os.replace`).

## Requisiti

- Python 3.11+
- le dipendenze in `requirements.txt` (`httpx`, `pytest`)

## Installazione

```bash
cd cacciatore_affari
python3.11 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp config.example.json config.json
```

Poi modifica `config.json` (vedi sotto) e inserisci token e chat_id Telegram.

## Avvio

```bash
python main.py                # un solo ciclo
python main.py --loop 6       # un ciclo ogni 6 ore (Ctrl+C per fermare)
python main.py --dry-run      # nessun invio Telegram: i messaggi finiscono nel log
python main.py -v             # log dettagliato
python main.py --config /percorso/altro_config.json
```

In alternativa a `--loop` puoi programmare `python main.py` con cron o con
l'Utilità di pianificazione di Windows.

## Telegram

1. Crea un bot con [@BotFather](https://t.me/BotFather) e copia il **token**.
2. Scrivi un messaggio al bot, poi apri
   `https://api.telegram.org/bot<TOKEN>/getUpdates` e copia il valore di `chat.id`.
3. Inseriscili in `config.json` → `telegram.token` / `telegram.chat_id`,
   oppure usa le variabili d'ambiente `TELEGRAM_TOKEN` e `TELEGRAM_CHAT_ID`.

Senza token il bot funziona lo stesso, ma scrive le notifiche solo nel log.

Vengono notificati gli annunci **nuovi** o con **prezzo ribassato** che hanno
un punteggio pari o superiore alla soglia. Dopo l'invio vengono marcati con
`notificato: true`. Se il prezzo scende ancora, l'annuncio torna a
`notificato: false` e viene segnalato di nuovo.

## Configurazione (`config.json`)

| Chiave | Significato |
|---|---|
| `regioni`, `province` | Area di ricerca. `province` (sigle, es. `["MI","BG"]`) prevale su `regioni`. Le regioni riconosciute sono in `geo.py` (per ora solo la Lombardia). |
| `categorie.<nome>` | `abilitata`, `tipo_lotto` (`IMMOBILI`/`MOBILI` sul PVP), `parole_chiave`, `parole_escluse`, `prezzo_max`, `prezzo_min`, `superficie_min`, `soglia_punteggio`. |
| `soglia_punteggio` | Soglia di notifica predefinita (0-100). |
| `scoring` | Pesi e parametri del punteggio (vedi sotto). |
| `dedup` | Tolleranze per riconoscere i duplicati (`tolleranza_superficie` 0.03 = ±3%, `tolleranza_prezzo`). |
| `telegram` | `token`, `chat_id`. |
| `http` | User-Agent, pausa tra richieste (`min_delay`/`max_delay`, predefinito 3-5 s), retry, timeout, `rispetta_robots`. |
| `fonti.pvp` | Impostazioni dell'adattatore PVP (vedi sotto). |
| `percorsi` | File di dati, cartella perizie e file di log (relativi alla cartella del config). |

### Punteggio (0-100)

Media pesata di tre componenti, ciascuna tra 0 e 1:

- **eur_mq**: €/m² rispetto alla mediana della provincia, calcolata sugli
  annunci raccolti (se la provincia ha meno di `campioni_minimi_mediana`
  annunci si usa la mediana della categoria). Metà della mediana o meno vale 1,
  una volta e mezza la mediana o più vale 0.
- **ribasso**: calo del prezzo rispetto alla prima base d'asta nota. Se
  l'annuncio compare già a un tentativo successivo, la base originaria è
  stimata con `ribasso_stimato_per_tentativo` (25% per asta). La componente
  vale 1 da `ribasso_massimo` in su.
- **tentativi**: aste andate deserte (tentativo − 1). La componente vale 1
  da `tentativi_massimi` in su.

Le componenti che non si possono calcolare vengono escluse e i loro pesi
ridistribuiti sulle altre. Per esempio, senza superficie non si calcola €/m²
(sarà il caso normale per auto e moto).

### Deduplica

Due annunci sono lo stesso bene se:

- hanno stesso **RGE + tribunale** (e lo stesso lotto, se indicato), oppure
- hanno stesso **comune**, **superficie ±3%** e **prezzo simile**.

I duplicati vengono fusi nell'annuncio già esistente. Gli ID alternativi
restano in `id_alias` e lo storico prezzi si aggiorna.

## Portale Vendite Pubbliche: come funziona l'adattatore

Il PVP è un'applicazione JavaScript. Le pagine HTML della lista non
contengono i dati, che vengono caricati via XHR in formato JSON. L'adattatore
usa quindi direttamente l'API JSON:

1. `GET /bo-ms/fe-config/area-annunci`: configurazione pubblica del frontend,
   da cui si ricava l'URL del microservizio di ricerca (il prefisso
   `ric-<hash>` può cambiare a ogni rilascio del portale).
2. `POST /ric-<hash>/ric-ms/ricerca/vendite?page=N&size=M&sort=dataVendita,asc`
   con corpo `{"filtroAnnunci": 1, "tipoLotto": "IMMOBILI"}`.
3. Per gli annunci nuovi legge la pagina `detail_annuncio.page?idAnnuncio=…`
   e ne ricava il link alla perizia.

Il filtro per categoria (parole chiave) e per regione/provincia viene
applicato anche sul client.

> ⚠️ **Da verificare al primo avvio.** Il formato dell'API non è documentato
> ufficialmente. Mentre scrivevo il codice il sito non era raggiungibile, quindi
> i nomi dei campi sono cercati tra diverse alternative plausibili
> (`CAMPI` in `adapters/pvp.py`). Al primo avvio:
>
> 1. esegui `python main.py --dry-run -v`;
> 2. apri il JSON salvato in `data/raw/` (`fonti.pvp.salva_risposte_grezze`);
> 3. se un campo non viene riconosciuto, aggiungi il percorso corretto in
>    `fonti.pvp.mappa_campi` senza toccare il codice, ad esempio
>    `"mappa_campi": {"prezzo_base": ["lotto.prezzoBaseAsta"], "comune": ["indirizzo.citta"]}`.
>    I percorsi annidati si scrivono con il punto; gli elementi di una lista si indicano con l'indice (`beni.0.superficie`);
> 4. per limitare le pagine scaricate puoi aggiungere filtri lato server in
>    `fonti.pvp.filtri_extra` (es. il codice della regione, se l'API lo
>    accetta), oppure ridurre `max_pagine`.

Altre opzioni di `fonti.pvp`: `page_size`, `max_pagine`, `sort`,
`scarica_dettagli` (visita la pagina di dettaglio degli annunci nuovi),
`scarica_perizie` (salva i PDF in `data/perizie/`), `url_dettaglio_api`
(modello di URL JSON per il dettaglio, es. `https://…/{id}`, al posto dell'HTML).

## Buone pratiche di rete

- User-Agent realistico e configurabile.
- Almeno 3-5 secondi tra una richiesta e l'altra allo stesso host. Se
  robots.txt indica un `Crawl-delay` maggiore, vale quello.
- Retry con backoff esponenziale su errori di rete, 429 e 5xx (rispettando `Retry-After`).
- Viene rispettato `robots.txt`. Se non è verificabile (errore di rete, 401/403 o 5xx), l'host viene saltato per quel ciclo.

## File e cartelle

```
cacciatore_affari/
├── main.py            # ciclo completo e opzione --loop
├── config.py          # caricamento di config.json
├── storage.py         # unico punto di accesso ai file JSON (load/save/upsert atomici)
├── models.py          # schema normalizzato dell'annuncio, merge e storico prezzi
├── dedup.py           # riconoscimento duplicati
├── scoring.py         # punteggio 0-100
├── notifier.py        # notifiche Telegram
├── perizie.py         # download PDF + stub analizza_perizia() (futuro: Ollama)
├── http_client.py     # client HTTP con rate limiting, retry e robots.txt
├── geo.py             # regioni e province
├── adapters/
│   ├── base.py        # classe astratta Adapter
│   └── pvp.py         # Portale Vendite Pubbliche
├── data/
│   ├── annunci.json   # archivio annunci (creato al primo avvio)
│   ├── perizie/       # PDF delle perizie
│   └── raw/           # risposte grezze per debug (opzionale)
├── logs/              # log a rotazione
└── tests/             # test pytest con dati finti
```

### Schema di un annuncio

`id, fonte, categoria, titolo, prezzo_base, offerta_minima, superficie_mq,
eur_mq, comune, provincia, indirizzo, lat, lon, tipo_vendita, data_asta,
numero_tentativo, rge, tribunale, url, url_perizia, first_seen, last_seen,
storico_prezzi, notificato`, più i campi accessori `lotto, regione,
descrizione, id_fonte, id_alias, punteggio, dettaglio_punteggio,
perizia_locale, notificato_il, extra`.

## Test

```bash
pytest
```

I test usano dati finti e un server HTTP simulato (`httpx.MockTransport`).
Non fanno richieste di rete.

## Estensioni future

- **Nuovo sito**: crea `adapters/<nome>.py` con una sottoclasse di `Adapter`
  (`fetch_listings`, `fetch_detail`), registrala in `adapters/__init__.py`
  e aggiungi la voce in `config.json` → `fonti`.
- **Auto e moto**: le categorie sono già presenti in `config.example.json`
  (disabilitate). Per attivarle, aggiungi la categoria a
  `categorie_supportate` dell'adattatore, verifica le parole chiave e poni
  `abilitata: true`.
- **Analisi perizie**: completa `perizie.analizza_perizia(pdf_path)` con Ollama.
