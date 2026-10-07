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

const FREQUENZE = [            // mesi tra una scadenza e la successiva (0 = una sola volta)
    'mensile' => ['Mensile', 1],
    'bimestrale' => ['Bimestrale', 2],
    'trimestrale' => ['Trimestrale', 3],
    'semestrale' => ['Semestrale', 6],
    'annuale' => ['Annuale', 12],
    'una_tantum' => ['Una tantum', 0],
];

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
    if ($s['scadenza'] !== '') {   // vuota = fine del trimestre della spesa
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
        'rif' => (string) ($in['rif'] ?? ''),
    ];
    if ($v['rif'] !== '' && !preg_match('/^\d{4}-T[1-4]$/', $v['rif'])) {
        $errors[] = 'Trimestre di riferimento non valido.';
    }
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
 * versamenti, saldo e quota scaduta.
 *
 * Copertura degli addebiti: un versamento destinato a un trimestre ("rif") copre
 * prima le quote di quel trimestre; tutto il resto copre le quote dalla scadenza
 * più vecchia. Ciò che resta scoperto oltre la scadenza è morosità.
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
                'saldo' => 0, 'scaduto' => 0, 'prima_scadenza' => '', 'addebiti' => [], 'versamenti' => [],
            ];
        }
        return $key;
    };

    foreach ($c['uscite'] as $s) {
        $scad = scadenza_quote($s);
        foreach ($s['riparto'] ?? [] as $r) {
            foreach (['proprietario' => 'prop', 'inquilino' => 'inq'] as $sog => $f) {
                if ($r[$f] <= 0) {
                    continue;
                }
                $k = $get($r['unita_id'], $sog, $r[$f . '_nome']);
                $pos[$k][$s['tipo'] === 'straordinaria' ? 'straordinarie' : 'ordinarie'] += $r[$f];
                $pos[$k]['addebitato'] += $r[$f];
                $pos[$k]['addebiti'][] = ['scadenza' => $scad, 'trimestre' => trimestre_di($scad), 'importo' => $r[$f], 'coperto' => 0];
            }
        }
    }
    foreach ($c['versamenti'] as $v) {
        $k = $get($v['unita_id'], $v['soggetto'], '');
        $pos[$k]['versato'] += $v['importo'];
        $pos[$k]['versamenti'][] = $v;
    }

    foreach ($pos as &$p) {
        $p['saldo'] = $p['addebitato'] - $p['versato'];
        usort($p['addebiti'], function ($a, $b) {
            return strcmp($a['scadenza'], $b['scadenza']);
        });
        $libero = 0;
        foreach ($p['versamenti'] as $v) {      // 1) versamenti destinati a un trimestre
            $resto = $v['importo'];
            if (($v['rif'] ?? '') !== '') {
                foreach ($p['addebiti'] as &$a) {
                    if ($a['trimestre'] === $v['rif'] && $resto > 0) {
                        $x = min($resto, $a['importo'] - $a['coperto']);
                        $a['coperto'] += $x;
                        $resto -= $x;
                    }
                }
                unset($a);
            }
            $libero += $resto;
        }
        foreach ($p['addebiti'] as &$a) {       // 2) il resto, dalla scadenza più vecchia
            $x = min($libero, $a['importo'] - $a['coperto']);
            $a['coperto'] += $x;
            $libero -= $x;
            if ($a['scadenza'] < $oggi && $a['coperto'] < $a['importo']) {
                $p['scaduto'] += $a['importo'] - $a['coperto'];
                if ($p['prima_scadenza'] === '') {
                    $p['prima_scadenza'] = $a['scadenza'];
                }
            }
        }
        unset($a);
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

// ----------------------------------------------------------------------
// Trimestri e rate trimestrali

/** "2026-05-14" -> "2026-T2" */
function trimestre_di(string $ymd): string
{
    return substr($ymd, 0, 4) . '-T' . (int) ceil((int) substr($ymd, 5, 2) / 3);
}

/** "2026-T2" -> ["2026-04-01", "2026-06-30"] */
function trimestre_intervallo(string $t): array
{
    $y = (int) substr($t, 0, 4);
    $q = (int) substr($t, -1);
    $fine = new DateTime(sprintf('%04d-%02d-01', $y, $q * 3));
    return [sprintf('%04d-%02d-01', $y, $q * 3 - 2), $fine->format('Y-m-t')];
}

/** "2026-T2" -> "2° trimestre 2026" */
function trimestre_label(string $t, bool $breve = false): string
{
    $q = (int) substr($t, -1);
    return $breve ? $q . '° trim.' : $q . '° trimestre ' . substr($t, 0, 4);
}

/** Scadenza delle quote di una spesa: quella indicata o la fine del trimestre della spesa. */
function scadenza_quote(array $s): string
{
    return ($s['scadenza'] ?? '') !== '' ? $s['scadenza'] : trimestre_intervallo(trimestre_di($s['data']))[1];
}

/**
 * Rate trimestrali dell'anno per ogni posizione:
 * [chiave => [ 'p' => posizione, 'T1'..'T4' => {dovuto, coperto, scadenza, stato} ]]
 * stato: '' (nulla da pagare), pagata, parziale, da_pagare, scaduta
 */
function rate_trimestrali(array $c, string $anno, ?string $oggi = null): array
{
    $oggi = $oggi ?? today();
    $out = [];
    foreach (situazione($c, $oggi) as $key => $p) {
        $row = ['p' => $p];
        $tot = 0;
        for ($q = 1; $q <= 4; $q++) {
            $t = $anno . '-T' . $q;
            $rata = ['trimestre' => $t, 'dovuto' => 0, 'coperto' => 0, 'scadenza' => trimestre_intervallo($t)[1]];
            foreach ($p['addebiti'] as $a) {
                if ($a['trimestre'] === $t) {
                    $rata['dovuto'] += $a['importo'];
                    $rata['coperto'] += $a['coperto'];
                    $rata['scadenza'] = min($rata['scadenza'], $a['scadenza']);
                }
            }
            $rata['residuo'] = $rata['dovuto'] - $rata['coperto'];
            $rata['stato'] = $rata['dovuto'] === 0 ? ''
                : ($rata['residuo'] <= 0 ? 'pagata'
                : ($rata['scadenza'] < $oggi ? 'scaduta'
                : ($rata['coperto'] > 0 ? 'parziale' : 'da_pagare')));
            $row['T' . $q] = $rata;
            $tot += $rata['dovuto'];
        }
        if ($tot > 0) {
            $out[$key] = $row;
        }
    }
    return $out;
}

/** Trimestri selezionabili come riferimento di un versamento (anno scorso, corrente, prossimo). */
function trimestri_selezionabili(): array
{
    $out = [];
    $y = (int) date('Y');
    for ($a = $y - 1; $a <= $y + 1; $a++) {
        for ($q = 1; $q <= 4; $q++) {
            $out[] = $a . '-T' . $q;
        }
    }
    return $out;
}

// ----------------------------------------------------------------------
// Affitti e altre entrate ricorrenti

function entrata_find(array $c, string $id): ?array
{
    foreach ($c['entrate'] as $e) {
        if ($e['id'] === $id) {
            return $e;
        }
    }
    return null;
}

/** Aggiunge $n mesi mantenendo il giorno (ridotto all'ultimo del mese se non esiste). */
function add_months(string $ymd, int $n, int $giorno): string
{
    $y = (int) substr($ymd, 0, 4);
    $m = (int) substr($ymd, 5, 2) + $n;
    $y += intdiv($m - 1, 12);
    $m = ($m - 1) % 12 + 1;
    $ultimo = (int) (new DateTime(sprintf('%04d-%02d-01', $y, $m)))->format('t');
    return sprintf('%04d-%02d-%02d', $y, $m, min($giorno, $ultimo));
}

/**
 * Scadenze di un'entrata fino a $fino (compreso), con lo stato di incasso.
 * Include anche eventuali incassi registrati su date non più in calendario
 * (se nel frattempo è cambiata la cadenza).
 * @return array<int, array{data: string, importo: int, pagamento: ?array}>
 */
function entrata_scadenze(array $e, string $fino): array
{
    $date = [];
    $mesi = FREQUENZE[$e['frequenza']][1] ?? 0;
    $giorno = (int) substr($e['data_inizio'], 8, 2);
    $limite = $e['data_fine'] !== '' ? min($fino, $e['data_fine']) : $fino;
    for ($i = 0, $d = $e['data_inizio']; $d <= $limite && $i < 1200; $i++) {
        $date[$d] = true;
        if ($mesi === 0) {
            break;
        }
        $d = add_months($e['data_inizio'], $mesi * ($i + 1), $giorno);
    }
    foreach ($e['pagamenti'] as $d => $_) {
        if ($d <= $fino) {
            $date[$d] = true;
        }
    }
    ksort($date);
    $out = [];
    foreach (array_keys($date) as $d) {
        $pag = $e['pagamenti'][$d] ?? null;
        $out[] = ['data' => (string) $d, 'importo' => $pag['importo'] ?? $e['importo'], 'pagamento' => $pag];
    }
    return $out;
}

/** Prossima scadenza non incassata a partire da oggi, o null. */
function entrata_prossima(array $e, string $oggi): ?array
{
    $orizzonte = add_months($oggi, 13, (int) substr($oggi, 8, 2));
    foreach (entrata_scadenze($e, $orizzonte) as $sc) {
        if ($sc['data'] >= $oggi && $sc['pagamento'] === null) {
            return $sc;
        }
    }
    return null;
}

/** Scadenze passate non incassate di tutte le entrate. */
function entrate_scadute(array $c, ?string $oggi = null): array
{
    $oggi = $oggi ?? today();
    $out = [];
    foreach ($c['entrate'] as $e) {
        foreach (entrata_scadenze($e, $oggi) as $sc) {
            if ($sc['data'] < $oggi && $sc['pagamento'] === null) {
                $out[] = ['e' => $e] + $sc;
            }
        }
    }
    usort($out, function ($a, $b) {
        return strcmp($a['data'], $b['data']);
    });
    return $out;
}

/** @return array{0: array, 1: string[]} */
function entrata_validate(array $in, array $c): array
{
    $errors = [];
    $e = [
        'descrizione' => trim((string) ($in['descrizione'] ?? '')),
        'debitore' => trim((string) ($in['debitore'] ?? '')),
        'unita_id' => (string) ($in['unita_id'] ?? ''),
        'importo' => parse_money((string) ($in['importo'] ?? '')),
        'frequenza' => (string) ($in['frequenza'] ?? ''),
        'data_inizio' => parse_date((string) ($in['data_inizio'] ?? '')),
        'data_fine' => trim((string) ($in['data_fine'] ?? '')),
        'in_cassa' => !empty($in['in_cassa']),
        'note' => trim((string) ($in['note'] ?? '')),
    ];
    if ($e['descrizione'] === '') {
        $errors[] = 'La descrizione è obbligatoria (es. "Affitto locale portineria").';
    }
    if ($e['debitore'] === '') {
        $errors[] = 'Indica chi deve pagare.';
    }
    if ($e['unita_id'] !== '' && unita_find($c, $e['unita_id']) === null) {
        $errors[] = 'Unità non valida.';
    }
    if ($e['importo'] === null || $e['importo'] <= 0) {
        $errors[] = "L'importo deve essere maggiore di zero.";
    }
    if (!isset(FREQUENZE[$e['frequenza']])) {
        $errors[] = 'Scegli la cadenza.';
    }
    if ($e['data_inizio'] === null) {
        $errors[] = 'Data della prima scadenza non valida (gg/mm/aaaa).';
    }
    if ($e['data_fine'] !== '') {
        $e['data_fine'] = parse_date($e['data_fine']);
        if ($e['data_fine'] === null) {
            $errors[] = 'Data di fine non valida (gg/mm/aaaa).';
        } elseif ($e['data_inizio'] && $e['data_fine'] < $e['data_inizio']) {
            $errors[] = 'La data di fine è precedente alla prima scadenza.';
        }
    }
    return [$e, $errors];
}

/** Saldo di cassa: saldo iniziale + versamenti + altre entrate incassate (se in cassa) - spese pagate. */
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
    foreach ($c['entrate'] as $e) {
        if ($e['in_cassa']) {
            foreach ($e['pagamenti'] as $p) {
                $saldo += $p['importo'];
            }
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
    $entrateScadute = entrate_scadute($c);
    return [
        'entrate_scadute' => $entrateScadute,
        'entrate_scadute_tot' => array_sum(array_column($entrateScadute, 'importo')),
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
