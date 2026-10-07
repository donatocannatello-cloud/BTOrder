<?php
declare(strict_types=1);
defined('APP') || exit;

$c = condominio_or_redirect(query('id'));

if (is_post()) {
    $sid = post('s');
    $s = uscita_find($c, $sid);
    if ($s === null) {
        flash('error', 'Spesa non trovata.');
    } elseif (post('action') === 'pagata') {
        condominio_update($c['id'], function (array $cur) use ($sid) {
            foreach ($cur['uscite'] as &$x) {
                if ($x['id'] === $sid) {
                    $x['pagata'] = true;
                    $x['data_pagamento'] = today();
                }
            }
            unset($x);
            return $cur;
        });
        flash('success', 'Spesa segnata come pagata in data ' . date_it(today()) . '.');
    }
    redirect('uscite', ['id' => $c['id']] + array_filter([
        'anno' => query('anno'), 'stato' => query('stato'), 'categoria' => query('categoria'), 'tipo' => query('tipo'),
    ]));
}

$anni = anni_movimenti($c);
$f = [
    'anno' => isset($_GET['anno']) ? query('anno') : date('Y'),
    'stato' => query('stato'),
    'categoria' => query('categoria'),
    'tipo' => query('tipo'),
];
$uscite = uscite_filtra($c, $f);
$tot = ['tutte' => 0, 'pagate' => 0, 'ordinaria' => 0, 'straordinaria' => 0];
foreach ($uscite as $s) {
    $tot['tutte'] += $s['importo'];
    $tot[$s['tipo']] += $s['importo'];
    if ($s['pagata']) {
        $tot['pagate'] += $s['importo'];
    }
}
$tabNome = array_column($c['tabelle'], 'nome', 'id');
$catNome = array_column($c['categorie'], 'nome', 'id');
$canAdd = $c['unita'] && $c['categorie'];
$title = 'Spese · ' . $c['nome'];
?>
<?= condominio_header($c, 'uscite') ?>

<div class="toolbar">
    <form method="get" action="index.php" class="filters">
        <input type="hidden" name="p" value="uscite">
        <input type="hidden" name="id" value="<?= e($c['id']) ?>">
        <label>Anno
            <select name="anno">
                <option value="">Tutti</option>
                <?php foreach ($anni as $a): ?>
                    <option value="<?= e($a) ?>"<?= selected($f['anno'] === $a) ?>><?= e($a) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <label>Stato
            <select name="stato">
                <option value="">Tutte</option>
                <option value="da_pagare"<?= selected($f['stato'] === 'da_pagare') ?>>Da pagare</option>
                <option value="pagate"<?= selected($f['stato'] === 'pagate') ?>>Pagate</option>
            </select>
        </label>
        <label>Tipo
            <select name="tipo">
                <option value="">Tutti</option>
                <?php foreach (TIPI_SPESA as $k => $l): ?>
                    <option value="<?= e($k) ?>"<?= selected($f['tipo'] === $k) ?>><?= e($l) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <label>Tipologia
            <select name="categoria">
                <option value="">Tutte</option>
                <?php foreach (categorie_sorted($c) as $k): ?>
                    <option value="<?= e($k['id']) ?>"<?= selected($f['categoria'] === $k['id']) ?>><?= e($k['nome']) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <button type="submit" class="btn btn-small">Filtra</button>
    </form>
    <?php if ($canAdd): ?>
        <a class="btn btn-primary" href="<?= e(url('uscita_form', ['id' => $c['id']])) ?>">+ Nuova spesa</a>
    <?php endif; ?>
</div>

<?php if (!$canAdd): ?>
    <div class="alert alert-info">Per registrare le spese servono almeno un'unità con i millesimi e una tipologia di spesa.</div>
<?php endif; ?>

<?php if (!$uscite): ?>
    <div class="card empty"><p>Nessuna spesa<?= $f['anno'] !== '' ? ' nel ' . e($f['anno']) : '' ?> con questi filtri.</p></div>
<?php else: ?>
    <div class="card table-wrap">
        <table class="table-compact">
            <thead>
            <tr>
                <th>Data</th>
                <th>Tipologia</th>
                <th>Fornitore / descrizione</th>
                <th>Tabella</th>
                <th class="num">Inquilino</th>
                <th class="num">Importo</th>
                <th>Stato</th>
                <th>Allegati</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($uscite as $s): ?>
                <tr>
                    <td class="nowrap"><?= e(date_it($s['data'])) ?></td>
                    <td><?= e($catNome[$s['categoria_id']] ?? '—') ?><br>
                        <span class="badge <?= $s['tipo'] === 'straordinaria' ? 'badge-warn' : 'badge-info' ?>"><?= e(TIPI_SPESA[$s['tipo']]) ?></span></td>
                    <td><strong><?= e($s['fornitore']) ?></strong><?= $s['descrizione'] !== '' ? '<br><span class="muted">' . e($s['descrizione']) . '</span>' : '' ?></td>
                    <td><?= e($tabNome[$s['tabella_id']] ?? '—') ?></td>
                    <td class="num"><?= e($s['quota_inquilino']) ?>%</td>
                    <td class="num"><strong><?= e(money($s['importo'])) ?></strong></td>
                    <td class="nowrap">
                        <?php if ($s['pagata']): ?>
                            <span class="badge badge-ok">Pagata</span><br><span class="muted"><?= e(date_it($s['data_pagamento'])) ?></span>
                        <?php else: ?>
                            <span class="badge badge-ko">Da pagare</span>
                        <?php endif; ?>
                    </td>
                    <td class="small">
                        <?php foreach ($s['allegati'] as $a): ?>
                            <a href="<?= e(url('allegato', ['id' => $c['id'], 's' => $s['id'], 'a' => $a['id']])) ?>" target="_blank" rel="noopener" title="<?= e($a['nome']) ?>">📎 <?= e(mb_strimwidth($a['nome'], 0, 22, '…')) ?></a><br>
                        <?php endforeach; ?>
                    </td>
                    <td class="actions-cell">
                        <?php if (!$s['pagata']): ?>
                            <form method="post" action="<?= e(url('uscite', ['id' => $c['id']] + array_filter($f))) ?>">
                                <?= csrf_field() ?>
                                <input type="hidden" name="action" value="pagata">
                                <input type="hidden" name="s" value="<?= e($s['id']) ?>">
                                <button type="submit" class="btn btn-small">Segna pagata</button>
                            </form>
                        <?php endif; ?>
                        <a class="btn btn-small" href="<?= e(url('uscita_form', ['id' => $c['id'], 's' => $s['id']])) ?>">Apri / riparto</a>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
            <tfoot>
            <tr>
                <th colspan="5">Totale (<?= e(plural(count($uscite), 'spesa', 'spese')) ?>) · ordinarie <?= e(money($tot['ordinaria'])) ?> · straordinarie <?= e(money($tot['straordinaria'])) ?></th>
                <th class="num"><?= e(money($tot['tutte'])) ?></th>
                <th colspan="3" class="muted">di cui da pagare: <?= e(money($tot['tutte'] - $tot['pagate'])) ?></th>
            </tr>
            </tfoot>
        </table>
    </div>
<?php endif; ?>
