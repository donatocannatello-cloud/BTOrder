<?php
declare(strict_types=1);
defined('APP') || exit;

// Nuovo condominio (senza id) o modifica dell'anagrafica (con id).
$id = query('id');
$c = $id !== '' ? condominio_or_redirect($id) : null;
$errors = [];
$form = $c ?? condominio_normalize(['ruolo' => 'amministratore']);

if (is_post()) {
    if (post('action') === 'delete' && $c) {
        if (post('conferma') !== $c['nome']) {
            $errors[] = 'Per eliminare scrivi esattamente il nome del condominio.';
        } else {
            condominio_delete($c['id']);
            flash('success', 'Condominio "' . $c['nome'] . '" eliminato. Una copia resta in data/backup.');
            redirect('condomini');
        }
    } else {
        [$fields, $errors] = condominio_validate($_POST);
        $form = array_merge($form, $fields);
        if (!$errors) {
            if ($c) {
                condominio_update($c['id'], function (array $cur) use ($fields) {
                    return array_merge($cur, $fields);
                });
                flash('success', 'Anagrafica salvata.');
                redirect('condominio', ['id' => $c['id']]);
            }
            $newId = condominio_create($fields);
            flash('success', 'Condominio creato con le tabelle Generale, Scale e Riscaldamento. Ora aggiungi le unità.');
            redirect('condominio', ['id' => $newId]);
        }
        // Rimostra i valori digitati così come sono
        $form['saldo_iniziale_raw'] = post('saldo_iniziale');
        $form['data_saldo_iniziale_raw'] = post('data_saldo_iniziale');
    }
}

$title = $c ? 'Anagrafica · ' . $c['nome'] : 'Nuovo condominio';
?>
<?php if ($c): ?>
    <?= condominio_header($c, 'condominio_form') ?>
<?php else: ?>
    <p class="breadcrumb"><a href="<?= e(url('condomini')) ?>">Condomini</a> ›</p>
    <h1>Nuovo condominio</h1>
<?php endif; ?>

<?= errors_box($errors) ?>

<form method="post" action="<?= e(url('condominio_form', $c ? ['id' => $c['id']] : [])) ?>" class="card form form-wide">
    <?= csrf_field() ?>
    <div class="form-row">
        <label>Nome *
            <input type="text" name="nome" value="<?= e($form['nome']) ?>" required maxlength="120" autofocus>
        </label>
        <label>Codice fiscale
            <input type="text" name="codice_fiscale" value="<?= e($form['codice_fiscale']) ?>" maxlength="16" class="upper">
        </label>
    </div>
    <label>Indirizzo
        <input type="text" name="indirizzo" value="<?= e($form['indirizzo']) ?>" maxlength="200">
    </label>
    <fieldset class="radio-group">
        <legend>Il mio ruolo *</legend>
        <?php foreach (RUOLI as $k => $label): ?>
            <label class="check"><input type="radio" name="ruolo" value="<?= e($k) ?>"<?= checked($form['ruolo'] === $k) ?>> <?= e($label) ?></label>
        <?php endforeach; ?>
    </fieldset>
    <div class="form-row">
        <label>Saldo di cassa iniziale (€)
            <input type="text" name="saldo_iniziale" inputmode="decimal" placeholder="0,00"
                   value="<?= e($form['saldo_iniziale_raw'] ?? money_input((int) $form['saldo_iniziale'])) ?>">
        </label>
        <label>Alla data
            <input type="date" name="data_saldo_iniziale"
                   value="<?= e($form['data_saldo_iniziale_raw'] ?? $form['data_saldo_iniziale']) ?>">
        </label>
    </div>
    <label>Note
        <textarea name="note" rows="3"><?= e($form['note']) ?></textarea>
    </label>
    <div class="actions">
        <button type="submit" class="btn btn-primary"><?= $c ? 'Salva' : 'Crea condominio' ?></button>
        <a class="btn" href="<?= e($c ? url('condominio', ['id' => $c['id']]) : url('condomini')) ?>">Annulla</a>
    </div>
</form>

<?php if ($c): ?>
    <form method="post" action="<?= e(url('condominio_form', ['id' => $c['id']])) ?>" class="card form form-wide danger-zone"
          data-confirm="Eliminare definitivamente il condominio e tutti i suoi dati?">
        <?= csrf_field() ?>
        <input type="hidden" name="action" value="delete">
        <h2>Elimina condominio</h2>
        <p class="muted">Elimina unità, tabelle, spese e rate di questo condominio. Una copia resta nei backup automatici.</p>
        <div class="form-row">
            <label>Per confermare scrivi il nome: <strong><?= e($c['nome']) ?></strong>
                <input type="text" name="conferma" autocomplete="off">
            </label>
        </div>
        <button type="submit" class="btn btn-danger">Elimina condominio</button>
    </form>
<?php endif; ?>
