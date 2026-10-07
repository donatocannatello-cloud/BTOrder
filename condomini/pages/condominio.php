<?php
declare(strict_types=1);
defined('APP') || exit;

// Scheda del condominio: elenco delle unità con millesimi e contatti.
$c = condominio_or_redirect(query('id'));

if (is_post() && post('action') === 'delete_unit') {
    $uid = post('u');
    $u = unita_find($c, $uid);
    if ($u === null) {
        flash('error', 'Unità non trovata.');
    } elseif ($why = unita_in_uso($c, $uid)) {
        flash('error', 'Impossibile eliminare ' . unita_label($u) . ': ' . $why . '.');
    } else {
        condominio_update($c['id'], function (array $cur) use ($uid) {
            $cur['unita'] = array_values(array_filter($cur['unita'], function ($x) use ($uid) {
                return $x['id'] !== $uid;
            }));
            return $cur;
        });
        flash('success', unita_label($u) . ' eliminata.');
    }
    redirect('condominio', ['id' => $c['id']]);
}

$unita = unita_sorted($c);
$title = $c['nome'];
?>
<?= condominio_header($c, 'condominio') ?>

<div class="toolbar">
    <h2>Unità (<?= e(count($unita)) ?>)</h2>
    <div class="actions">
        <a class="btn" href="<?= e(url('millesimi', ['id' => $c['id']])) ?>">Modifica millesimi</a>
        <a class="btn btn-primary" href="<?= e(url('unita_form', ['id' => $c['id']])) ?>">+ Nuova unità</a>
    </div>
</div>

<?php if (!$unita): ?>
    <div class="card empty">
        <p>Nessuna unità registrata.</p>
        <p><a class="btn btn-primary" href="<?= e(url('unita_form', ['id' => $c['id']])) ?>">Aggiungi la prima unità</a></p>
    </div>
<?php else: ?>
    <div class="card table-wrap">
        <table class="table-compact">
            <thead>
            <tr>
                <th>Scala</th>
                <th>Int.</th>
                <th>Piano</th>
                <th>Proprietario</th>
                <th>Inquilino</th>
                <th>Contatti</th>
                <?php foreach ($c['tabelle'] as $t): ?>
                    <th class="num" title="Millesimi <?= e($t['nome']) ?>"><?= e($t['nome']) ?></th>
                <?php endforeach; ?>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($unita as $u):
                $contatto = $u['inquilino']['nome'] !== '' ? $u['inquilino'] : $u['proprietario']; ?>
                <tr>
                    <td><?= e($u['scala']) ?></td>
                    <td><strong><?= e($u['interno']) ?></strong></td>
                    <td><?= e($u['piano']) ?></td>
                    <td><?= e($u['proprietario']['nome']) ?></td>
                    <td><?= $u['inquilino']['nome'] !== '' ? e($u['inquilino']['nome']) : '<span class="muted">—</span>' ?></td>
                    <td class="small">
                        <?php if ($contatto['telefono'] !== ''): ?><a href="tel:<?= e(preg_replace('/[^0-9+]/', '', $contatto['telefono'])) ?>"><?= e($contatto['telefono']) ?></a><br><?php endif; ?>
                        <?php if ($contatto['email'] !== ''): ?><a href="mailto:<?= e($contatto['email']) ?>"><?= e($contatto['email']) ?></a><?php endif; ?>
                    </td>
                    <?php foreach ($c['tabelle'] as $t):
                        $m = (float) ($u['millesimi'][$t['id']] ?? 0); ?>
                        <td class="num"><?= $m > 0 ? e(millesimi_fmt($m)) : '<span class="muted">—</span>' ?></td>
                    <?php endforeach; ?>
                    <td class="actions-cell">
                        <a class="btn btn-small" href="<?= e(url('unita_form', ['id' => $c['id'], 'u' => $u['id']])) ?>">Modifica</a>
                        <form method="post" action="<?= e(url('condominio', ['id' => $c['id']])) ?>"
                              data-confirm="Eliminare <?= e(unita_label($u)) ?> (<?= e($u['proprietario']['nome']) ?>)?">
                            <?= csrf_field() ?>
                            <input type="hidden" name="action" value="delete_unit">
                            <input type="hidden" name="u" value="<?= e($u['id']) ?>">
                            <button type="submit" class="btn btn-small btn-danger">Elimina</button>
                        </form>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
            <tfoot>
            <tr>
                <th colspan="6">Totale millesimi</th>
                <?php foreach ($c['tabelle'] as $t):
                    $tot = tabella_totale($c, $t['id']); ?>
                    <th class="num <?= abs($tot - 1000) < 0.001 ? 'pos' : 'warn' ?>"
                        title="<?= abs($tot - 1000) < 0.001 ? 'Quadra a 1000' : 'Non quadra a 1000' ?>"><?= e(millesimi_fmt($tot)) ?></th>
                <?php endforeach; ?>
                <th></th>
            </tr>
            </tfoot>
        </table>
    </div>
    <p class="muted">I totali in arancione non quadrano a 1000: il riparto userà comunque le proporzioni tra le unità.</p>
<?php endif; ?>

<?php if ($c['note'] !== ''): ?>
    <div class="card"><h2>Note</h2><p class="pre"><?= e($c['note']) ?></p></div>
<?php endif; ?>
