<?php
declare(strict_types=1);
defined('APP') || exit;

// Posizione di ogni proprietario e inquilino: addebitato, versato, saldo, morosità.
$c = condominio_or_redirect(query('id'));
$sit = situazione($c);
$soloMorosi = query('morosi') === '1';
$righe = $soloMorosi ? array_filter($sit, function ($p) {
    return $p['scaduto'] > 0;
}) : $sit;
$tot = ['ordinarie' => 0, 'straordinarie' => 0, 'addebitato' => 0, 'versato' => 0, 'saldo' => 0, 'scaduto' => 0];
foreach ($righe as $p) {
    foreach ($tot as $k => $_) {
        $tot[$k] += $p[$k];
    }
}
$nMorosi = count(array_filter($sit, function ($p) {
    return $p['scaduto'] > 0;
}));
$title = 'Situazione · ' . $c['nome'];
?>
<?= condominio_header($c, 'situazione') ?>

<div class="grid grid-4">
    <div class="card stat">
        <span class="stat-label">Saldo di cassa</span>
        <span class="stat-value <?= cassa_saldo($c) < 0 ? 'neg' : '' ?>"><?= e(money(cassa_saldo($c))) ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Totale da incassare</span>
        <span class="stat-value"><?= e(money(array_sum(array_map(function ($p) { return max(0, $p['saldo']); }, $sit)))) ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Di cui scaduto (morosità)</span>
        <span class="stat-value <?= $nMorosi ? 'neg' : 'pos' ?>"><?= e(money(array_sum(array_column($sit, 'scaduto')))) ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Condòmini morosi</span>
        <span class="stat-value <?= $nMorosi ? 'neg' : 'pos' ?>"><?= e($nMorosi) ?></span>
    </div>
</div>

<div class="toolbar">
    <h2>Posizioni</h2>
    <div class="actions">
        <a class="btn btn-small" href="<?= e(url('report', ['id' => $c['id'], 'tipo' => 'situazione', 'formato' => 'csv'])) ?>">⬇ CSV</a>
        <a class="btn btn-small" href="<?= e(url('report', ['id' => $c['id'], 'tipo' => 'situazione', 'formato' => 'stampa'])) ?>" target="_blank" rel="noopener">🖨 Stampa</a>
        <?php if ($soloMorosi): ?>
            <a class="btn btn-small" href="<?= e(url('situazione', ['id' => $c['id']])) ?>">Mostra tutti</a>
        <?php else: ?>
            <a class="btn btn-small" href="<?= e(url('situazione', ['id' => $c['id'], 'morosi' => '1'])) ?>">Solo morosi</a>
        <?php endif; ?>
    </div>
</div>

<?php if (!$righe): ?>
    <div class="card empty"><p><?= $soloMorosi ? 'Nessuna morosità: tutte le quote scadute sono state versate.' : 'Nessun addebito: registra una spesa per vedere il riparto.' ?></p></div>
<?php else: ?>
    <div class="card table-wrap">
        <table class="table-compact">
            <thead>
            <tr>
                <th>Unità</th>
                <th>Nome</th>
                <th>Ruolo</th>
                <th class="num">Ordinarie</th>
                <th class="num">Straordinarie</th>
                <th class="num">Totale addebitato</th>
                <th class="num">Versato</th>
                <th class="num">Saldo da versare</th>
                <th class="num">Scaduto</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($righe as $key => $p): ?>
                <tr class="<?= $p['scaduto'] > 0 ? 'row-error' : '' ?>">
                    <td class="nowrap"><?= e($p['unita'] ? unita_label($p['unita']) : '(eliminata)') ?></td>
                    <td><strong><?= e($p['nome']) ?></strong></td>
                    <td><?= e(SOGGETTI[$p['soggetto']]) ?></td>
                    <td class="num"><?= e(money($p['ordinarie'])) ?></td>
                    <td class="num"><?= e(money($p['straordinarie'])) ?></td>
                    <td class="num"><?= e(money($p['addebitato'])) ?></td>
                    <td class="num"><?= e(money($p['versato'])) ?></td>
                    <td class="num"><strong class="<?= $p['saldo'] > 0 ? '' : 'pos' ?>"><?= e(money($p['saldo'])) ?></strong>
                        <?= $p['saldo'] < 0 ? '<br><span class="muted">a credito</span>' : '' ?></td>
                    <td class="num">
                        <?php if ($p['scaduto'] > 0): ?>
                            <strong class="neg"><?= e(money($p['scaduto'])) ?></strong><br>
                            <span class="muted">dal <?= e(date_it($p['prima_scadenza'])) ?></span>
                        <?php else: ?>
                            <span class="muted">—</span>
                        <?php endif; ?>
                    </td>
                    <td class="actions-cell">
                        <?php if ($p['saldo'] > 0): ?>
                            <a class="btn btn-small" href="<?= e(url('versamenti', ['id' => $c['id'], 'chi' => $key])) ?>#form-versamento">Registra versamento</a>
                        <?php endif; ?>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
            <tfoot>
            <tr>
                <th colspan="3">Totale</th>
                <th class="num"><?= e(money($tot['ordinarie'])) ?></th>
                <th class="num"><?= e(money($tot['straordinarie'])) ?></th>
                <th class="num"><?= e(money($tot['addebitato'])) ?></th>
                <th class="num"><?= e(money($tot['versato'])) ?></th>
                <th class="num"><?= e(money($tot['saldo'])) ?></th>
                <th class="num"><?= e(money($tot['scaduto'])) ?></th>
                <th></th>
            </tr>
            </tfoot>
        </table>
    </div>
    <p class="muted">Le quote scadono a fine trimestre. I versamenti destinati a una rata coprono quel trimestre, gli altri coprono prima le quote più vecchie. Le righe in rosso hanno quote non versate oltre la scadenza.</p>
<?php endif; ?>
