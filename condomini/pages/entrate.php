<?php
declare(strict_types=1);
defined('APP') || exit;

// Affitti e altre entrate ricorrenti: elenco e scadenziario con stato incassato/non incassato.
$c = condominio_or_redirect(query('id'));
$oggi = today();
$anno = preg_match('/^\d{4}$/', query('anno')) ? query('anno') : date('Y');
$stato = query('stato');
$back = function () use ($c, $anno, $stato) {
    redirect('entrate', array_filter(['id' => $c['id'], 'anno' => $anno, 'stato' => $stato]));
};

if (is_post()) {
    $eid = post('e');
    $d = post('d');
    $e = entrata_find($c, $eid);
    $inCalendario = false;
    if ($e && preg_match('/^\d{4}-\d{2}-\d{2}$/', $d)) {
        foreach (entrata_scadenze($e, max($d, $oggi)) as $sc) {
            $inCalendario = $inCalendario || $sc['data'] === $d;
        }
    }
    if (!$inCalendario) {
        flash('error', 'Scadenza non trovata.');
        $back();
    }

    if (post('action') === 'incassa') {
        $data = post('data') === '' ? $oggi : parse_date(post('data'));
        $importo = post('importo') === '' ? $e['importo'] : parse_money(post('importo'));
        $metodo = post('metodo');
        if ($data === null) {
            flash('error', 'Data di incasso non valida (gg/mm/aaaa).');
        } elseif ($importo === null || $importo <= 0) {
            flash('error', 'Importo non valido.');
        } elseif (!isset(METODI_PAGAMENTO[$metodo])) {
            flash('error', 'Scegli il metodo di pagamento.');
        } else {
            condominio_update($c['id'], function (array $cur) use ($eid, $d, $data, $importo, $metodo) {
                foreach ($cur['entrate'] as &$x) {
                    if ($x['id'] === $eid) {
                        $x['pagamenti'][$d] = ['data' => $data, 'importo' => $importo, 'metodo' => $metodo, 'note' => ''];
                    }
                }
                unset($x);
                return $cur;
            });
            flash('success', $e['descrizione'] . ' del ' . date_it($d) . ': incassati ' . money($importo) . '.');
        }
    } elseif (post('action') === 'annulla') {
        condominio_update($c['id'], function (array $cur) use ($eid, $d) {
            foreach ($cur['entrate'] as &$x) {
                if ($x['id'] === $eid) {
                    unset($x['pagamenti'][$d]);
                }
            }
            unset($x);
            return $cur;
        });
        flash('success', $e['descrizione'] . ' del ' . date_it($d) . ' segnata come non incassata.');
    }
    $back();
}

// Scadenziario dell'anno scelto
$righe = [];
foreach ($c['entrate'] as $e) {
    foreach (entrata_scadenze($e, $anno . '-12-31') as $sc) {
        if (substr($sc['data'], 0, 4) !== $anno) {
            continue;
        }
        $st = $sc['pagamento'] ? 'incassata' : ($sc['data'] < $oggi ? 'scaduta' : 'da_incassare');
        if ($stato === '' || $stato === $st || ($stato === 'non_incassate' && $st !== 'incassata')) {
            $righe[] = ['e' => $e, 'stato' => $st] + $sc;
        }
    }
}
usort($righe, function ($a, $b) {
    return strcmp($a['data'], $b['data']) ?: strcmp($a['e']['descrizione'], $b['e']['descrizione']);
});
$tot = ['previsto' => 0, 'incassato' => 0, 'scaduto' => 0];
foreach ($righe as $r) {
    $tot['previsto'] += $r['importo'];
    $tot['incassato'] += $r['pagamento']['importo'] ?? 0;
    $tot['scaduto'] += $r['stato'] === 'scaduta' ? $r['importo'] : 0;
}
$scadute = entrate_scadute($c, $oggi);
$anni = anni_movimenti($c);
foreach ($c['entrate'] as $e) {
    foreach ([substr($e['data_inizio'], 0, 4), $e['data_fine'] !== '' ? substr($e['data_fine'], 0, 4) : ''] as $a) {
        if ($a !== '' && !in_array($a, $anni, true)) {
            $anni[] = $a;
        }
    }
}
rsort($anni);
$badge = ['incassata' => ['badge-ok', 'Incassata'], 'scaduta' => ['badge-ko', 'Scaduta'], 'da_incassare' => ['badge-info', 'Da incassare']];
$title = 'Affitti e altre entrate · ' . $c['nome'];
?>
<?= condominio_header($c, 'entrate') ?>

<div class="toolbar">
    <h2>Entrate ricorrenti</h2>
    <a class="btn btn-primary" href="<?= e(url('entrata_form', ['id' => $c['id']])) ?>">+ Nuova entrata</a>
</div>

<?php if (!$c['entrate']): ?>
    <div class="card empty">
        <p>Nessuna entrata registrata. Qui puoi gestire affitti (es. locale portineria, antenna, posti auto)
            e altri incassi a cadenza regolare o una tantum.</p>
        <p><a class="btn btn-primary" href="<?= e(url('entrata_form', ['id' => $c['id']])) ?>">+ Nuova entrata</a></p>
    </div>
<?php else: ?>
    <div class="card table-wrap">
        <table class="table-compact">
            <thead>
            <tr>
                <th>Descrizione</th><th>Debitore</th><th>Cadenza</th><th class="num">Importo</th><th>Periodo</th>
                <th>Prossima scadenza</th><th class="num">Scaduto non incassato</th><th></th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($c['entrate'] as $e):
                $prox = entrata_prossima($e, $oggi);
                $sc = array_filter($scadute, function ($x) use ($e) { return $x['e']['id'] === $e['id']; });
                $u = $e['unita_id'] !== '' ? unita_find($c, $e['unita_id']) : null; ?>
                <tr class="<?= $sc ? 'row-error' : '' ?>">
                    <td><strong><?= e($e['descrizione']) ?></strong>
                        <?= $e['in_cassa'] ? '' : '<br><span class="badge badge-warn">fuori cassa condominiale</span>' ?></td>
                    <td><?= e($e['debitore']) ?><?= $u ? '<br><span class="muted">' . e(unita_label($u)) . '</span>' : '' ?></td>
                    <td><?= e(FREQUENZE[$e['frequenza']][0] ?? $e['frequenza']) ?></td>
                    <td class="num"><?= e(money($e['importo'])) ?></td>
                    <td class="nowrap">dal <?= e(date_it($e['data_inizio'])) ?><?= $e['data_fine'] !== '' ? '<br>al ' . e(date_it($e['data_fine'])) : '' ?></td>
                    <td class="nowrap"><?= $prox ? e(date_it($prox['data'])) : '<span class="muted">—</span>' ?></td>
                    <td class="num"><?= $sc ? '<strong class="neg">' . e(money(array_sum(array_column($sc, 'importo')))) . '</strong><br><span class="muted">' . e(plural(count($sc), 'scadenza', 'scadenze')) . '</span>' : '<span class="muted">—</span>' ?></td>
                    <td class="actions-cell"><a class="btn btn-small" href="<?= e(url('entrata_form', ['id' => $c['id'], 'e' => $e['id']])) ?>">Modifica</a></td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>

    <div class="toolbar">
        <h2>Scadenziario <?= e($anno) ?></h2>
        <form method="get" action="index.php" class="filters">
            <input type="hidden" name="p" value="entrate">
            <input type="hidden" name="id" value="<?= e($c['id']) ?>">
            <label>Anno
                <select name="anno">
                    <?php foreach ($anni as $a): ?>
                        <option value="<?= e($a) ?>"<?= selected($anno === $a) ?>><?= e($a) ?></option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>Stato
                <select name="stato">
                    <option value="">Tutte</option>
                    <option value="non_incassate"<?= selected($stato === 'non_incassate') ?>>Non incassate</option>
                    <option value="scaduta"<?= selected($stato === 'scaduta') ?>>Scadute</option>
                    <option value="incassata"<?= selected($stato === 'incassata') ?>>Incassate</option>
                </select>
            </label>
            <button type="submit" class="btn btn-small">Filtra</button>
            <a class="btn btn-small" href="<?= e(url('report', ['id' => $c['id'], 'tipo' => 'entrate', 'anno' => $anno, 'formato' => 'csv'])) ?>">⬇ CSV</a>
            <a class="btn btn-small" href="<?= e(url('report', ['id' => $c['id'], 'tipo' => 'entrate', 'anno' => $anno, 'formato' => 'stampa'])) ?>" target="_blank" rel="noopener">🖨 Stampa</a>
        </form>
    </div>

    <?php if (!$righe): ?>
        <div class="card empty"><p>Nessuna scadenza nel <?= e($anno) ?> con questo filtro.</p></div>
    <?php else: ?>
        <div class="card table-wrap">
            <table class="table-compact">
                <thead>
                <tr><th>Scadenza</th><th>Entrata</th><th>Debitore</th><th class="num">Importo</th><th>Stato</th><th>Incasso</th></tr>
                </thead>
                <tbody>
                <?php foreach ($righe as $r): $e = $r['e']; ?>
                    <tr class="<?= $r['stato'] === 'scaduta' ? 'row-error' : ($r['stato'] === 'incassata' ? 'row-paid' : '') ?>">
                        <td class="nowrap"><strong><?= e(date_it($r['data'])) ?></strong></td>
                        <td><?= e($e['descrizione']) ?></td>
                        <td><?= e($e['debitore']) ?></td>
                        <td class="num"><?= e(money($r['importo'])) ?></td>
                        <td><span class="badge <?= e($badge[$r['stato']][0]) ?>"><?= e($badge[$r['stato']][1]) ?></span></td>
                        <td>
                            <?php if ($r['pagamento']): ?>
                                <form method="post" class="inline-form" action="<?= e(url('entrate', array_filter(['id' => $c['id'], 'anno' => $anno, 'stato' => $stato]))) ?>"
                                      data-confirm="Segnare come NON incassata la scadenza del <?= e(date_it($r['data'])) ?>?">
                                    <?= csrf_field() ?>
                                    <input type="hidden" name="action" value="annulla">
                                    <input type="hidden" name="e" value="<?= e($e['id']) ?>">
                                    <input type="hidden" name="d" value="<?= e($r['data']) ?>">
                                    <span class="small">il <?= e(date_it($r['pagamento']['data'])) ?> · <?= e(METODI_PAGAMENTO[$r['pagamento']['metodo']] ?? '') ?></span>
                                    <button type="submit" class="btn btn-small">Segna non incassata</button>
                                </form>
                            <?php else: ?>
                                <form method="post" class="inline-form" action="<?= e(url('entrate', array_filter(['id' => $c['id'], 'anno' => $anno, 'stato' => $stato]))) ?>">
                                    <?= csrf_field() ?>
                                    <input type="hidden" name="action" value="incassa">
                                    <input type="hidden" name="e" value="<?= e($e['id']) ?>">
                                    <input type="hidden" name="d" value="<?= e($r['data']) ?>">
                                    <input type="text" name="data" class="input-date input-small" placeholder="oggi" maxlength="10" inputmode="numeric" aria-label="Data incasso (gg/mm/aaaa)">
                                    <input type="text" name="importo" class="input-small" placeholder="<?= e(money_input($r['importo'])) ?>" inputmode="decimal" aria-label="Importo incassato">
                                    <select name="metodo" aria-label="Metodo">
                                        <?php foreach (METODI_PAGAMENTO as $k => $l): ?>
                                            <option value="<?= e($k) ?>"><?= e($l) ?></option>
                                        <?php endforeach; ?>
                                    </select>
                                    <button type="submit" class="btn btn-small btn-primary">Segna incassata</button>
                                </form>
                            <?php endif; ?>
                        </td>
                    </tr>
                <?php endforeach; ?>
                </tbody>
                <tfoot>
                <tr>
                    <th colspan="3">Previsto <?= e(money($tot['previsto'])) ?> · incassato <?= e(money($tot['incassato'])) ?></th>
                    <th class="num"></th>
                    <th colspan="2" class="<?= $tot['scaduto'] ? 'neg' : '' ?>">Scaduto non incassato: <?= e(money($tot['scaduto'])) ?></th>
                </tr>
                </tfoot>
            </table>
        </div>
        <p class="muted">Data e importo dell'incasso sono facoltativi: se vuoti si usano la data di oggi e l'importo previsto.</p>
    <?php endif; ?>
<?php endif; ?>
