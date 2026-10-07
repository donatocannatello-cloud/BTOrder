<?php
declare(strict_types=1);
defined('APP') || exit;

// Nuova spesa (senza s) o scheda della spesa con riparto (con s).
$c = condominio_or_redirect(query('id'));
$sid = query('s');
$s = $sid !== '' ? uscita_find($c, $sid) : null;
if ($sid !== '' && $s === null) {
    flash('error', 'Spesa non trovata.');
    redirect('uscite', ['id' => $c['id']]);
}
$self = function () use ($c, &$s) {
    redirect('uscita_form', ['id' => $c['id'], 's' => $s['id']]);
};

$errors = [];
$form = $s ?? [
    'data' => today(), 'categoria_id' => '', 'fornitore' => '', 'descrizione' => '', 'importo' => null,
    'tabella_id' => '', 'quota_inquilino' => 0, 'scadenza' => '', 'pagata' => false, 'data_pagamento' => '',
];
$raw = null;

if (is_post()) {
    $action = post('action', 'save');

    if ($s && $action === 'delete') {
        condominio_update($c['id'], function (array $cur) use ($sid) {
            $cur['uscite'] = array_values(array_filter($cur['uscite'], function ($x) use ($sid) {
                return $x['id'] !== $sid;
            }));
            return $cur;
        });
        foreach ($s['allegati'] as $a) {
            allegato_elimina_file($c['id'], $a);
        }
        flash('success', 'Spesa eliminata.');
        redirect('uscite', ['id' => $c['id']]);
    }

    if ($s && $action === 'del_allegato') {
        $aid = post('a');
        $found = null;
        condominio_update($c['id'], function (array $cur) use ($sid, $aid, &$found) {
            foreach ($cur['uscite'] as &$x) {
                if ($x['id'] === $sid) {
                    foreach ($x['allegati'] as $i => $a) {
                        if ($a['id'] === $aid) {
                            $found = $a;
                            unset($x['allegati'][$i]);
                        }
                    }
                    $x['allegati'] = array_values($x['allegati']);
                }
            }
            unset($x);
            return $cur;
        });
        if ($found) {
            allegato_elimina_file($c['id'], $found);
            flash('success', 'Allegato "' . $found['nome'] . '" eliminato.');
        }
        $self();
    }

    if ($s && $action === 'ricalcola') {
        $nuovo = riparto_calcola($c, $s['tabella_id'], $s['importo'], $s['quota_inquilino']);
        if (!$nuovo) {
            $errors[] = 'La tabella della spesa non ha più millesimi: impossibile ricalcolare.';
        } else {
            condominio_update($c['id'], function (array $cur) use ($sid, $nuovo) {
                foreach ($cur['uscite'] as &$x) {
                    if ($x['id'] === $sid) {
                        $x['riparto'] = $nuovo;
                    }
                }
                unset($x);
                return $cur;
            });
            flash('success', 'Riparto ricalcolato con millesimi e inquilini attuali.');
            $self();
        }
    }

    if ($action === 'save') {
        [$fields, $errors] = uscita_validate($_POST, $c);
        $files = uploaded_files('allegati');
        foreach ($files as $file) {
            if ($p = allegato_problema($file)) {
                $errors[] = $p;
            }
        }
        if (!$errors) {
            // Il riparto si ricalcola solo se cambiano i dati che lo determinano.
            $ricalcola = !$s || $s['importo'] !== $fields['importo'] || $s['tabella_id'] !== $fields['tabella_id']
                || (int) $s['quota_inquilino'] !== $fields['quota_inquilino'] || empty($s['riparto']);
            $fields['riparto'] = $ricalcola
                ? riparto_calcola($c, $fields['tabella_id'], $fields['importo'], $fields['quota_inquilino'])
                : $s['riparto'];
            $nuovi = [];
            foreach ($files as $file) {
                $nuovi[] = allegato_salva($c['id'], $file);
            }
            $savedId = $s['id'] ?? Store::newId();
            condominio_update($c['id'], function (array $cur) use ($savedId, $fields, $nuovi) {
                foreach ($cur['uscite'] as &$x) {
                    if ($x['id'] === $savedId) {
                        $x = array_merge($x, $fields);
                        $x['allegati'] = array_merge($x['allegati'] ?? [], $nuovi);
                        return $cur;
                    }
                }
                unset($x);
                $cur['uscite'][] = ['id' => $savedId] + $fields + ['allegati' => $nuovi];
                return $cur;
            });
            flash('success', ($s ? 'Spesa aggiornata' : 'Spesa registrata') . ($ricalcola ? ' e ripartita tra le unità.' : '.'));
            if (post('next') === 'altra') {
                redirect('uscita_form', ['id' => $c['id']]);
            }
            redirect('uscita_form', ['id' => $c['id'], 's' => $savedId]);
        }
        if ($files) {
            $errors[] = 'Riseleziona gli allegati: per sicurezza i file non vengono conservati se il modulo ha errori.';
        }
        $raw = $_POST;
    }
}

$val = function (string $k, string $fallback) use ($raw) {
    return $raw !== null ? (string) ($raw[$k] ?? '') : $fallback;
};
$categorie = categorie_sorted($c);
$tabNome = array_column($c['tabelle'], 'nome', 'id');
$title = ($s ? 'Spesa del ' . date_it($s['data']) : 'Nuova spesa') . ' · ' . $c['nome'];
?>
<?= condominio_header($c, 'uscite') ?>

<p class="breadcrumb"><a href="<?= e(url('uscite', ['id' => $c['id']])) ?>">‹ Tutte le spese</a></p>
<h2><?= $s ? 'Spesa del ' . e(date_it($s['data'])) . ' · ' . e(money($s['importo'])) : 'Nuova spesa' ?></h2>
<?= errors_box($errors) ?>

<form method="post" enctype="multipart/form-data" class="form"
      action="<?= e(url('uscita_form', array_filter(['id' => $c['id'], 's' => $s['id'] ?? '']))) ?>">
    <?= csrf_field() ?>
    <input type="hidden" name="action" value="save">
    <div class="grid grid-2">
        <section class="card">
            <h3>Spesa</h3>
            <div class="form-row">
                <label>Data *
                    <input type="text" inputmode="numeric" placeholder="gg/mm/aaaa" maxlength="10" class="input-date" name="data" value="<?= e($val('data', date_it($form['data']))) ?>" required>
                </label>
                <label>Importo (€) *
                    <input type="text" name="importo" inputmode="decimal" placeholder="0,00" required
                           value="<?= e($val('importo', money_input($form['importo']))) ?>">
                </label>
            </div>
            <label>Tipologia *
                <select name="categoria_id" required data-categoria>
                    <option value="">— scegli —</option>
                    <?php foreach (TIPI_SPESA as $tipo => $tipoLabel): ?>
                        <optgroup label="<?= e($tipoLabel) ?>">
                            <?php foreach ($categorie as $k): if ($k['tipo'] !== $tipo) continue; ?>
                                <option value="<?= e($k['id']) ?>" data-tabella="<?= e($k['tabella_id']) ?>" data-quota="<?= e($k['quota_inquilino']) ?>"
                                    <?= selected($val('categoria_id', $form['categoria_id']) === $k['id']) ?>><?= e($k['nome']) ?></option>
                            <?php endforeach; ?>
                        </optgroup>
                    <?php endforeach; ?>
                </select>
            </label>
            <label>Fornitore
                <input type="text" name="fornitore" value="<?= e($val('fornitore', $form['fornitore'])) ?>" maxlength="120">
            </label>
            <label>Descrizione
                <input type="text" name="descrizione" value="<?= e($val('descrizione', $form['descrizione'])) ?>" maxlength="250"
                       placeholder="es. fattura n. 123 del 05/10/2026">
            </label>
        </section>

        <section class="card">
            <h3>Riparto</h3>
            <div class="form-row">
                <label>Tabella millesimale *
                    <select name="tabella_id" required data-tabella-select>
                        <option value="">— scegli —</option>
                        <?php foreach ($c['tabelle'] as $t): ?>
                            <option value="<?= e($t['id']) ?>"<?= selected($val('tabella_id', $form['tabella_id']) === $t['id']) ?>><?= e($t['nome']) ?></option>
                        <?php endforeach; ?>
                    </select>
                </label>
                <label>A carico inquilino (%)
                    <input type="number" name="quota_inquilino" min="0" max="100" step="1" data-quota-input
                           value="<?= e($val('quota_inquilino', (string) $form['quota_inquilino'])) ?>">
                </label>
            </div>
            <p class="muted">Tabella e percentuale si compilano dalla tipologia; puoi cambiarle per questa spesa.
                Senza inquilino, la quota va tutta al proprietario.</p>
            <label>Scadenza versamento quote
                <input type="text" inputmode="numeric" placeholder="automatica: fine trimestre" maxlength="10" class="input-date" name="scadenza" value="<?= e($val('scadenza', date_it($form['scadenza']))) ?>">
            </label>
            <p class="muted">Lascia vuoto per la scadenza trimestrale<?= $s ? ' (' . e(date_it(scadenza_quote($s))) . ')' : '' ?>:
                le quote scadono alla fine del trimestre della spesa (31/03, 30/06, 30/09, 31/12).
                Oltre la scadenza le quote non versate risultano morose.</p>
        </section>

        <section class="card">
            <h3>Pagamento al fornitore</h3>
            <label class="check"><input type="checkbox" name="pagata" value="1"<?= checked($raw !== null ? !empty($raw['pagata']) : (bool) $form['pagata']) ?>> Pagata</label>
            <label>Data pagamento
                <input type="text" inputmode="numeric" placeholder="gg/mm/aaaa" maxlength="10" class="input-date" name="data_pagamento" value="<?= e($val('data_pagamento', date_it($form['data_pagamento']))) ?>">
            </label>
            <p class="muted">Se segni "Pagata" senza data, si usa la data di oggi.</p>
        </section>

        <section class="card">
            <h3>Allegati (fattura, ricevuta)</h3>
            <?php if ($s && $s['allegati']): ?>
                <ul class="plain attach-list">
                    <?php foreach ($s['allegati'] as $a): ?>
                        <li>
                            <a href="<?= e(url('allegato', ['id' => $c['id'], 's' => $s['id'], 'a' => $a['id']])) ?>" target="_blank" rel="noopener">📎 <?= e($a['nome']) ?></a>
                            <span class="muted">(<?= e(format_bytes((int) $a['size'])) ?>)</span>
                            <button type="submit" form="del-<?= e($a['id']) ?>" class="btn btn-small btn-danger">Elimina</button>
                        </li>
                    <?php endforeach; ?>
                </ul>
            <?php endif; ?>
            <label>Aggiungi file
                <input type="file" name="allegati[]" multiple accept=".pdf,image/*">
            </label>
            <p class="muted">PDF o foto, max <?= e(format_bytes(min(UPLOAD_MAX_BYTES, upload_limit_bytes()))) ?> per file.</p>
        </section>
    </div>

    <div class="actions form-actions">
        <button type="submit" class="btn btn-primary"><?= $s ? 'Salva modifiche' : 'Registra e ripartisci' ?></button>
        <?php if (!$s): ?>
            <button type="submit" name="next" value="altra" class="btn">Registra e inserisci un'altra</button>
        <?php endif; ?>
        <a class="btn" href="<?= e(url('uscite', ['id' => $c['id']])) ?>">Annulla</a>
    </div>
</form>

<?php if ($s): ?>
    <?php foreach ($s['allegati'] as $a): ?>
        <form method="post" id="del-<?= e($a['id']) ?>" action="<?= e(url('uscita_form', ['id' => $c['id'], 's' => $s['id']])) ?>"
              data-confirm="Eliminare l'allegato &quot;<?= e($a['nome']) ?>&quot;?" hidden>
            <?= csrf_field() ?>
            <input type="hidden" name="action" value="del_allegato">
            <input type="hidden" name="a" value="<?= e($a['id']) ?>">
        </form>
    <?php endforeach; ?>

    <div class="toolbar">
        <h2>Riparto: tabella <?= e($tabNome[$s['tabella_id']] ?? '?') ?>, inquilino <?= e($s['quota_inquilino']) ?>%</h2>
        <form method="post" action="<?= e(url('uscita_form', ['id' => $c['id'], 's' => $s['id']])) ?>"
              data-confirm="Ricalcolare il riparto con i millesimi e gli inquilini attuali?">
            <?= csrf_field() ?>
            <input type="hidden" name="action" value="ricalcola">
            <button type="submit" class="btn btn-small">Ricalcola riparto</button>
        </form>
    </div>
    <div class="card table-wrap">
        <table class="table-compact">
            <thead>
            <tr>
                <th>Unità</th>
                <th class="num">Millesimi</th>
                <th class="num">Quota unità</th>
                <th>Proprietario</th>
                <th class="num">A carico prop.</th>
                <th>Inquilino</th>
                <th class="num">A carico inq.</th>
            </tr>
            </thead>
            <tbody>
            <?php $sum = ['quota' => 0, 'prop' => 0, 'inq' => 0, 'm' => 0.0];
            foreach ($s['riparto'] as $r):
                $u = unita_find($c, $r['unita_id']);
                $sum['quota'] += $r['quota']; $sum['prop'] += $r['prop']; $sum['inq'] += $r['inq']; $sum['m'] += $r['millesimi']; ?>
                <tr>
                    <td><?= e($u ? unita_label($u) : '(unità eliminata)') ?></td>
                    <td class="num"><?= e(millesimi_fmt((float) $r['millesimi'])) ?></td>
                    <td class="num"><strong><?= e(money($r['quota'])) ?></strong></td>
                    <td><?= e($r['prop_nome']) ?></td>
                    <td class="num"><?= e(money($r['prop'])) ?></td>
                    <td><?= $r['inq_nome'] !== '' ? e($r['inq_nome']) : '<span class="muted">—</span>' ?></td>
                    <td class="num"><?= $r['inq'] > 0 ? e(money($r['inq'])) : '<span class="muted">—</span>' ?></td>
                </tr>
            <?php endforeach; ?>
            </tbody>
            <tfoot>
            <tr>
                <th>Totale</th>
                <th class="num"><?= e(millesimi_fmt(round($sum['m'], 4))) ?></th>
                <th class="num"><?= e(money($sum['quota'])) ?></th>
                <th></th>
                <th class="num"><?= e(money($sum['prop'])) ?></th>
                <th></th>
                <th class="num"><?= e(money($sum['inq'])) ?></th>
            </tr>
            </tfoot>
        </table>
    </div>
    <p class="muted">Il riparto è salvato con la spesa: modifiche successive a millesimi o inquilini non lo cambiano,
        a meno di premere "Ricalcola riparto" o di modificare importo, tabella o percentuale.</p>

    <form method="post" action="<?= e(url('uscita_form', ['id' => $c['id'], 's' => $s['id']])) ?>" class="card danger-zone form-wide"
          data-confirm="Eliminare definitivamente questa spesa, il suo riparto e gli allegati?">
        <?= csrf_field() ?>
        <input type="hidden" name="action" value="delete">
        <h3>Elimina spesa</h3>
        <p class="muted">Gli addebiti ai condòmini derivanti da questa spesa verranno annullati.</p>
        <button type="submit" class="btn btn-danger">Elimina spesa</button>
    </form>
<?php endif; ?>
