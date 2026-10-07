<?php
declare(strict_types=1);
defined('APP') || exit;

/*
 * Report. Ogni report restituisce una lista di sezioni con la stessa struttura,
 * che viene poi mostrata a video, stampata o esportata in CSV:
 *
 *   ['titolo' => string, 'nota' => string,
 *    'colonne' => [[etichetta, tipo], ...]       tipo: text | money | date | num
 *    'righe'   => [['v' => [...valori], 'class' => ''], ...]
 *    'totale'  => [...valori] | null]
 *
 * Gli importi sono in centesimi, le date "aaaa-mm-gg": la formattazione è fatta
 * da chi visualizza (report_cell / report_csv).
 */

const REPORT_TIPI = [
    'estratto' => 'Estratto conto per condòmino',
    'rendiconto' => 'Rendiconto annuale',
    'entrate' => 'Affitti e altre entrate',
    'uscite' => 'Elenco spese',
    'versamenti' => 'Elenco versamenti',
    'situazione' => 'Situazione e morosità',
];

function report_riga(array $v, string $class = ''): array
{
    return ['v' => $v, 'class' => $class];
}

function nel_periodo(string $ymd, string $anno): bool
{
    return $anno === '' || substr($ymd, 0, 4) === $anno;
}

function periodo_label(string $anno): string
{
    return $anno === '' ? 'tutti gli anni' : 'anno ' . $anno;
}

// ----------------------------------------------------------------------
// Estratto conto

/**
 * Movimenti di una posizione ("unita_id|soggetto") o di un'intera unità ("unita_id|").
 * @return array<int, array{data: string, descrizione: string, scadenza: string, dare: int, avere: int}>
 */
function estratto_movimenti(array $c, string $uid, string $sog): array
{
    $mov = [];
    $catNome = array_column($c['categorie'], 'nome', 'id');
    foreach ($c['uscite'] as $s) {
        foreach ($s['riparto'] ?? [] as $r) {
            if ($r['unita_id'] !== $uid) {
                continue;
            }
            foreach (['proprietario' => 'prop', 'inquilino' => 'inq'] as $sg => $f) {
                if (($sog === '' || $sog === $sg) && $r[$f] > 0) {
                    $mov[] = [
                        'data' => $s['data'],
                        'descrizione' => trim(($catNome[$s['categoria_id']] ?? 'Spesa') . ($s['fornitore'] !== '' ? ' - ' . $s['fornitore'] : ''))
                            . ($s['tipo'] === 'straordinaria' ? ' (straordinaria)' : '') . ($sog === '' ? ' [' . strtolower(SOGGETTI[$sg]) . ']' : ''),
                        'scadenza' => scadenza_quote($s),
                        'dare' => $r[$f],
                        'avere' => 0,
                    ];
                }
            }
        }
    }
    foreach ($c['versamenti'] as $v) {
        if ($v['unita_id'] === $uid && ($sog === '' || $sog === $v['soggetto'])) {
            $mov[] = [
                'data' => $v['data'],
                'descrizione' => 'Versamento ' . strtolower(METODI_PAGAMENTO[$v['metodo']] ?? '')
                    . ($v['rif'] !== '' ? ' - rata ' . trimestre_label($v['rif']) : '')
                    . ($v['note'] !== '' && strcasecmp($v['note'], 'Rata ' . trimestre_label($v['rif'] ?: '0000-T0')) !== 0 ? ' - ' . $v['note'] : '')
                    . ($sog === '' ? ' [' . strtolower(SOGGETTI[$v['soggetto']]) . ']' : ''),
                'scadenza' => '',
                'dare' => 0,
                'avere' => $v['importo'],
            ];
        }
    }
    usort($mov, function ($a, $b) {
        return strcmp($a['data'], $b['data']) ?: $a['avere'] <=> $b['avere'];
    });
    return $mov;
}

/** Posizioni selezionabili per l'estratto: [chiave => etichetta]. */
function estratto_scelte(array $c): array
{
    $out = [];
    $sit = situazione($c);
    foreach (unita_sorted($c) as $u) {
        $out[$u['id'] . '|'] = unita_label($u) . ' - intera unità';
        foreach (SOGGETTI as $sog => $label) {
            $k = $u['id'] . '|' . $sog;
            if (isset($sit[$k]) || $u[$sog]['nome'] !== '') {
                $out[$k] = unita_label($u) . ' - ' . ($sit[$k]['nome'] ?? $u[$sog]['nome']) . ' (' . strtolower($label) . ')';
            }
        }
    }
    return $out;
}

function report_estratto(array $c, string $chi, string $anno): array
{
    $scelte = estratto_scelte($c);
    $chiavi = $chi === 'tutti'
        ? array_keys(array_intersect_key(situazione($c), $scelte))
        : (isset($scelte[$chi]) ? [$chi] : []);
    $sezioni = [];
    foreach ($chiavi as $key) {
        [$uid, $sog] = explode('|', $key, 2);
        $saldo = 0;
        $righe = [];
        $tot = ['dare' => 0, 'avere' => 0];
        $precedente = 0;
        foreach (estratto_movimenti($c, $uid, $sog) as $m) {
            if ($anno !== '' && substr($m['data'], 0, 4) < $anno) {
                $precedente += $m['dare'] - $m['avere'];
            }
        }
        if ($anno !== '') {
            $saldo = $precedente;
            $righe[] = report_riga(['', 'Saldo al 01/01/' . $anno . ($precedente > 0 ? ' (da versare)' : ($precedente < 0 ? ' (a credito)' : '')), '', null, null, $saldo], 'row-sub');
        }
        foreach (estratto_movimenti($c, $uid, $sog) as $m) {
            if (!nel_periodo($m['data'], $anno)) {
                continue;
            }
            $saldo += $m['dare'] - $m['avere'];
            $tot['dare'] += $m['dare'];
            $tot['avere'] += $m['avere'];
            $righe[] = report_riga([$m['data'], $m['descrizione'], $m['scadenza'], $m['dare'] ?: null, $m['avere'] ?: null, $saldo]);
        }
        $sezioni[] = [
            'titolo' => 'Estratto conto: ' . $scelte[$key],
            'nota' => periodo_label($anno) . '. Saldo positivo = importo da versare, negativo = a credito.',
            'colonne' => [['Data', 'date'], ['Descrizione', 'text'], ['Scadenza', 'date'], ['Addebiti', 'money'], ['Versamenti', 'money'], ['Saldo', 'money']],
            'righe' => $righe,
            'totale' => ['', 'Totale periodo', '', $tot['dare'], $tot['avere'], $saldo],
        ];
    }
    return $sezioni;
}

// ----------------------------------------------------------------------
// Rendiconto annuale

/** Movimenti di cassa con la loro data effettiva: [data, importo (+/-)]. */
function movimenti_cassa(array $c): array
{
    $mov = [];
    foreach ($c['versamenti'] as $v) {
        $mov[] = ['data' => $v['data'], 'importo' => $v['importo'], 'tipo' => 'versamenti'];
    }
    foreach ($c['uscite'] as $s) {
        if ($s['pagata']) {
            $mov[] = ['data' => $s['data_pagamento'] ?: $s['data'], 'importo' => -$s['importo'], 'tipo' => 'spese'];
        }
    }
    foreach ($c['entrate'] as $e) {
        if ($e['in_cassa']) {
            foreach ($e['pagamenti'] as $p) {
                $mov[] = ['data' => $p['data'], 'importo' => $p['importo'], 'tipo' => 'entrate'];
            }
        }
    }
    return $mov;
}

function report_rendiconto(array $c, string $anno): array
{
    $sezioni = [];
    $catNome = array_column($c['categorie'], 'nome', 'id');

    // Spese per tipologia (competenza: data della spesa)
    foreach (TIPI_SPESA as $tipo => $tipoLabel) {
        $per = [];
        foreach ($c['uscite'] as $s) {
            if ($s['tipo'] !== $tipo || !nel_periodo($s['data'], $anno)) {
                continue;
            }
            $k = $catNome[$s['categoria_id']] ?? '(tipologia eliminata)';
            $per[$k] = $per[$k] ?? ['n' => 0, 'tot' => 0, 'pagato' => 0];
            $per[$k]['n']++;
            $per[$k]['tot'] += $s['importo'];
            $per[$k]['pagato'] += $s['pagata'] ? $s['importo'] : 0;
        }
        ksort($per, SORT_NATURAL | SORT_FLAG_CASE);
        $plurale = substr(strtolower($tipoLabel), 0, -1) . 'e';   // ordinaria -> ordinarie
        $righe = [];
        $t = ['n' => 0, 'tot' => 0, 'pagato' => 0];
        foreach ($per as $nome => $x) {
            $righe[] = report_riga([$nome, $x['n'], $x['tot'], $x['pagato'], $x['tot'] - $x['pagato']]);
            foreach ($t as $k => $_) {
                $t[$k] += $x[$k];
            }
        }
        $sezioni[] = [
            'titolo' => 'Spese ' . $plurale . ' per tipologia',
            'nota' => 'Per data della spesa, ' . periodo_label($anno) . '.',
            'colonne' => [['Tipologia', 'text'], ['N. spese', 'num'], ['Totale', 'money'], ['Pagato', 'money'], ['Da pagare', 'money']],
            'righe' => $righe,
            'totale' => ['Totale spese ' . $plurale, $t['n'], $t['tot'], $t['pagato'], $t['tot'] - $t['pagato']],
        ];
    }

    // Movimenti di cassa (per data di incasso/pagamento)
    $iniziale = (int) $c['saldo_iniziale'];
    $nel = ['versamenti' => 0, 'entrate' => 0, 'spese' => 0];
    foreach (movimenti_cassa($c) as $m) {
        if ($anno !== '' && substr($m['data'], 0, 4) < $anno) {
            $iniziale += $m['importo'];
        } elseif (nel_periodo($m['data'], $anno)) {
            $nel[$m['tipo']] += $m['importo'];
        }
    }
    $finale = $iniziale + array_sum($nel);
    $sezioni[] = [
        'titolo' => 'Movimenti di cassa',
        'nota' => 'Per data effettiva di incasso o pagamento. Le entrate segnate "fuori cassa" non sono incluse.',
        'colonne' => [['Voce', 'text'], ['Importo', 'money']],
        'righe' => [
            report_riga([$anno !== '' ? 'Saldo di cassa al 01/01/' . $anno : 'Saldo di cassa iniziale', $iniziale], 'row-sub'),
            report_riga(['Entrate: versamenti dei condòmini', $nel['versamenti']]),
            report_riga(['Entrate: affitti e altre entrate incassate', $nel['entrate']]),
            report_riga(['Uscite: spese pagate', $nel['spese']]),
        ],
        'totale' => [$anno !== '' ? 'Saldo di cassa al 31/12/' . $anno : 'Saldo di cassa attuale', $finale],
    ];

    // Riparto consuntivo per condòmino
    $per = [];
    foreach ($c['uscite'] as $s) {
        if (!nel_periodo($s['data'], $anno)) {
            continue;
        }
        foreach ($s['riparto'] ?? [] as $r) {
            foreach (['proprietario' => 'prop', 'inquilino' => 'inq'] as $sog => $f) {
                if ($r[$f] > 0) {
                    $k = $r['unita_id'] . '|' . $sog;
                    $per[$k] = $per[$k] ?? ['ord' => 0, 'str' => 0, 'vers' => 0, 'nome' => $r[$f . '_nome']];
                    $per[$k][$s['tipo'] === 'straordinaria' ? 'str' : 'ord'] += $r[$f];
                }
            }
        }
    }
    foreach ($c['versamenti'] as $v) {
        if (nel_periodo($v['data'], $anno)) {
            $k = $v['unita_id'] . '|' . $v['soggetto'];
            $per[$k] = $per[$k] ?? ['ord' => 0, 'str' => 0, 'vers' => 0, 'nome' => ''];
            $per[$k]['vers'] += $v['importo'];
        }
    }
    $sit = situazione($c);
    $righe = [];
    $t = ['ord' => 0, 'str' => 0, 'vers' => 0];
    foreach (array_keys($sit + $per) as $k) {
        if (!isset($per[$k])) {
            continue;
        }
        [$uid, $sog] = explode('|', $k, 2);
        $u = unita_find($c, $uid);
        $x = $per[$k];
        $righe[] = report_riga([$u ? unita_label($u) : '(eliminata)', $sit[$k]['nome'] ?? $x['nome'], SOGGETTI[$sog], $x['ord'], $x['str'], $x['ord'] + $x['str'], $x['vers']]);
        foreach ($t as $kk => $_) {
            $t[$kk] += $x[$kk];
        }
    }
    $sezioni[] = [
        'titolo' => 'Riparto consuntivo per condòmino',
        'nota' => 'Quote delle spese del periodo e versamenti effettuati nel periodo.',
        'colonne' => [['Unità', 'text'], ['Nome', 'text'], ['Ruolo', 'text'], ['Ordinarie', 'money'], ['Straordinarie', 'money'], ['Totale quote', 'money'], ['Versato', 'money']],
        'righe' => $righe,
        'totale' => ['Totale', '', '', $t['ord'], $t['str'], $t['ord'] + $t['str'], $t['vers']],
    ];
    return $sezioni;
}

// ----------------------------------------------------------------------
// Affitti e altre entrate

function report_entrate(array $c, string $anno): array
{
    $oggi = today();
    $fino = $anno !== '' ? $anno . '-12-31' : max($oggi, '1970-01-01');
    $riep = [];
    $dett = [];
    $t = ['prev' => 0, 'inc' => 0, 'scad' => 0];
    foreach ($c['entrate'] as $e) {
        $x = ['n' => 0, 'prev' => 0, 'inc' => 0, 'scad' => 0];
        foreach (entrata_scadenze($e, $fino) as $sc) {
            if (!nel_periodo($sc['data'], $anno)) {
                continue;
            }
            $stato = $sc['pagamento'] ? 'Incassata' : ($sc['data'] < $oggi ? 'Scaduta' : 'Da incassare');
            $x['n']++;
            $x['prev'] += $sc['importo'];
            $x['inc'] += $sc['pagamento']['importo'] ?? 0;
            $x['scad'] += $stato === 'Scaduta' ? $sc['importo'] : 0;
            $dett[] = report_riga([$sc['data'], $e['descrizione'], $e['debitore'], $sc['importo'], $stato,
                $sc['pagamento']['data'] ?? '', $sc['pagamento'] ? ($sc['pagamento']['importo']) : null], $stato === 'Scaduta' ? 'row-error' : '');
        }
        $riep[] = report_riga([$e['descrizione'], $e['debitore'], FREQUENZE[$e['frequenza']][0] ?? '', $e['in_cassa'] ? 'sì' : 'no',
            $x['n'], $x['prev'], $x['inc'], $x['scad']], $x['scad'] > 0 ? 'row-error' : '');
        foreach ($t as $k => $_) {
            $t[$k] += $x[$k];
        }
    }
    usort($dett, function ($a, $b) {
        return strcmp($a['v'][0], $b['v'][0]);
    });
    return [
        [
            'titolo' => 'Riepilogo entrate',
            'nota' => periodo_label($anno) . ($anno === '' ? ', scadenze fino a oggi' : '') . '.',
            'colonne' => [['Entrata', 'text'], ['Debitore', 'text'], ['Cadenza', 'text'], ['In cassa', 'text'], ['Scadenze', 'num'],
                ['Previsto', 'money'], ['Incassato', 'money'], ['Scaduto non incassato', 'money']],
            'righe' => $riep,
            'totale' => ['Totale', '', '', '', '', $t['prev'], $t['inc'], $t['scad']],
        ],
        [
            'titolo' => 'Dettaglio scadenze',
            'nota' => '',
            'colonne' => [['Scadenza', 'date'], ['Entrata', 'text'], ['Debitore', 'text'], ['Importo', 'money'], ['Stato', 'text'], ['Incassata il', 'date'], ['Importo incassato', 'money']],
            'righe' => $dett,
            'totale' => null,
        ],
    ];
}

// ----------------------------------------------------------------------
// Elenchi

function report_uscite(array $c, string $anno): array
{
    $catNome = array_column($c['categorie'], 'nome', 'id');
    $tabNome = array_column($c['tabelle'], 'nome', 'id');
    $righe = [];
    $t = ['tot' => 0, 'pag' => 0];
    foreach (uscite_filtra($c, ['anno' => $anno]) as $s) {
        $righe[] = report_riga([$s['data'], $catNome[$s['categoria_id']] ?? '', TIPI_SPESA[$s['tipo']], $s['fornitore'], $s['descrizione'],
            $tabNome[$s['tabella_id']] ?? '', $s['quota_inquilino'] . '%', $s['importo'], $s['pagata'] ? 'Pagata' : 'Da pagare', $s['data_pagamento'], scadenza_quote($s)]);
        $t['tot'] += $s['importo'];
        $t['pag'] += $s['pagata'] ? $s['importo'] : 0;
    }
    return [[
        'titolo' => 'Elenco spese',
        'nota' => periodo_label($anno) . '. Pagato ' . money($t['pag']) . ', da pagare ' . money($t['tot'] - $t['pag']) . '.',
        'colonne' => [['Data', 'date'], ['Tipologia', 'text'], ['Tipo', 'text'], ['Fornitore', 'text'], ['Descrizione', 'text'], ['Tabella', 'text'],
            ['Quota inquilino', 'text'], ['Importo', 'money'], ['Stato', 'text'], ['Pagata il', 'date'], ['Scadenza quote', 'date']],
        'righe' => $righe,
        'totale' => ['Totale', '', '', '', '', '', '', $t['tot'], '', '', ''],
    ]];
}

function report_versamenti(array $c, string $anno): array
{
    $sit = situazione($c);
    $lista = array_filter($c['versamenti'], function ($v) use ($anno) {
        return nel_periodo($v['data'], $anno);
    });
    usort($lista, function ($a, $b) {
        return strcmp($a['data'], $b['data']);
    });
    $righe = [];
    $tot = 0;
    foreach ($lista as $v) {
        $u = unita_find($c, $v['unita_id']);
        $righe[] = report_riga([$v['data'], $u ? unita_label($u) : '', $sit[$v['unita_id'] . '|' . $v['soggetto']]['nome'] ?? '', SOGGETTI[$v['soggetto']] ?? '',
            METODI_PAGAMENTO[$v['metodo']] ?? '', $v['rif'] !== '' ? trimestre_label($v['rif']) : '', $v['note'], $v['importo']]);
        $tot += $v['importo'];
    }
    return [[
        'titolo' => 'Elenco versamenti dei condòmini',
        'nota' => periodo_label($anno) . '.',
        'colonne' => [['Data', 'date'], ['Unità', 'text'], ['Nome', 'text'], ['Ruolo', 'text'], ['Metodo', 'text'], ['Rata', 'text'], ['Note', 'text'], ['Importo', 'money']],
        'righe' => $righe,
        'totale' => ['Totale', '', '', '', '', '', '', $tot],
    ]];
}

function report_situazione(array $c): array
{
    $righe = [];
    $t = ['addebitato' => 0, 'versato' => 0, 'saldo' => 0, 'scaduto' => 0];
    foreach (situazione($c) as $p) {
        $righe[] = report_riga([$p['unita'] ? unita_label($p['unita']) : '(eliminata)', $p['nome'], SOGGETTI[$p['soggetto']],
            $p['ordinarie'], $p['straordinarie'], $p['addebitato'], $p['versato'], $p['saldo'], $p['scaduto'], $p['prima_scadenza']],
            $p['scaduto'] > 0 ? 'row-error' : '');
        foreach ($t as $k => $_) {
            $t[$k] += $p[$k];
        }
    }
    return [[
        'titolo' => 'Situazione dei condòmini al ' . date_it(today()),
        'nota' => 'Saldo di cassa: ' . money(cassa_saldo($c)) . '.',
        'colonne' => [['Unità', 'text'], ['Nome', 'text'], ['Ruolo', 'text'], ['Ordinarie', 'money'], ['Straordinarie', 'money'], ['Addebitato', 'money'],
            ['Versato', 'money'], ['Saldo', 'money'], ['Scaduto', 'money'], ['Scaduto dal', 'date']],
        'righe' => $righe,
        'totale' => ['Totale', '', '', null, null, $t['addebitato'], $t['versato'], $t['saldo'], $t['scaduto'], ''],
    ]];
}

function report_genera(array $c, string $tipo, string $anno, string $chi): array
{
    switch ($tipo) {
        case 'estratto': return report_estratto($c, $chi, $anno);
        case 'rendiconto': return report_rendiconto($c, $anno);
        case 'entrate': return report_entrate($c, $anno);
        case 'uscite': return report_uscite($c, $anno);
        case 'versamenti': return report_versamenti($c, $anno);
        case 'situazione': return report_situazione($c);
    }
    return [];
}

// ----------------------------------------------------------------------
// Visualizzazione

/** Valore di una cella in HTML (già con escape). */
function report_cell($v, string $tipo): string
{
    if ($v === null || $v === '') {
        return '';
    }
    switch ($tipo) {
        case 'money':
            return '<span class="' . ($v < 0 ? 'neg' : '') . '">' . e(money((int) $v)) . '</span>';
        case 'date':
            return e(date_it((string) $v));
        default:
            return e($v);
    }
}

function report_html(array $sezioni): string
{
    ob_start();
    foreach ($sezioni as $i => $s): ?>
        <section class="report-section<?= $i > 0 && str_starts_with($s['titolo'], 'Estratto') ? ' page-break' : '' ?>">
            <h2><?= e($s['titolo']) ?></h2>
            <?php if ($s['nota'] !== ''): ?><p class="muted"><?= e($s['nota']) ?></p><?php endif; ?>
            <div class="card table-wrap">
                <table class="table-compact report-table">
                    <thead><tr>
                        <?php foreach ($s['colonne'] as [$label, $tipo]): ?>
                            <th class="<?= in_array($tipo, ['money', 'num'], true) ? 'num' : '' ?>"><?= e($label) ?></th>
                        <?php endforeach; ?>
                    </tr></thead>
                    <tbody>
                    <?php if (!$s['righe']): ?>
                        <tr><td colspan="<?= count($s['colonne']) ?>" class="muted">Nessun dato nel periodo.</td></tr>
                    <?php endif; ?>
                    <?php foreach ($s['righe'] as $r): ?>
                        <tr class="<?= e($r['class']) ?>">
                            <?php foreach ($s['colonne'] as $j => [$label, $tipo]): ?>
                                <td class="<?= in_array($tipo, ['money', 'num'], true) ? 'num' : ($tipo === 'date' ? 'nowrap' : '') ?>"><?= report_cell($r['v'][$j] ?? null, $tipo) ?></td>
                            <?php endforeach; ?>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                    <?php if ($s['totale'] !== null): ?>
                        <tfoot><tr>
                            <?php foreach ($s['colonne'] as $j => [$label, $tipo]): ?>
                                <th class="<?= in_array($tipo, ['money', 'num'], true) ? 'num' : '' ?>"><?= report_cell($s['totale'][$j] ?? null, $tipo) ?></th>
                            <?php endforeach; ?>
                        </tr></tfoot>
                    <?php endif; ?>
                </table>
            </div>
        </section>
    <?php endforeach;
    return (string) ob_get_clean();
}

/**
 * CSV per Excel in italiano: separatore ";", virgola decimale, date gg/mm/aaaa, UTF-8 con BOM.
 * I testi che iniziano con = + - @ vengono preceduti da un apice (protezione da formule).
 */
function report_csv(array $c, string $titolo, array $sezioni): string
{
    $h = fopen('php://temp', 'r+');
    $put = function (array $row) use ($h) {
        fputcsv($h, $row, ';', '"', '');
    };
    $text = function ($v): string {
        $v = (string) $v;
        return $v !== '' && strpbrk($v[0], "=+-@\t\r") !== false ? "'" . $v : $v;
    };
    $fmt = function ($v, string $tipo) use ($text): string {
        if ($v === null || $v === '') {
            return '';
        }
        switch ($tipo) {
            case 'money': return money((int) $v, false);
            case 'date': return date_it((string) $v);
            case 'num': return (string) $v;
        }
        return $text($v);
    };
    $put([$text($c['nome']), $text($titolo), 'generato il ' . date('d/m/Y H:i')]);
    foreach ($sezioni as $s) {
        $put([]);
        $put([$text($s['titolo'])]);
        $put(array_map($text, array_column($s['colonne'], 0)));
        foreach ($s['righe'] as $r) {
            $row = [];
            foreach ($s['colonne'] as $j => [$_, $tipo]) {
                $row[] = $fmt($r['v'][$j] ?? null, $tipo);
            }
            $put($row);
        }
        if ($s['totale'] !== null) {
            $row = [];
            foreach ($s['colonne'] as $j => [$_, $tipo]) {
                $row[] = $fmt($s['totale'][$j] ?? null, $tipo);
            }
            $put($row);
        }
    }
    rewind($h);
    $csv = (string) stream_get_contents($h);
    fclose($h);
    return "\xEF\xBB\xBF" . str_replace("\n", "\r\n", $csv);
}
