<?php
declare(strict_types=1);
defined('APP') || exit;

// Rate trimestrali dei condòmini: per ogni trimestre quanto è dovuto e se è stato pagato.
$c = condominio_or_redirect(query('id'));
$anni = anni_movimenti($c);
$anno = preg_match('/^\d{4}$/', query('anno')) ? query('anno') : date('Y');

if (is_post() && post('action') === 'paga') {
    // Segna pagata una rata: registra un versamento del residuo destinato a quel trimestre.
    $key = post('chi');
    $t = post('t');
    $rate = rate_trimestrali($c, substr($t, 0, 4));
    $rata = $rate[$key][substr($t, 5)] ?? null;
    $metodo = post('metodo');
    $data = parse_date(post('data')) ?? today();
    if ($rata === null || $rata['residuo'] <= 0 || !preg_match('/^\d{4}-T[1-4]$/', $t)) {
        flash('error', 'Rata non trovata o già pagata.');
    } elseif (!isset(METODI_PAGAMENTO[$metodo])) {
        flash('error', 'Scegli il metodo di pagamento.');
    } else {
        [$uid, $sog] = explode('|', $key, 2);
        condominio_update($c['id'], function (array $cur) use ($uid, $sog, $rata, $t, $metodo, $data) {
            $cur['versamenti'][] = [
                'id' => Store::newId(), 'unita_id' => $uid, 'soggetto' => $sog, 'data' => $data,
                'importo' => $rata['residuo'], 'metodo' => $metodo, 'note' => 'Rata ' . trimestre_label($t), 'rif' => $t,
            ];
            return $cur;
        });
        flash('success', 'Rata ' . trimestre_label($t) . ' di ' . $rate[$key]['p']['nome'] . ' segnata pagata (' . money($rata['residuo']) . ').');
    }
    redirect('rate', ['id' => $c['id'], 'anno' => $anno, 'metodo' => $metodo]);
}

$rate = rate_trimestrali($c, $anno);
$metodo = isset(METODI_PAGAMENTO[query('metodo')]) ? query('metodo') : 'bonifico';
$tot = [];
for ($q = 1; $q <= 4; $q++) {
    $tot['T' . $q] = ['dovuto' => 0, 'coperto' => 0];
    foreach ($rate as $r) {
        $tot['T' . $q]['dovuto'] += $r['T' . $q]['dovuto'];
        $tot['T' . $q]['coperto'] += min($r['T' . $q]['coperto'], $r['T' . $q]['dovuto']);
    }
}
$badge = [
    'pagata' => ['badge-ok', 'Pagata'],
    'parziale' => ['badge-warn', 'Parziale'],
    'da_pagare' => ['badge-info', 'Da pagare'],
    'scaduta' => ['badge-ko', 'Scaduta'],
];
$title = 'Rate trimestrali · ' . $c['nome'];
?>
<?= condominio_header($c, 'rate') ?>

<div class="toolbar">
    <form method="get" action="index.php" class="filters">
        <input type="hidden" name="p" value="rate">
        <input type="hidden" name="id" value="<?= e($c['id']) ?>">
        <label>Anno
            <select name="anno">
                <?php foreach ($anni as $a): ?>
                    <option value="<?= e($a) ?>"<?= selected($anno === $a) ?>><?= e($a) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <label>Metodo per "Segna pagata"
            <select name="metodo">
                <?php foreach (METODI_PAGAMENTO as $k => $l): ?>
                    <option value="<?= e($k) ?>"<?= selected($metodo === $k) ?>><?= e($l) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <button type="submit" class="btn btn-small">Aggiorna</button>
    </form>
</div>

<p class="muted">Ogni rata comprende le quote delle spese con scadenza nel trimestre (di norma: spese registrate nel trimestre,
    scadenza al 31/03, 30/06, 30/09, 31/12). "Segna pagata" registra un versamento del residuo, con data di oggi, nella scheda Incassi.</p>

<?php if (!$rate): ?>
    <div class="card empty"><p>Nessuna rata nel <?= e($anno) ?>: le rate nascono dalle spese registrate.</p></div>
<?php else: ?>
    <div class="card table-wrap">
        <table class="table-compact rate-table">
            <thead>
            <tr>
                <th>Unità</th>
                <th>Nome</th>
                <?php for ($q = 1; $q <= 4; $q++): ?>
                    <th class="num"><?= e(trimestre_label($anno . '-T' . $q)) ?><br><span class="muted">scad. <?= e(date_it(trimestre_intervallo($anno . '-T' . $q)[1])) ?></span></th>
                <?php endfor; ?>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($rate as $key => $r): $p = $r['p']; ?>
                <tr>
                    <td class="nowrap"><?= e($p['unita'] ? unita_label($p['unita']) : '(eliminata)') ?></td>
                    <td><strong><?= e($p['nome']) ?></strong><br><span class="muted"><?= e(strtolower(SOGGETTI[$p['soggetto']])) ?></span></td>
                    <?php for ($q = 1; $q <= 4; $q++): $rata = $r['T' . $q]; ?>
                        <td class="num rata rata-<?= e($rata['stato'] ?: 'vuota') ?>">
                            <?php if ($rata['stato'] === ''): ?>
                                <span class="muted">—</span>
                            <?php else: ?>
                                <strong><?= e(money($rata['dovuto'])) ?></strong><br>
                                <span class="badge <?= e($badge[$rata['stato']][0]) ?>"><?= e($badge[$rata['stato']][1]) ?></span>
                                <?php if ($rata['residuo'] > 0): ?>
                                    <?php if ($rata['coperto'] > 0): ?><br><span class="muted">resta <?= e(money($rata['residuo'])) ?></span><?php endif; ?>
                                    <form method="post" action="<?= e(url('rate', ['id' => $c['id'], 'anno' => $anno])) ?>"
                                          data-confirm="Segnare pagata la rata <?= e(trimestre_label($rata['trimestre'])) ?> di <?= e($p['nome']) ?> (<?= e(money($rata['residuo'])) ?>, <?= e(strtolower(METODI_PAGAMENTO[$metodo])) ?>)?">
                                        <?= csrf_field() ?>
                                        <input type="hidden" name="action" value="paga">
                                        <input type="hidden" name="chi" value="<?= e($key) ?>">
                                        <input type="hidden" name="t" value="<?= e($rata['trimestre']) ?>">
                                        <input type="hidden" name="metodo" value="<?= e($metodo) ?>">
                                        <button type="submit" class="btn btn-small">Segna pagata</button>
                                    </form>
                                <?php endif; ?>
                            <?php endif; ?>
                        </td>
                    <?php endfor; ?>
                </tr>
            <?php endforeach; ?>
            </tbody>
            <tfoot>
            <tr>
                <th colspan="2">Totale incassato / dovuto</th>
                <?php foreach ($tot as $t): ?>
                    <th class="num"><?= e(money($t['coperto'])) ?><br><span class="muted">su <?= e(money($t['dovuto'])) ?></span></th>
                <?php endforeach; ?>
            </tr>
            </tfoot>
        </table>
    </div>
    <p class="muted">Per annullare un pagamento elimina il versamento corrispondente nella scheda
        <a href="<?= e(url('versamenti', ['id' => $c['id']])) ?>">Incassi</a>.</p>
<?php endif; ?>
