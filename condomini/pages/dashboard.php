<?php
declare(strict_types=1);
defined('APP') || exit;

// Riepilogo di tutti i condomini: cassa, spese da pagare, morosità.
$condomini = condomini_all();
$rows = [];
$tot = ['cassa' => 0, 'da_pagare' => 0, 'scaduto' => 0];
$spese = [];
$morosi = [];
foreach ($condomini as $c) {
    $r = condominio_riepilogo($c);
    $rows[] = ['c' => $c, 'r' => $r];
    $tot['cassa'] += $r['cassa'];
    $tot['da_pagare'] += $r['da_pagare_tot'];
    $tot['scaduto'] += $r['scaduto_tot'];
    foreach ($r['da_pagare'] as $s) {
        $spese[] = ['c' => $c, 's' => $s];
    }
    foreach ($r['morosi'] as $p) {
        $morosi[] = ['c' => $c, 'p' => $p];
    }
}
usort($spese, function ($a, $b) {
    return strcmp($a['s']['data'], $b['s']['data']);
});
usort($morosi, function ($a, $b) {
    return $b['p']['scaduto'] <=> $a['p']['scaduto'];
});

$title = 'Dashboard';
?>
<div class="page-head">
    <h1>Dashboard</h1>
    <span class="muted">Oggi <?= e(date_it(today())) ?></span>
</div>

<?php if (!$condomini): ?>
    <div class="card empty">
        <p>Non ci sono ancora condomini in archivio.</p>
        <p><a class="btn btn-primary" href="<?= e(url('condominio_form')) ?>">Crea il primo condominio</a></p>
    </div>
    <?php return; ?>
<?php endif; ?>

<div class="grid grid-3">
    <div class="card stat">
        <span class="stat-label">Saldo di cassa (tutti i condomini)</span>
        <span class="stat-value <?= $tot['cassa'] < 0 ? 'neg' : '' ?>"><?= e(money($tot['cassa'])) ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Spese da pagare</span>
        <span class="stat-value"><?= e(money($tot['da_pagare'])) ?></span>
        <span class="muted"><?= e(plural(count($spese), 'fattura', 'fatture')) ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Quote scadute non versate</span>
        <span class="stat-value <?= $tot['scaduto'] > 0 ? 'neg' : 'pos' ?>"><?= e(money($tot['scaduto'])) ?></span>
        <span class="muted"><?= e(plural(count($morosi), 'condòmino moroso', 'condòmini morosi')) ?></span>
    </div>
</div>

<div class="toolbar"><h2>Condomini</h2></div>
<div class="card table-wrap">
    <table class="dash-table">
        <thead>
        <tr>
            <th>Condominio</th>
            <th class="num">Unità</th>
            <th class="num">Saldo di cassa</th>
            <th class="num">Spese da pagare</th>
            <th class="num">Quote scadute</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <?php foreach ($rows as ['c' => $c, 'r' => $r]): ?>
            <tr>
                <td><a href="<?= e(url('uscite', ['id' => $c['id']])) ?>"><strong><?= e($c['nome']) ?></strong></a>
                    <br><span class="muted"><?= e($c['indirizzo']) ?> · <?= e(RUOLI[$c['ruolo']] ?? '') ?></span></td>
                <td class="num"><?= e(count($c['unita'])) ?></td>
                <td class="num"><strong class="<?= $r['cassa'] < 0 ? 'neg' : '' ?>"><?= e(money($r['cassa'])) ?></strong></td>
                <td class="num"><?= $r['da_pagare_tot'] ? e(money($r['da_pagare_tot'])) . '<br><span class="muted">' . e(plural(count($r['da_pagare']), 'fattura', 'fatture')) . '</span>' : '<span class="muted">—</span>' ?></td>
                <td class="num"><?= $r['scaduto_tot'] ? '<strong class="neg">' . e(money($r['scaduto_tot'])) . '</strong><br><span class="muted">' . e(plural(count($r['morosi']), 'moroso', 'morosi')) . '</span>' : '<span class="muted">—</span>' ?></td>
                <td class="actions-cell">
                    <a class="btn btn-small" href="<?= e(url('uscita_form', ['id' => $c['id']])) ?>">+ Spesa</a>
                    <a class="btn btn-small" href="<?= e(url('versamenti', ['id' => $c['id']])) ?>">+ Incasso</a>
                    <a class="btn btn-small" href="<?= e(url('situazione', ['id' => $c['id']])) ?>">Situazione</a>
                </td>
            </tr>
        <?php endforeach; ?>
        </tbody>
    </table>
</div>

<div class="grid grid-2">
    <section class="card">
        <h2>Quote scadute</h2>
        <?php if (!$morosi): ?>
            <p class="muted">Nessuna morosità.</p>
        <?php else: ?>
            <div class="table-wrap">
                <table class="table-compact">
                    <thead><tr><th>Condominio</th><th>Chi</th><th>Scaduto dal</th><th class="num">Importo</th></tr></thead>
                    <tbody>
                    <?php foreach (array_slice($morosi, 0, 15) as ['c' => $c, 'p' => $p]): ?>
                        <tr>
                            <td><a href="<?= e(url('situazione', ['id' => $c['id'], 'morosi' => '1'])) ?>"><?= e($c['nome']) ?></a></td>
                            <td><?= e($p['nome']) ?><br><span class="muted"><?= e($p['unita'] ? unita_label($p['unita']) : '') ?> · <?= e(strtolower(SOGGETTI[$p['soggetto']])) ?></span></td>
                            <td class="nowrap"><?= e(date_it($p['prima_scadenza'])) ?></td>
                            <td class="num"><strong class="neg"><?= e(money($p['scaduto'])) ?></strong></td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
            <?php if (count($morosi) > 15): ?><p class="muted">e altri <?= e(count($morosi) - 15) ?>…</p><?php endif; ?>
        <?php endif; ?>
    </section>

    <section class="card">
        <h2>Spese da pagare</h2>
        <?php if (!$spese): ?>
            <p class="muted">Nessuna spesa da pagare.</p>
        <?php else: ?>
            <div class="table-wrap">
                <table class="table-compact">
                    <thead><tr><th>Data</th><th>Condominio</th><th>Fornitore</th><th class="num">Importo</th></tr></thead>
                    <tbody>
                    <?php foreach (array_slice($spese, 0, 15) as ['c' => $c, 's' => $s]): ?>
                        <tr>
                            <td class="nowrap"><?= e(date_it($s['data'])) ?></td>
                            <td><?= e($c['nome']) ?></td>
                            <td><a href="<?= e(url('uscita_form', ['id' => $c['id'], 's' => $s['id']])) ?>"><?= e($s['fornitore'] !== '' ? $s['fornitore'] : (categoria_find($c, $s['categoria_id'])['nome'] ?? 'Spesa')) ?></a></td>
                            <td class="num"><?= e(money($s['importo'])) ?></td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
            <?php if (count($spese) > 15): ?><p class="muted">e altre <?= e(count($spese) - 15) ?>…</p><?php endif; ?>
        <?php endif; ?>
    </section>
</div>
