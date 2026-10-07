<?php
declare(strict_types=1);
defined('APP') || exit;

// Tipologie di spesa: ordinaria/straordinaria, tabella predefinita, quota inquilino.
$c = condominio_or_redirect(query('id'));
$errors = [];
$rawNew = null;

if (is_post()) {
    $action = post('action');
    $kid = post('k');

    if ($action === 'delete') {
        $k = categoria_find($c, $kid);
        if ($k === null) {
            $errors[] = 'Tipologia non trovata.';
        } elseif ($why = categoria_in_uso($c, $kid)) {
            $errors[] = 'Impossibile eliminare "' . $k['nome'] . '": ' . $why . '.';
        } else {
            condominio_update($c['id'], function (array $cur) use ($kid) {
                $cur['categorie'] = array_values(array_filter($cur['categorie'], function ($x) use ($kid) {
                    return $x['id'] !== $kid;
                }));
                return $cur;
            });
            flash('success', 'Tipologia "' . $k['nome'] . '" eliminata.');
            redirect('categorie', ['id' => $c['id']]);
        }
    } elseif ($action === 'save' || $action === 'add') {
        $selfId = $action === 'save' ? $kid : null;
        if ($selfId !== null && categoria_find($c, $selfId) === null) {
            $errors[] = 'Tipologia non trovata.';
        } else {
            [$fields, $errors] = categoria_validate($_POST, $c, $selfId);
        }
        if (!$errors) {
            condominio_update($c['id'], function (array $cur) use ($selfId, $fields) {
                if ($selfId === null) {
                    $cur['categorie'][] = ['id' => Store::newId()] + $fields;
                } else {
                    foreach ($cur['categorie'] as &$x) {
                        if ($x['id'] === $selfId) {
                            $x = ['id' => $selfId] + $fields;
                        }
                    }
                    unset($x);
                }
                return $cur;
            });
            flash('success', 'Tipologia "' . $fields['nome'] . '" ' . ($selfId ? 'aggiornata.' : 'aggiunta.')
                . ($selfId ? ' Le spese già registrate non cambiano.' : ''));
            redirect('categorie', ['id' => $c['id']]);
        }
        if ($action === 'add') {
            $rawNew = $_POST;
        }
    }
}

$uso = [];
foreach ($c['uscite'] as $s) {
    $uso[$s['categoria_id']] = ($uso[$s['categoria_id']] ?? 0) + 1;
}

/** Riga di modifica (o di inserimento) di una tipologia. */
$row = function (array $k, string $action) use ($c) {
    $formId = 'cat-' . ($k['id'] ?? 'new');
    ob_start(); ?>
    <td>
        <input type="text" name="nome" form="<?= e($formId) ?>" value="<?= e($k['nome'] ?? '') ?>" maxlength="80" required
               placeholder="<?= $action === 'add' ? 'Nuova tipologia' : '' ?>" aria-label="Nome">
    </td>
    <td>
        <select name="tipo" form="<?= e($formId) ?>" aria-label="Tipo">
            <?php foreach (TIPI_SPESA as $t => $l): ?>
                <option value="<?= e($t) ?>"<?= selected(($k['tipo'] ?? 'ordinaria') === $t) ?>><?= e($l) ?></option>
            <?php endforeach; ?>
        </select>
    </td>
    <td>
        <select name="tabella_id" form="<?= e($formId) ?>" aria-label="Tabella">
            <?php foreach ($c['tabelle'] as $t): ?>
                <option value="<?= e($t['id']) ?>"<?= selected(($k['tabella_id'] ?? '') === $t['id']) ?>><?= e($t['nome']) ?></option>
            <?php endforeach; ?>
        </select>
    </td>
    <td class="num">
        <input type="number" name="quota_inquilino" form="<?= e($formId) ?>" min="0" max="100" step="1" class="input-pct"
               value="<?= e($k['quota_inquilino'] ?? 0) ?>" aria-label="Percentuale inquilino"> %
    </td>
    <?php return (string) ob_get_clean();
};

$title = 'Tipologie di spesa · ' . $c['nome'];
?>
<?= condominio_header($c, 'categorie') ?>

<?= errors_box($errors) ?>

<p class="muted">Ogni tipologia indica se la spesa è ordinaria o straordinaria, la tabella millesimale proposta
    e la percentuale a carico dell'inquilino (il resto è a carico del proprietario). I valori iniziali seguono la prassi
    della L. 392/1978; adattali al regolamento e ai contratti di locazione. Le modifiche valgono per le nuove spese.</p>

<div class="card table-wrap">
    <table class="table-compact table-form">
        <thead>
        <tr><th>Tipologia</th><th>Tipo</th><th>Tabella predefinita</th><th class="num">A carico inquilino</th><th class="num">Spese</th><th></th></tr>
        </thead>
        <tbody>
        <?php foreach (categorie_sorted($c) as $k): ?>
            <tr>
                <?= $row($k, 'save') ?>
                <td class="num"><?= e($uso[$k['id']] ?? 0) ?></td>
                <td class="actions-cell">
                    <form method="post" id="cat-<?= e($k['id']) ?>" action="<?= e(url('categorie', ['id' => $c['id']])) ?>">
                        <?= csrf_field() ?>
                        <input type="hidden" name="action" value="save">
                        <input type="hidden" name="k" value="<?= e($k['id']) ?>">
                        <button type="submit" class="btn btn-small">Salva</button>
                    </form>
                    <?php if (empty($uso[$k['id']])): ?>
                        <form method="post" action="<?= e(url('categorie', ['id' => $c['id']])) ?>"
                              data-confirm="Eliminare la tipologia &quot;<?= e($k['nome']) ?>&quot;?">
                            <?= csrf_field() ?>
                            <input type="hidden" name="action" value="delete">
                            <input type="hidden" name="k" value="<?= e($k['id']) ?>">
                            <button type="submit" class="btn btn-small btn-danger">Elimina</button>
                        </form>
                    <?php endif; ?>
                </td>
            </tr>
        <?php endforeach; ?>
        </tbody>
        <tfoot>
        <tr class="add-row-tr">
            <?= $row($rawNew ? [
                'nome' => (string) ($rawNew['nome'] ?? ''), 'tipo' => (string) ($rawNew['tipo'] ?? ''),
                'tabella_id' => (string) ($rawNew['tabella_id'] ?? ''), 'quota_inquilino' => (string) ($rawNew['quota_inquilino'] ?? '0'),
            ] : [], 'add') ?>
            <td></td>
            <td class="actions-cell">
                <form method="post" id="cat-new" action="<?= e(url('categorie', ['id' => $c['id']])) ?>">
                    <?= csrf_field() ?>
                    <input type="hidden" name="action" value="add">
                    <button type="submit" class="btn btn-small btn-primary">+ Aggiungi</button>
                </form>
            </td>
        </tr>
        </tfoot>
    </table>
</div>
