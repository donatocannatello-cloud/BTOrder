"""Genera una pagina HTML con tabella e mappa degli annunci raccolti.

I dati vengono incorporati nella pagina, così il file si apre con un doppio
clic senza bisogno di un server. La mappa usa Leaflet/OpenStreetMap e
richiede la connessione a internet; la tabella funziona anche offline.

Uso:
    python viewer.py                 # genera report/annunci.html e lo apre nel browser
    python viewer.py --no-open       # genera soltanto
"""

from __future__ import annotations

import argparse
import json
import webbrowser
from datetime import datetime
from pathlib import Path

import storage
from config import carica_config

# Campi inclusi nella pagina (lo storico serve per mostrare i ribassi)
CAMPI_PAGINA = (
    "id", "fonte", "categoria", "titolo", "prezzo_base", "offerta_minima", "superficie_mq",
    "eur_mq", "comune", "provincia", "indirizzo", "lat", "lon", "tipo_vendita", "data_asta",
    "numero_tentativo", "rge", "tribunale", "url", "url_perizia", "first_seen", "last_seen",
    "storico_prezzi", "notificato", "punteggio", "dettaglio_punteggio",
)


def genera_html(annunci: list[dict], generato_il: str | None = None) -> str:
    righe = [{k: a.get(k) for k in CAMPI_PAGINA} for a in annunci]
    # "</" dentro un <script> chiuderebbe il tag: lo si spezza
    dati = json.dumps(righe, ensure_ascii=False).replace("</", "<\\/")
    generato_il = generato_il or datetime.now().strftime("%d/%m/%Y %H:%M")
    return (_TEMPLATE
            .replace("__DATI__", dati)
            .replace("__GENERATO__", generato_il)
            .replace("__TOTALE__", str(len(righe))))


def scrivi_report(path_annunci: str | Path, path_html: str | Path) -> Path:
    annunci = storage.load_annunci(path_annunci)
    storage.save_text(path_html, genera_html(annunci))
    return Path(path_html)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Genera la pagina HTML degli annunci")
    p.add_argument("--config", default=str(Path(__file__).with_name("config.json")))
    p.add_argument("--no-open", action="store_true", help="non aprire il browser")
    args = p.parse_args(argv)
    cfg = carica_config(args.config)
    out = scrivi_report(cfg["percorsi"]["annunci"], cfg["percorsi"]["report"])
    print(f"Report generato: {out}")
    if not args.no_open:
        webbrowser.open(out.resolve().as_uri())
    return 0


_TEMPLATE = r"""<!DOCTYPE html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Cacciatore Affari</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
<style>
:root {
  --bg: #f6f7f9; --panel: #ffffff; --text: #1d2330; --muted: #667085; --border: #e3e6eb;
  --accent: #2f6fdf; --good: #1a7f4b; --mid: #b7791f; --low: #9aa3b2; --drop: #c0392b;
  --row-hover: #f0f4fb;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #12151b; --panel: #1b1f27; --text: #e6e9ef; --muted: #98a2b3; --border: #2c323d;
    --accent: #6ea0ff; --good: #3fbf7f; --mid: #e0a846; --low: #6b7385; --drop: #ff6b5b;
    --row-hover: #232936;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text);
       font: 14px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
header { padding: 16px 20px 8px; display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 16px; }
h1 { font-size: 20px; margin: 0; }
.sub { color: var(--muted); font-size: 13px; }
.filtri { display: flex; flex-wrap: wrap; gap: 10px; padding: 8px 20px 12px; align-items: end; }
.filtri label { display: flex; flex-direction: column; font-size: 12px; color: var(--muted); gap: 3px; }
.filtri input, .filtri select { font: inherit; color: var(--text); background: var(--panel);
  border: 1px solid var(--border); border-radius: 6px; padding: 6px 8px; min-width: 120px; }
.filtri input[type=search] { min-width: 220px; }
.stat { display: flex; flex-wrap: wrap; gap: 10px; padding: 0 20px 12px; }
.stat div { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; padding: 8px 12px; }
.stat b { display: block; font-size: 18px; }
.stat span { font-size: 12px; color: var(--muted); }
main { display: grid; grid-template-columns: minmax(0, 1fr) minmax(300px, 32%); gap: 14px; padding: 0 20px 20px; }
@media (max-width: 1000px) { main { grid-template-columns: 1fr; } #mappa { height: 360px; } }
.pannello { background: var(--panel); border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.tabella { overflow: auto; max-height: calc(100vh - 230px); }
@media (max-width: 1000px) { .tabella { max-height: none; } }
table { border-collapse: collapse; width: 100%; }
th, td { padding: 7px 9px; border-bottom: 1px solid var(--border); text-align: left; white-space: nowrap; }
th { position: sticky; top: 0; background: var(--panel); font-size: 12px; color: var(--muted);
     cursor: pointer; user-select: none; z-index: 1; }
th.num, td.num { text-align: right; font-variant-numeric: tabular-nums; }
th[aria-sort=ascending]::after { content: " ▲"; }
th[aria-sort=descending]::after { content: " ▼"; }
tbody tr { cursor: pointer; }
tbody tr:hover, tbody tr.sel { background: var(--row-hover); }
td.titolo { white-space: normal; min-width: 220px; max-width: 360px; }
.badge { display: inline-block; min-width: 38px; text-align: center; border-radius: 999px;
         padding: 1px 8px; font-weight: 600; color: #fff; }
.b-good { background: var(--good); } .b-mid { background: var(--mid); } .b-low { background: var(--low); }
.drop { color: var(--drop); font-weight: 600; font-size: 12px; }
a { color: var(--accent); }
#mappa { height: calc(100vh - 230px); min-height: 360px; }
.vuoto { padding: 30px; text-align: center; color: var(--muted); }
.nota-mappa { padding: 6px 10px; font-size: 12px; color: var(--muted); border-top: 1px solid var(--border); }
</style>
</head>
<body>
<header>
  <h1>Cacciatore Affari</h1>
  <span class="sub">Generato il __GENERATO__ · __TOTALE__ annunci in archivio</span>
</header>

<div class="filtri">
  <label>Cerca<input type="search" id="f-testo" placeholder="comune, titolo, tribunale…"></label>
  <label>Categoria<select id="f-categoria"><option value="">Tutte</option></select></label>
  <label>Provincia<select id="f-provincia"><option value="">Tutte</option></select></label>
  <label>Punteggio minimo<input type="number" id="f-punteggio" min="0" max="100" step="5" value="0"></label>
  <label>Prezzo massimo €<input type="number" id="f-prezzo" min="0" step="10000" placeholder="nessuno"></label>
  <label>Solo ribassati<select id="f-ribasso"><option value="">No</option><option value="1">Sì</option></select></label>
</div>

<div class="stat" id="stat"></div>

<main>
  <section class="pannello tabella">
    <table>
      <thead><tr>
        <th data-k="punteggio" class="num">Punti</th>
        <th data-k="titolo">Titolo</th>
        <th data-k="comune">Comune</th>
        <th data-k="provincia">Prov.</th>
        <th data-k="prezzo_base" class="num">Base €</th>
        <th data-k="offerta_minima" class="num">Min. €</th>
        <th data-k="superficie_mq" class="num">m²</th>
        <th data-k="eur_mq" class="num">€/m²</th>
        <th data-k="numero_tentativo" class="num">Tent.</th>
        <th data-k="data_asta">Asta</th>
        <th data-k="tribunale">Tribunale / RGE</th>
        <th>Link</th>
      </tr></thead>
      <tbody id="righe"></tbody>
    </table>
    <div class="vuoto" id="vuoto" hidden>Nessun annuncio corrisponde ai filtri.</div>
  </section>
  <section class="pannello">
    <div id="mappa"></div>
    <div class="nota-mappa" id="nota-mappa"></div>
  </section>
</main>

<script id="dati" type="application/json">__DATI__</script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<script>
const DATI = JSON.parse(document.getElementById("dati").textContent);
const $ = id => document.getElementById(id);
const eur = v => v == null ? "–" : v.toLocaleString("it-IT", {maximumFractionDigits: 0});
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const dataIt = s => { if (!s) return "–"; const d = new Date(s); return isNaN(d) ? esc(s) : d.toLocaleDateString("it-IT"); };
const ribasso = a => { const st = a.storico_prezzi || []; return st.length >= 2 && st[st.length-1].prezzo_base < st[st.length-2].prezzo_base ? st : null; };
const classe = p => p == null ? "b-low" : p >= 70 ? "b-good" : p >= 50 ? "b-mid" : "b-low";
const safeUrl = u => /^https?:\/\//i.test(u || "") ? u : null;

let ordine = {k: "punteggio", dir: -1};
let selezionato = null;

function riempiSelect(id, campo) {
  const valori = [...new Set(DATI.map(a => a[campo]).filter(Boolean))].sort();
  for (const v of valori) $(id).insertAdjacentHTML("beforeend", `<option>${esc(v)}</option>`);
}
riempiSelect("f-categoria", "categoria");
riempiSelect("f-provincia", "provincia");

function filtrati() {
  const t = $("f-testo").value.trim().toLowerCase();
  const cat = $("f-categoria").value, prov = $("f-provincia").value;
  const pmin = parseFloat($("f-punteggio").value) || 0;
  const pmax = parseFloat($("f-prezzo").value);
  const soloRib = $("f-ribasso").value === "1";
  return DATI.filter(a =>
    (!cat || a.categoria === cat) && (!prov || a.provincia === prov) &&
    (a.punteggio ?? 0) >= pmin && (isNaN(pmax) || (a.prezzo_base ?? 0) <= pmax) &&
    (!soloRib || ribasso(a)) &&
    (!t || [a.titolo, a.comune, a.indirizzo, a.tribunale, a.rge].join(" ").toLowerCase().includes(t))
  ).sort((x, y) => {
    const a = x[ordine.k], b = y[ordine.k];
    if (a == null && b == null) return 0;
    if (a == null) return 1;
    if (b == null) return -1;
    return (typeof a === "string" ? a.localeCompare(b) : a - b) * ordine.dir;
  });
}

function riga(a) {
  const st = ribasso(a);
  const drop = st ? `<div class="drop">▼ da ${eur(st[st.length-2].prezzo_base)}</div>` : "";
  const url = safeUrl(a.url), per = safeUrl(a.url_perizia);
  return `<tr data-id="${esc(a.id)}">
    <td class="num"><span class="badge ${classe(a.punteggio)}">${a.punteggio ?? "–"}</span></td>
    <td class="titolo">${esc(a.titolo) || "–"}</td>
    <td>${esc(a.comune) || "–"}</td>
    <td>${esc(a.provincia) || "–"}</td>
    <td class="num">${eur(a.prezzo_base)}${drop}</td>
    <td class="num">${eur(a.offerta_minima)}</td>
    <td class="num">${eur(a.superficie_mq)}</td>
    <td class="num">${eur(a.eur_mq)}</td>
    <td class="num">${a.numero_tentativo ?? "–"}</td>
    <td>${dataIt(a.data_asta)}</td>
    <td>${esc(a.tribunale) || "–"}<br><span class="sub">${esc(a.rge)}</span></td>
    <td>${url ? `<a href="${esc(url)}" target="_blank" rel="noopener">annuncio</a>` : ""}
        ${per ? `<br><a href="${esc(per)}" target="_blank" rel="noopener">perizia</a>` : ""}</td>
  </tr>`;
}

function statistiche(lista) {
  const prezzi = lista.map(a => a.eur_mq).filter(v => v != null).sort((a, b) => a - b);
  const med = prezzi.length ? prezzi[Math.floor(prezzi.length / 2)] : null;
  const box = (v, t) => `<div><b>${v}</b><span>${t}</span></div>`;
  $("stat").innerHTML =
    box(lista.length, "annunci visibili") +
    box(lista.filter(a => (a.punteggio ?? 0) >= 60).length, "con punteggio ≥ 60") +
    box(lista.filter(ribasso).length, "ribassati") +
    box(med == null ? "–" : eur(med) + " €", "€/m² mediano");
}

// --- mappa -------------------------------------------------------------
let mappa = null, livello = null;
const marker = {};
if (window.L) {
  mappa = L.map("mappa").setView([45.62, 9.77], 8);  // Lombardia
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 18, attribution: "© OpenStreetMap"
  }).addTo(mappa);
  livello = L.layerGroup().addTo(mappa);
} else {
  $("mappa").innerHTML = '<div class="vuoto">Mappa non disponibile (serve la connessione a internet).</div>';
}

function aggiornaMappa(lista) {
  if (!mappa) return;
  livello.clearLayers();
  for (const k in marker) delete marker[k];
  const conCoord = lista.filter(a => a.lat != null && a.lon != null);
  for (const a of conCoord) {
    const col = getComputedStyle(document.documentElement)
      .getPropertyValue(a.punteggio >= 70 ? "--good" : a.punteggio >= 50 ? "--mid" : "--low").trim();
    const m = L.circleMarker([a.lat, a.lon], {radius: 8, color: col, fillColor: col, fillOpacity: .8, weight: 1})
      .bindPopup(`<b>${esc(a.titolo)}</b><br>${esc(a.comune)} (${esc(a.provincia)})<br>` +
                 `Base € ${eur(a.prezzo_base)} · punti ${a.punteggio ?? "–"}` +
                 (safeUrl(a.url) ? `<br><a href="${esc(a.url)}" target="_blank" rel="noopener">apri annuncio</a>` : ""))
      .on("click", () => evidenzia(a.id, false));
    m.addTo(livello);
    marker[a.id] = m;
  }
  if (conCoord.length) mappa.fitBounds(L.latLngBounds(conCoord.map(a => [a.lat, a.lon])).pad(0.15), {maxZoom: 13});
  const senza = lista.length - conCoord.length;
  $("nota-mappa").textContent = senza === 1 ? "1 annuncio senza coordinate non è sulla mappa."
    : senza ? `${senza} annunci senza coordinate non sono sulla mappa.` : "";
}

function evidenzia(id, daTabella) {
  selezionato = id;
  document.querySelectorAll("#righe tr").forEach(tr => tr.classList.toggle("sel", tr.dataset.id === id));
  if (daTabella && marker[id]) { mappa.setView(marker[id].getLatLng(), Math.max(mappa.getZoom(), 12)); marker[id].openPopup(); }
  if (!daTabella) document.querySelector(`#righe tr[data-id="${CSS.escape(id)}"]`)?.scrollIntoView({block: "center", behavior: "smooth"});
}

function aggiorna() {
  const lista = filtrati();
  $("righe").innerHTML = lista.map(riga).join("");
  $("vuoto").hidden = lista.length > 0;
  document.querySelectorAll("th[data-k]").forEach(th =>
    th.setAttribute("aria-sort", th.dataset.k === ordine.k ? (ordine.dir > 0 ? "ascending" : "descending") : "none"));
  statistiche(lista);
  aggiornaMappa(lista);
  if (selezionato) evidenzia(selezionato, false);
}

document.querySelectorAll("th[data-k]").forEach(th => th.addEventListener("click", () => {
  const k = th.dataset.k;
  ordine = ordine.k === k ? {k, dir: -ordine.dir} : {k, dir: ["titolo","comune","provincia","tribunale","data_asta"].includes(k) ? 1 : -1};
  aggiorna();
}));
$("righe").addEventListener("click", e => {
  if (e.target.closest("a")) return;
  const tr = e.target.closest("tr"); if (tr) evidenzia(tr.dataset.id, true);
});
document.querySelectorAll(".filtri input, .filtri select").forEach(el => el.addEventListener("input", aggiorna));
aggiorna();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    raise SystemExit(main())
