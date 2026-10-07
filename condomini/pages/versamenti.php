<?php
declare(strict_types=1);
defined('APP') || exit;

// Incassi: versamenti dei proprietari e degli inquilini.
$c = condominio_or_redirect(query('id'));
$vid = query('v');
$edit = $vid !== '' ? versamento_find($c, $vid) : null;
$errors = [];
$raw = null;

if (is_post()) {
    if (post('action') === 'delete') {
        $del = post('v');
        condominio_update($c['id'], function (array $cur) use ($del) {
            $cur['versamenti'] = array_values(array_filter($cur['versamenti'], function ($x) use ($del) {
                return $x['id'] !== $del;
            }));
            return $cur;
        });
        flash('success', 'Versamento eliminato.');
        redirect('versamenti', ['id' => $c['id']]);
    }

    [$fields, $errors] = versamento_validate($_POST, $c);
    if (!$errors) {
        $savedId = $edit['id'] ?? Store::newId();
        condominio_update($c['id'], function (array $cur) use ($savedId, $fields) {
            foreach ($cur['versamenti'] as $i => $x) {
                if ($x['id'] === $savedId) {
                    $cur['versamenti'][$i] = ['id' => $savedId] + $fields;
                    return $cur;
                }
            }
            $cur['versamenti'][] = ['id' => $savedId] + $fields;
            return $cur;
        });
        flash('success', 'Versamento di ' . money($fields['importo']) . ($edit ? ' aggiornato.' : ' registrato.'));
        redirect('versamenti', ['id' => $c['id']]);
    }
    $raw = $_POST;
}

$sit = situazione($c);
// Chi può versare: proprietari, inquilini attuali e chiunque abbia già una posizione.
$chi = [];
foreach (unita_sorted($c) as $u) {
    foreach (SOGGETTI as $sog => $sogLabel) {
        $key = $u['id'] . '|' . $sog;
        if ($sog === 'proprietario' || $u['inquilino']['nome'] !== '' || isset($sit[$key])) {
            $nome = $sit[$key]['nome'] ?? $u[$sog]['nome'];
            $saldo = $sit[$key]['saldo'] ?? 0;
            $chi[$key] = unita_label($u) . ' · ' . $nome . ' (' . strtolower($sogLabel) . ')'
                . ($saldo > 0 ? ' · da versare ' . money($saldo) : '');
        }
    }
}

$form = [
    'chi' => $raw['chi'] ?? ($edit ? $edit['unita_id'] . '|' . $edit['soggetto'] : query('chi')),
    'data' => $raw['data'] ?? date_it($edit['data'] ?? today()),
    'importo' => $raw['importo'] ?? ($edit ? money_input($edit['importo']) : (isset($sit[query('chi')]) && $sit[query('chi')]['saldo'] > 0 ? money_input($sit[query('chi')]['saldo']) : '')),
    'metodo' => $raw['metodo'] ?? ($edit['metodo'] ?? 'bonifico'),
    'note' => $raw['note'] ?? ($edit['note'] ?? ''),
];

$anni = anni_movimenti($c);
$anno = isset($_GET['anno']) ? query('anno') : date('Y');
$lista = array_filter($c['versamenti'], function ($v) use ($anno) {
    return $anno === '' || substr($v['data'], 0, 4) === $anno;
});
usort($lista, function ($a, $b) {
    return strcmp($b['data'], $a['data']);
});
$title = 'Incassi · ' . $c['nome'];
?>
<?= condominio_header($c, 'versamenti') ?>

<?= errors_box($errors) ?>

<?php if (!$c['unita']): ?>
    <div class="alert alert-info">Aggiungi prima le unità del condominio.</div>
<?php else: ?>
    <form method="post" action="<?= e(url('versamenti', array_filter(['id' => $c['id'], 'v' => $edit['id'] ?? '']))) ?>" class="card form" id="form-versamento">
        <?= csrf_field() ?>
        <h3><?= $edit ? 'Modifica versamento' : 'Registra un versamento' ?></h3>
        <div class="form-row form-row-wide">
            <label class="span-2">Chi ha versato *
                <select name="chi" required>
                    <option value="">— scegli —</option>
                    <?php foreach ($chi as $k => $label): ?>
                        <option value="<?= e($k) ?>"<?= selected($form['chi'] === $k) ?>><?= e($label) ?></option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>Data *
                <input type="text" inputmode="numeric" placeholder="gg/mm/aaaa" maxlength="10" class="input-date" name="data" value="<?= e($form['data']) ?>" required>
            </label>
            <label>Importo (€) *
                <input type="text" name="importo" inputmode="decimal" placeholder="0,00" value="<?= e($form['importo']) ?>" required>
            </label>
            <label>Metodo *
                <select name="metodo" required>
                    <?php foreach (METODI_PAGAMENTO as $k => $l): ?>
                        <option value="<?= e($k) ?>"<?= selected($form['metodo'] === $k) ?>><?= e($l) ?></option>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>Note
                <input type="text" name="note" value="<?= e($form['note']) ?>" maxlength="200" placeholder="es. CRO, rif. rata">
            </label>
        </div>
        <div class="actions">
            <button type="submit" class="btn btn-primary"><?= $edit ? 'Salva' : 'Registra versamento' ?></button>
            <?php if ($edit): ?><a class="btn" href="<?= e(url('versamenti', ['id' => $c['id']])) ?>">Annulla</a><?php endif; ?>
        </div>
    </form>
<?php endif; ?>

<div class="toolbar">
    <h2>Versamenti registrati</h2>
    <form method="get" action="index.php" class="filters">
        <input type="hidden" name="p" value="versamenti">
        <input type="hidden" name="id" value="<?= e($c['id']) ?>">
        <label>Anno
            <select name="anno">
                <option value="">Tutti</option>
                <?php foreach ($anni as $a): ?>
                    <option value="<?= e($a) ?>"<?= selected($anno === $a) ?>><?= e($a) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
        <button type="submit" class="btn btn-small">Filtra</button>
    </form>
</div>

<?php if (!$lista): ?>
    <div class="card empty"><p>Nessun versamento<?= $anno !== '' ? ' nel ' . e($anno) : '' ?>.</p></div>
<?php else: ?>
    <div class="card table-wrap">
        <table class="table-compact">
            <thead>
            <tr><th>Data</th><th>Unità</th><th>Versato da</th><th>Metodo</th><th>Note</th><th class="num">Importo</th><th></th></tr>
            </thead>
            <tbody>
            <?php $tot = 0;
            foreach ($lista as $v):
                $tot += $v['importo'];
                $u = unita_find($c, $v['unita_id']);
                $key = $v['unita_id'] . '|' . $v['soggetto']; ?>
                <tr>
                    <td class="nowrap"><?= e(date_it($v['data'])) ?></td>
                    <td><?= e($u ? unita_label($u) : '—') ?></td>
                    <td><?= e($sit[$key]['nome'] ?? '') ?> <span class="muted">(<?= e(strtolower(SOGGETTI[$v['soggetto']] ?? '')) ?>)</span></td>
                    <td><?= e(METODI_PAGAMENTO[$v['metodo']] ?? $v['metodo']) ?></td>
                    <td class="small"><?= e($v['note']) ?></td>
                    <td class="num"><strong><?= e(money($v['importo'])) ?></strong></td>
                    <td class="actions-cell">
                        <a class="btn btn-small" href="<?= e(url('versamenti', ['id' => $c['id'], 'v' => $v['id']])) ?>">Modifica</a>
                        <form method="post" action="<?= e(url('versamenti', ['id' => $c['id']])) ?>"
                              data-confirm="Eliminare il versamento di <?= e(money($v['importo'])) ?> del <?= e(date_it($v['data'])) ?>?">
                            <?= csrf_field() ?>
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="v" value="<?= e($v['id']) ?>">
                            <button type="submit" class="btn btn-small btn-danger">Elimina</button>
                        </form>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
            <tfoot>
            <tr><th colspan="5">Totale incassato (<?= e(plural(count($lista), 'versamento', 'versamenti')) ?>)</th><th class="num"><?= e(money($tot)) ?></th><th></th></tr>
            </tfoot>
        </table>
    </div>
<?php endif; ?>
