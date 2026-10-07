<?php
declare(strict_types=1);
defined('APP') || exit;

const TIPI_SPESA = [
    'ordinaria' => 'Ordinaria',
    'straordinaria' => 'Straordinaria',
];

const SOGGETTI = [
    'proprietario' => 'Proprietario',
    'inquilino' => 'Inquilino',
];

const METODI_PAGAMENTO = [
    'bonifico' => 'Bonifico',
    'contanti' => 'Contanti',
    'assegno' => 'Assegno',
    'pos' => 'POS / carta',
    'rid' => 'RID / addebito',
    'altro' => 'Altro',
];

const GIORNI_SCADENZA_QUOTE = 30;   // scadenza predefinita delle quote dopo la data della spesa

const ALLEGATI_MIME = [
    'application/pdf' => 'pdf',
    'image/jpeg' => 'jpg',
    'image/png' => 'png',
    'image/webp' => 'webp',
    'image/heic' => 'heic',
    'image/heif' => 'heif',
];

/*
 * Tipologie iniziali di ogni condominio: [nome, tipo, tabella, % a carico inquilino].
 * Le percentuali seguono la prassi della L. 392/1978 (art. 9) e della tabella
 * oneri accessori Confedilizia-Sunia; sono solo valori di partenza, modificabili.
 */
const CATEGORIE_PREDEFINITE = [
    ['Pulizia scale e parti comuni', 'ordinaria', 'Scale', 100],
    ['Energia elettrica parti comuni', 'ordinaria', 'Generale', 100],
    ['Ascensore: consumi e manutenzione ordinaria', 'ordinaria', 'Scale', 100],
    ['Riscaldamento: combustibile e conduzione', 'ordinaria', 'Riscaldamento', 100],
    ['Acqua', 'ordinaria', 'Generale', 100],
    ['Giardino e verde', 'ordinaria', 'Generale', 100],
    ['Portierato', 'ordinaria', 'Generale', 90],
    ['Compenso amministratore', 'ordinaria', 'Generale', 0],
    ['Assicurazione fabbricato', 'ordinaria', 'Generale', 0],
    ['Spese bancarie e postali', 'ordinaria', 'Generale', 0],
    ['Piccola manutenzione parti comuni', 'ordinaria', 'Generale', 0],
    ['Lavori straordinari', 'straordinaria', 'Generale', 0],
    ['Sostituzione impianti', 'straordinaria', 'Generale', 0],
];

function categorie_predefinite(array $tabelle): array
{
    $byName = [];
    foreach ($tabelle as $t) {
        $byName[mb_strtolower($t['nome'])] = $t['id'];
    }
    $first = $tabelle[0]['id'] ?? '';
    $out = [];
    foreach (CATEGORIE_PREDEFINITE as [$nome, $tipo, $tab, $inq]) {
        $out[] = [
            'id' => Store::newId(),
            'nome' => $nome,
            'tipo' => $tipo,
            'tabella_id' => $byName[mb_strtolower($tab)] ?? $first,
            'quota_inquilino' => $inq,
        ];
    }
    return $out;
}

function categoria_find(array $c, string $id): ?array
{
    foreach ($c['categorie'] as $k) {
        if ($k['id'] === $id) {
            return $k;
        }
    }
    return null;
}

function categorie_sorted(array $c): array
{
    $list = $c['categorie'];
    usort($list, function ($a, $b) {
        return strcmp($a['tipo'], $b['tipo']) ?: strnatcasecmp($a['nome'], $b['nome']);
    });
    return $list;
}

function categoria_in_uso(array $c, string $id): ?string
{
    foreach ($c['uscite'] as $s) {
        if ($s['categoria_id'] === $id) {
            return 'è usata da almeno una spesa';
        }
    }
    return null;
}

/** @return array{0: array, 1: string[]} */
function categoria_validate(array $in, array $c, ?string $selfId): array
{
    $errors = [];
    $k = [
        'nome' => trim((string) ($in['nome'] ?? '')),
        'tipo' => (string) ($in['tipo'] ?? ''),
        'tabella_id' => (string) ($in['tabella_id'] ?? ''),
        'quota_inquilino' => (int) ($in['quota_inquilino'] ?? -1),
    ];
    if ($k['nome'] === '' || mb_strlen($k['nome']) > 80) {
        $errors[] = 'Il nome della tipologia è obbligatorio (max 80 caratteri).';
    }
    foreach ($c['categorie'] as $o) {
        if ($o['id'] !== $selfId && strcasecmp($o['nome'], $k['nome']) === 0) {
            $errors[] = 'Esiste già una tipologia "' . $o['nome'] . '".';
        }
    }
    if (!isset(TIPI_SPESA[$k['tipo']])) {
        $errors[] = 'Scegli se la tipologia è ordinaria o straordinaria.';
    }
    if (tabella_find($c, $k['tabella_id']) === null) {
        $errors[] = 'Scegli la tabella millesimale predefinita.';
    }
    if (!preg_match('/^\d{1,3}$/', trim((string) ($in['quota_inquilino'] ?? ''))) || $k['quota_inquilino'] > 100) {
        $errors[] = 'La quota a carico dell\'inquilino deve essere una percentuale tra 0 e 100.';
    }
    return [$k, $errors];
}

// ----------------------------------------------------------------------
// Riparto

/**
 * Ripartisce $importo (centesimi) tra le unità secondo i millesimi della tabella,
 * poi divide ogni quota tra proprietario e inquilino.
 * Usa il metodo dei resti maggiori: la somma delle quote è sempre esattamente $importo.
 * Restituisce [] se nessuna unità ha millesimi nella tabella.
 */
function riparto_calcola(array $c, string $tabellaId, int $importo, int $quotaInquilino): array
{
    $parti = [];
    $tot = 0.0;
    foreach (unita_sorted($c) as $u) {
        $m = (float) ($u['millesimi'][$tabellaId] ?? 0);
        if ($m > 0) {
            $parti[] = ['u' => $u, 'm' => $m];
            $tot += $m;
        }
    }
    if ($tot <= 0) {
        return [];
    }

    $assegnato = 0;
    foreach ($parti as $i => $p) {
        $esatto = $importo * $p['m'] / $tot;
        $parti[$i]['quota'] = (int) floor($esatto);
        $parti[$i]['resto'] = $esatto - floor($esatto);
        $assegnato += $parti[$i]['quota'];
    }
    $ordine = array_keys($parti);
    usort($ordine, function ($a, $b) use ($parti) {
        return $parti[$b]['resto'] <=> $parti[$a]['resto'] ?: $a <=> $b;
    });
    for ($k = 0, $diff = $importo - $assegnato; $k < $diff; $k++) {
        $parti[$ordine[$k % count($ordine)]]['quota']++;
    }

    $out = [];
    foreach ($parti as $p) {
        $u = $p['u'];
        $haInquilino = $u['inquilino']['nome'] !== '';
        $inq = $haInquilino ? (int) round($p['quota'] * $quotaInquilino / 100) : 0;
        $out[] = [
            'unita_id' => $u['id'],
            'millesimi' => $p['m'],
            'quota' => $p['quota'],
            'prop' => $p['quota'] - $inq,
            'inq' => $inq,
            'prop_nome' => $u['proprietario']['nome'],
            'inq_nome' => $haInquilino ? $u['inquilino']['nome'] : '',
        ];
    }
    return $out;
}

// ----------------------------------------------------------------------
// Spese (uscite)

function uscita_find(array $c, string $id): ?array
{
    foreach ($c['uscite'] as $s) {
        if ($s['id'] === $id) {
            return $s;
        }
    }
    return null;
}

/**
 * @return array{0: array, 1: string[]} [campi della spesa (senza id/allegati/riparto), errori]
 */
function uscita_validate(array $in, array $c): array
{
    $errors = [];
    $s = [
        'data' => parse_date((string) ($in['data'] ?? '')),
        'categoria_id' => (string) ($in['categoria_id'] ?? ''),
        'fornitore' => trim((string) ($in['fornitore'] ?? '')),
        'descrizione' => trim((string) ($in['descrizione'] ?? '')),
        'importo' => parse_money((string) ($in['importo'] ?? '')),
        'tabella_id' => (string) ($in['tabella_id'] ?? ''),
        'quota_inquilino' => (int) ($in['quota_inquilino'] ?? 0),
        'scadenza' => trim((string) ($in['scadenza'] ?? '')),
        'pagata' => !empty($in['pagata']),
        'data_pagamento' => trim((string) ($in['data_pagamento'] ?? '')),
    ];
    if ($s['data'] === null) {
        $errors[] = 'Data della spesa non valida.';
    }
    $cat = categoria_find($c, $s['categoria_id']);
    if ($cat === null) {
        $errors[] = 'Scegli la tipologia di spesa.';
    }
    $s['tipo'] = $cat['tipo'] ?? 'ordinaria';
    if ($s['importo'] === null || $s['importo'] <= 0) {
        $errors[] = "L'importo deve essere maggiore di zero (es. 1.234,56).";
    }
    if (tabella_find($c, $s['tabella_id']) === null) {
        $errors[] = 'Scegli la tabella millesimale di riparto.';
    } elseif (tabella_totale($c, $s['tabella_id']) <= 0) {
        $errors[] = 'La tabella scelta non ha millesimi assegnati: compila prima i millesimi.';
    }
    if ($s['quota_inquilino'] < 0 || $s['quota_inquilino'] > 100) {
        $errors[] = 'La quota inquilino deve essere tra 0 e 100%.';
    }
    if ($s['scadenza'] === '') {
        $s['scadenza'] = $s['data'] ? date('Y-m-d', strtotime($s['data'] . ' +' . GIORNI_SCADENZA_QUOTE . ' days')) : null;
    } else {
        $s['scadenza'] = parse_date($s['scadenza']);
        if ($s['scadenza'] === null) {
            $errors[] = 'Scadenza delle quote non valida.';
        }
    }
    if ($s['pagata']) {
        $s['data_pagamento'] = $s['data_pagamento'] === '' ? today() : parse_date($s['data_pagamento']);
        if ($s['data_pagamento'] === null) {
            $errors[] = 'Data di pagamento non valida.';
        }
    } else {
        $s['data_pagamento'] = '';
    }
    return [$s, $errors];
}

/** Spese filtrate e ordinate per data decrescente. */
function uscite_filtra(array $c, array $f): array
{
    $out = array_filter($c['uscite'], function ($s) use ($f) {
        return (($f['anno'] ?? '') === '' || substr($s['data'], 0, 4) === $f['anno'])
            && (($f['stato'] ?? '') === '' || ($f['stato'] === 'pagate') === (bool) $s['pagata'])
            && (($f['categoria'] ?? '') === '' || $s['categoria_id'] === $f['categoria'])
            && (($f['tipo'] ?? '') === '' || $s['tipo'] === $f['tipo']);
    });
    usort($out, function ($a, $b) {
        return strcmp($b['data'], $a['data']);
    });
    return $out;
}

/** Anni presenti nei movimenti, dal più recente (include sempre l'anno corrente). */
function anni_movimenti(array $c): array
{
    $anni = [date('Y') => true];
    foreach ($c['uscite'] as $s) {
        $anni[substr($s['data'], 0, 4)] = true;
    }
    foreach ($c['versamenti'] as $v) {
        $anni[substr($v['data'], 0, 4)] = true;
    }
    $anni = array_map('strval', array_keys($anni));
    rsort($anni);
    return $anni;
}

// ----------------------------------------------------------------------
// Versamenti (incassi)

function versamento_find(array $c, string $id): ?array
{
    foreach ($c['versamenti'] as $v) {
        if ($v['id'] === $id) {
            return $v;
        }
    }
    return null;
}

/** @return array{0: array, 1: string[]} */
function versamento_validate(array $in, array $c): array
{
    $errors = [];
    [$uid, $sog] = array_pad(explode('|', (string) ($in['chi'] ?? ''), 2), 2, '');
    $v = [
        'unita_id' => $uid,
        'soggetto' => $sog,
        'data' => parse_date((string) ($in['data'] ?? '')),
        'importo' => parse_money((string) ($in['importo'] ?? '')),
        'metodo' => (string) ($in['metodo'] ?? ''),
        'note' => trim((string) ($in['note'] ?? '')),
    ];
    if (unita_find($c, $uid) === null || !isset(SOGGETTI[$sog])) {
        $errors[] = 'Scegli chi ha versato.';
    }
    if ($v['data'] === null) {
        $errors[] = 'Data del versamento non valida.';
    }
    if ($v['importo'] === null || $v['importo'] <= 0) {
        $errors[] = "L'importo deve essere maggiore di zero.";
    }
    if (!isset(METODI_PAGAMENTO[$v['metodo']])) {
        $errors[] = 'Scegli il metodo di pagamento.';
    }
    return [$v, $errors];
}

// ----------------------------------------------------------------------
// Situazione dei condòmini, morosità, cassa

/**
 * Posizione di ogni proprietario/inquilino: addebiti dalle spese (riparto salvato),
 * versamenti, saldo e quota scaduta. I versamenti coprono gli addebiti dal più
 * vecchio (per scadenza): ciò che resta scoperto oltre la scadenza è morosità.
 *
 * @return array<string, array> chiave "unita_id|soggetto"
 */
function situazione(array $c, ?string $oggi = null): array
{
    $oggi = $oggi ?? today();
    $pos = [];
    $get = function (string $uid, string $sog, string $nome) use (&$pos, $c) {
        $key = $uid . '|' . $sog;
        if (!isset($pos[$key])) {
            $u = unita_find($c, $uid);
            $nomeAttuale = $u ? $u[$sog]['nome'] : '';
            $pos[$key] = [
                'unita_id' => $uid, 'soggetto' => $sog, 'unita' => $u,
                'nome' => $nomeAttuale !== '' ? $nomeAttuale : $nome,
                'ordinarie' => 0, 'straordinarie' => 0, 'addebitato' => 0, 'versato' => 0,
                'saldo' => 0, 'scaduto' => 0, 'prima_scadenza' => '', 'addebiti' => [],
            ];
        }
        return $key;
    };

    foreach ($c['uscite'] as $s) {
        foreach ($s['riparto'] ?? [] as $r) {
            foreach (['proprietario' => 'prop', 'inquilino' => 'inq'] as $sog => $f) {
                if ($r[$f] <= 0) {
                    continue;
                }
                $k = $get($r['unita_id'], $sog, $r[$f . '_nome']);
                $pos[$k][$s['tipo'] === 'straordinaria' ? 'straordinarie' : 'ordinarie'] += $r[$f];
                $pos[$k]['addebitato'] += $r[$f];
                $pos[$k]['addebiti'][] = ['scadenza' => $s['scadenza'], 'importo' => $r[$f]];
            }
        }
    }
    foreach ($c['versamenti'] as $v) {
        $k = $get($v['unita_id'], $v['soggetto'], '');
        $pos[$k]['versato'] += $v['importo'];
    }

    foreach ($pos as &$p) {
        $p['saldo'] = $p['addebitato'] - $p['versato'];
        usort($p['addebiti'], function ($a, $b) {
            return strcmp($a['scadenza'], $b['scadenza']);
        });
        $copertura = $p['versato'];
        foreach ($p['addebiti'] as $a) {
            $coperto = min($copertura, $a['importo']);
            $copertura -= $coperto;
            if ($a['scadenza'] < $oggi && $coperto < $a['importo']) {
                $p['scaduto'] += $a['importo'] - $coperto;
                if ($p['prima_scadenza'] === '') {
                    $p['prima_scadenza'] = $a['scadenza'];
                }
            }
        }
    }
    unset($p);

    // Ordine: per unità (come nell'elenco unità), proprietario prima dell'inquilino.
    $ordine = [];
    foreach (unita_sorted($c) as $i => $u) {
        $ordine[$u['id']] = $i;
    }
    uasort($pos, function ($a, $b) use ($ordine) {
        return ($ordine[$a['unita_id']] ?? PHP_INT_MAX) <=> ($ordine[$b['unita_id']] ?? PHP_INT_MAX)
            ?: strcmp($b['soggetto'], $a['soggetto']);
    });
    return $pos;
}

/** Saldo di cassa: saldo iniziale + versamenti incassati - spese pagate. */
function cassa_saldo(array $c): int
{
    $saldo = (int) $c['saldo_iniziale'];
    foreach ($c['versamenti'] as $v) {
        $saldo += $v['importo'];
    }
    foreach ($c['uscite'] as $s) {
        if ($s['pagata']) {
            $saldo -= $s['importo'];
        }
    }
    return $saldo;
}

/** Riepilogo per la dashboard. */
function condominio_riepilogo(array $c): array
{
    $daPagare = array_values(array_filter($c['uscite'], function ($s) {
        return !$s['pagata'];
    }));
    usort($daPagare, function ($a, $b) {
        return strcmp($a['data'], $b['data']);
    });
    $morosi = array_filter(situazione($c), function ($p) {
        return $p['scaduto'] > 0;
    });
    return [
        'cassa' => cassa_saldo($c),
        'da_pagare' => $daPagare,
        'da_pagare_tot' => array_sum(array_column($daPagare, 'importo')),
        'morosi' => $morosi,
        'scaduto_tot' => array_sum(array_column($morosi, 'scaduto')),
    ];
}

// ----------------------------------------------------------------------
// Allegati (uploads/<condominio>/<id>.<ext>, serviti solo da pages/allegato.php)

function allegati_dir(string $cid): string
{
    return UPLOAD_DIR . '/' . $cid;
}

/**
 * Normalizza $_FILES[campo] con upload multiplo in un elenco di file.
 * @return array<int, array{name: string, tmp_name: string, error: int, size: int}>
 */
function uploaded_files(string $field): array
{
    $f = $_FILES[$field] ?? null;
    if (!is_array($f) || !isset($f['name'])) {
        return [];
    }
    if (!is_array($f['name'])) {
        $f = array_map(function ($v) {
            return [$v];
        }, $f);
    }
    $out = [];
    foreach ($f['name'] as $i => $name) {
        if ((int) $f['error'][$i] === UPLOAD_ERR_NO_FILE) {
            continue;
        }
        $out[] = ['name' => (string) $name, 'tmp_name' => (string) $f['tmp_name'][$i], 'error' => (int) $f['error'][$i], 'size' => (int) $f['size'][$i]];
    }
    return $out;
}

/** Controlla un file caricato; restituisce il messaggio d'errore o null. */
function allegato_problema(array $file): ?string
{
    $nome = $file['name'];
    if ($file['error'] === UPLOAD_ERR_INI_SIZE || $file['error'] === UPLOAD_ERR_FORM_SIZE || $file['size'] > UPLOAD_MAX_BYTES) {
        return "\"$nome\" è troppo grande (max " . format_bytes(min(UPLOAD_MAX_BYTES, upload_limit_bytes())) . ').';
    }
    if ($file['error'] !== UPLOAD_ERR_OK || !is_uploaded_file($file['tmp_name'])) {
        return "Caricamento di \"$nome\" non riuscito.";
    }
    if (!isset(ALLEGATI_MIME[allegato_mime($file['tmp_name'])])) {
        return "\"$nome\": sono ammessi solo PDF e immagini (JPG, PNG, WEBP, HEIC).";
    }
    return null;
}

function allegato_mime(string $path): string
{
    if (!function_exists('finfo_open')) {
        throw new RuntimeException("L'estensione PHP fileinfo non è attiva: necessaria per gli allegati.");
    }
    $fi = finfo_open(FILEINFO_MIME_TYPE);
    $mime = (string) finfo_file($fi, $path);
    finfo_close($fi);
    return $mime;
}

/** Sposta un file già validato nella cartella del condominio e ne restituisce i metadati. */
function allegato_salva(string $cid, array $file): array
{
    $dir = allegati_dir($cid);
    if (!is_dir($dir) && !@mkdir($dir, 0750, true) && !is_dir($dir)) {
        throw new RuntimeException('Impossibile creare la cartella degli allegati.');
    }
    $mime = allegato_mime($file['tmp_name']);
    $id = Store::newId();
    $stored = $id . '.' . ALLEGATI_MIME[$mime];
    if (!move_uploaded_file($file['tmp_name'], $dir . '/' . $stored)) {
        throw new RuntimeException('Impossibile salvare l\'allegato "' . $file['name'] . '".');
    }
    $nome = preg_replace('/[^\pL\pN ._()\-]+/u', '_', basename($file['name'])) ?: $stored;
    return ['id' => $id, 'nome' => mb_substr($nome, 0, 120), 'mime' => $mime, 'size' => $file['size'], 'file' => $stored];
}

function allegato_path(string $cid, array $a): ?string
{
    if (!is_id($cid) || !preg_match('/^[a-f0-9]{16}\.[a-z]{3,4}$/', (string) ($a['file'] ?? ''))) {
        return null;
    }
    $p = allegati_dir($cid) . '/' . $a['file'];
    return is_file($p) ? $p : null;
}

function allegato_elimina_file(string $cid, array $a): void
{
    $p = allegato_path($cid, $a);
    if ($p !== null) {
        @unlink($p);
    }
}

/** Limite effettivo di upload imposto da PHP (upload_max_filesize / post_max_size). */
function upload_limit_bytes(): int
{
    $toBytes = function (string $v): int {
        $v = trim($v);
        $n = (int) $v;
        switch (strtolower(substr($v, -1))) {
            case 'g': return $n * 1073741824;
            case 'm': return $n * 1048576;
            case 'k': return $n * 1024;
        }
        return $n;
    };
    $limits = array_filter([$toBytes((string) ini_get('upload_max_filesize')), $toBytes((string) ini_get('post_max_size'))]);
    return $limits ? min($limits) : UPLOAD_MAX_BYTES;
}
