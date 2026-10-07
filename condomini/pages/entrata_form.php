<?php
declare(strict_types=1);
defined('APP') || exit;

// Nuova entrata ricorrente (senza e) o modifica (con e).
$c = condominio_or_redirect(query('id'));
$eid = query('e');
$e = $eid !== '' ? entrata_find($c, $eid) : null;
if ($eid !== '' && $e === null) {
    flash('error', 'Entrata non trovata.');
    redirect('entrate', ['id' => $c['id']]);
}
$errors = [];
$raw = null;

if (is_post()) {
    if ($e && post('action') === 'delete') {
        condominio_update($c['id'], function (array $cur) use ($eid) {
            $cur['entrate'] = array_values(array_filter($cur['entrate'], function ($x) use ($eid) {
                return $x['id'] !== $eid;
            }));
            return $cur;
        });
        flash('success', 'Entrata "' . $e['descrizione'] . '" eliminata con il suo storico incassi.');
        redirect('entrate', ['id' => $c['id']]);
    }

    [$fields, $errors] = entrata_validate($_POST, $c);
    if (!$errors) {
        $savedId = $e['id'] ?? Store::newId();
        condominio_update($c['id'], function (array $cur) use ($savedId, $fields) {
            foreach ($cur['entrate'] as &$x) {
                if ($x['id'] === $savedId) {
                    $x = array_merge($x, $fields);   // gli incassi già registrati restano
                    return $cur;
                }
            }
            unset($x);
            $cur['entrate'][] = ['id' => $savedId] + $fields + ['pagamenti' => []];
            return $cur;
        });
        flash('success', 'Entrata "' . $fields['descrizione'] . '" ' . ($e ? 'aggiornata.' : 'creata: le scadenze sono nello scadenziario.'));
        redirect('entrate', ['id' => $c['id'], 'anno' => substr($fields['data_inizio'], 0, 4) > date('Y') ? substr($fields['data_inizio'], 0, 4) : date('Y')]);
    }
    $raw = $_POST;
}

$f = function (string $k, string $fallback) use ($raw) {
    return $raw !== null ? (string) ($raw[$k] ?? '') : $fallback;
};
$inCassa = $raw !== null ? !empty($raw['in_cassa']) : ($e['in_cassa'] ?? true);
$title = ($e ? 'Modifica entrata' : 'Nuova entrata') . ' · ' . $c['nome'];
?>
<?= condominio_header($c, 'entrate') ?>

<p class="breadcrumb"><a href="<?= e(url('entrate', ['id' => $c['id']])) ?>">‹ Affitti e altre entrate</a></p>
<h2><?= $e ? 'Modifica: ' . e($e['descrizione']) : 'Nuova entrata' ?></h2>
<?= errors_box($errors) ?>

<form method="post" action="<?= e(url('entrata_form', array_filter(['id' => $c['id'], 'e' => $e['id'] ?? '']))) ?>" class="card form form-wide">
    <?= csrf_field() ?>
    <div class="form-row">
        <label>Descrizione *
            <input type="text" name="descrizione" value="<?= e($f('descrizione', $e['descrizione'] ?? '')) ?>" maxlength="120" required
                   placeholder="es. Affitto locale ex portineria" autofocus>
        </label>
        <label>Debitore (chi paga) *
            <input type="text" name="debitore" value="<?= e($f('debitore', $e['debitore'] ?? '')) ?>" maxlength="120" required>
        </label>
    </div>
    <div class="form-row">
        <label>Importo per scadenza (€) *
            <input type="text" name="importo" inputmode="decimal" placeholder="0,00" required
                   value="<?= e($f('importo', $e ? money_input($e['importo']) : '')) ?>">
        </label>
        <label>Cadenza *
            <select name="frequenza" required>
                <?php foreach (FREQUENZE as $k => [$l, $_]): ?>
                    <option value="<?= e($k) ?>"<?= selected($f('frequenza', $e['frequenza'] ?? 'mensile') === $k) ?>><?= e($l) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
    </div>
    <div class="form-row">
        <label>Prima scadenza *
            <input type="text" name="data_inizio" class="input-date" inputmode="numeric" placeholder="gg/mm/aaaa" maxlength="10" required
                   value="<?= e($f('data_inizio', date_it($e['data_inizio'] ?? today()))) ?>">
        </label>
        <label>Ultima scadenza <span class="muted">(vuoto = senza fine)</span>
            <input type="text" name="data_fine" class="input-date" inputmode="numeric" placeholder="gg/mm/aaaa" maxlength="10"
                   value="<?= e($f('data_fine', date_it($e['data_fine'] ?? ''))) ?>">
        </label>
    </div>
    <p class="muted">Le scadenze successive cadono nello stesso giorno del mese della prima (es. il 5 di ogni mese);
        se il mese è più corto si usa l'ultimo giorno.</p>
    <label>Unità collegata <span class="muted">(facoltativa)</span>
        <select name="unita_id">
            <option value="">— nessuna —</option>
            <?php foreach (unita_sorted($c) as $u): ?>
                <option value="<?= e($u['id']) ?>"<?= selected($f('unita_id', $e['unita_id'] ?? '') === $u['id']) ?>><?= e(unita_label($u) . ' · ' . $u['proprietario']['nome']) ?></option>
            <?php endforeach; ?>
        </select>
    </label>
    <label class="check"><input type="checkbox" name="in_cassa" value="1"<?= checked($inCassa) ?>>
        Gli incassi entrano nella cassa del condominio</label>
    <p class="muted">Togli la spunta per entrate personali (es. l'affitto del tuo appartamento): restano nello scadenziario
        ma non modificano il saldo di cassa condominiale.</p>
    <label>Note
        <textarea name="note" rows="2"><?= e($f('note', $e['note'] ?? '')) ?></textarea>
    </label>
    <div class="actions">
        <button type="submit" class="btn btn-primary"><?= $e ? 'Salva' : 'Crea entrata' ?></button>
        <a class="btn" href="<?= e(url('entrate', ['id' => $c['id']])) ?>">Annulla</a>
    </div>
</form>

<?php if ($e): ?>
    <form method="post" action="<?= e(url('entrata_form', ['id' => $c['id'], 'e' => $e['id']])) ?>" class="card danger-zone form-wide"
          data-confirm="Eliminare l'entrata e tutto lo storico degli incassi?">
        <?= csrf_field() ?>
        <input type="hidden" name="action" value="delete">
        <h3>Elimina entrata</h3>
        <p class="muted">Elimina anche gli incassi registrati (<?= e(count($e['pagamenti'])) ?>). Se il contratto è terminato,
            è meglio impostare l'ultima scadenza e conservare lo storico.</p>
        <button type="submit" class="btn btn-danger">Elimina entrata</button>
    </form>
<?php endif; ?>
