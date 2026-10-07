<?php
declare(strict_types=1);
defined('APP') || exit;

// Nuova unità (senza u) o modifica (con u).
$c = condominio_or_redirect(query('id'));
$uid = query('u');
$u = $uid !== '' ? unita_find($c, $uid) : null;
if ($uid !== '' && $u === null) {
    flash('error', 'Unità non trovata.');
    redirect('condominio', ['id' => $c['id']]);
}

$errors = [];
$form = $u ?? condominio_normalize(['unita' => [['id' => '']]])['unita'][0];
$millRaw = [];

if (is_post()) {
    [$fields, $errors] = unita_validate($_POST, $c, $u['id'] ?? null);
    if (!$errors) {
        $savedId = $u['id'] ?? Store::newId();
        condominio_update($c['id'], function (array $cur) use ($fields, $savedId) {
            foreach ($cur['unita'] as $i => $x) {
                if ($x['id'] === $savedId) {
                    $cur['unita'][$i] = ['id' => $savedId] + $fields;
                    return $cur;
                }
            }
            $cur['unita'][] = ['id' => $savedId] + $fields;
            return $cur;
        });
        flash('success', unita_label($fields) . ($u ? ' aggiornata.' : ' aggiunta.'));
        if (post('next') === 'altra') {
            redirect('unita_form', ['id' => $c['id']]);
        }
        redirect('condominio', ['id' => $c['id']]);
    }
    $form = ['id' => $u['id'] ?? ''] + $fields;
    $millRaw = is_array($_POST['millesimi'] ?? null) ? $_POST['millesimi'] : [];
}

$title = ($u ? 'Modifica ' . unita_label($u) : 'Nuova unità') . ' · ' . $c['nome'];
?>
<?= condominio_header($c, 'condominio') ?>

<h2><?= $u ? 'Modifica ' . e(unita_label($u)) : 'Nuova unità' ?></h2>
<?= errors_box($errors) ?>

<form method="post" action="<?= e(url('unita_form', array_filter(['id' => $c['id'], 'u' => $u['id'] ?? '']))) ?>" class="form">
    <?= csrf_field() ?>
    <div class="grid grid-2">
        <section class="card">
            <h3>Unità</h3>
            <div class="form-row form-row-4">
                <label>Interno *
                    <input type="text" name="interno" value="<?= e($form['interno']) ?>" required maxlength="20" autofocus>
                </label>
                <label>Scala
                    <input type="text" name="scala" value="<?= e($form['scala']) ?>" maxlength="20">
                </label>
                <label>Piano
                    <input type="text" name="piano" value="<?= e($form['piano']) ?>" maxlength="20">
                </label>
            </div>
            <label>Descrizione <span class="muted">(es. appartamento, box, negozio)</span>
                <input type="text" name="descrizione" value="<?= e($form['descrizione']) ?>" maxlength="120">
            </label>
            <label>Note
                <textarea name="note" rows="2"><?= e($form['note']) ?></textarea>
            </label>
        </section>

        <section class="card">
            <h3>Millesimi</h3>
            <?php if (!$c['tabelle']): ?>
                <p class="muted">Nessuna tabella millesimale: creala in <a href="<?= e(url('millesimi', ['id' => $c['id']])) ?>">Tabelle millesimali</a>.</p>
            <?php endif; ?>
            <div class="form-row">
                <?php foreach ($c['tabelle'] as $t): ?>
                    <label><?= e($t['nome']) ?>
                        <input type="text" inputmode="decimal" name="millesimi[<?= e($t['id']) ?>]" placeholder="0"
                               value="<?= e(array_key_exists($t['id'], $millRaw) ? (string) $millRaw[$t['id']] : millesimi_input($form['millesimi'][$t['id']] ?? 0)) ?>">
                    </label>
                <?php endforeach; ?>
            </div>
            <p class="muted">Lascia vuoto (o 0) se l'unità non partecipa alle spese di quella tabella.</p>
        </section>

        <?php foreach (['proprietario' => 'Proprietario', 'inquilino' => 'Inquilino (se presente)'] as $k => $label): ?>
            <section class="card">
                <h3><?= e($label) ?></h3>
                <label>Nome e cognome<?= $k === 'proprietario' ? ' *' : '' ?>
                    <input type="text" name="<?= $k ?>[nome]" value="<?= e($form[$k]['nome']) ?>" maxlength="120"<?= $k === 'proprietario' ? ' required' : '' ?>>
                </label>
                <div class="form-row">
                    <label>Telefono
                        <input type="tel" name="<?= $k ?>[telefono]" value="<?= e($form[$k]['telefono']) ?>" maxlength="40">
                    </label>
                    <label>Email
                        <input type="email" name="<?= $k ?>[email]" value="<?= e($form[$k]['email']) ?>" maxlength="120">
                    </label>
                </div>
            </section>
        <?php endforeach; ?>
    </div>

    <div class="actions form-actions">
        <button type="submit" class="btn btn-primary">Salva</button>
        <?php if (!$u): ?>
            <button type="submit" name="next" value="altra" class="btn">Salva e aggiungi un'altra</button>
        <?php endif; ?>
        <a class="btn" href="<?= e(url('condominio', ['id' => $c['id']])) ?>">Annulla</a>
    </div>
</form>
