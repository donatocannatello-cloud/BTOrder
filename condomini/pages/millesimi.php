<?php
declare(strict_types=1);
defined('APP') || exit;

// Tabelle millesimali: gestione delle tabelle e griglia unità × tabelle.
$c = condominio_or_redirect(query('id'));
$errors = [];
$gridRaw = null; // valori digitati da rimostrare in caso di errore

if (is_post()) {
    $action = post('action');
    $back = function () use ($c) {
        redirect('millesimi', ['id' => $c['id']]);
    };

    if ($action === 'add_table' || $action === 'rename_table') {
        $nome = post('nome');
        $tid = post('t');
        if ($nome === '' || strlen($nome) > 60) {
            $errors[] = 'Il nome della tabella è obbligatorio (max 60 caratteri).';
        } else {
            foreach ($c['tabelle'] as $t) {
                if ($t['id'] !== $tid && strcasecmp($t['nome'], $nome) === 0) {
                    $errors[] = 'Esiste già una tabella "' . $t['nome'] . '".';
                }
            }
        }
        if ($action === 'rename_table' && tabella_find($c, $tid) === null) {
            $errors[] = 'Tabella non trovata.';
        }
        if (!$errors) {
            condominio_update($c['id'], function (array $cur) use ($action, $nome, $tid) {
                if ($action === 'add_table') {
                    $cur['tabelle'][] = ['id' => Store::newId(), 'nome' => $nome];
                } else {
                    foreach ($cur['tabelle'] as &$t) {
                        if ($t['id'] === $tid) {
                            $t['nome'] = $nome;
                        }
                    }
                    unset($t);
                }
                return $cur;
            });
            flash('success', $action === 'add_table' ? 'Tabella "' . $nome . '" aggiunta.' : 'Tabella rinominata.');
            $back();
        }
    } elseif ($action === 'delete_table') {
        $tid = post('t');
        $t = tabella_find($c, $tid);
        if ($t === null) {
            $errors[] = 'Tabella non trovata.';
        } elseif ($why = tabella_in_uso($c, $tid)) {
            $errors[] = 'Impossibile eliminare la tabella "' . $t['nome'] . '": ' . $why . '.';
        } else {
            condominio_update($c['id'], function (array $cur) use ($tid) {
                $cur['tabelle'] = array_values(array_filter($cur['tabelle'], function ($x) use ($tid) {
                    return $x['id'] !== $tid;
                }));
                foreach ($cur['unita'] as &$u) {
                    unset($u['millesimi'][$tid]);
                }
                unset($u);
                return $cur;
            });
            flash('success', 'Tabella "' . $t['nome'] . '" eliminata.');
            $back();
        }
    } elseif ($action === 'save_grid') {
        $gridRaw = is_array($_POST['m'] ?? null) ? $_POST['m'] : [];
        $parsed = [];
        foreach ($c['unita'] as $u) {
            foreach ($c['tabelle'] as $t) {
                $raw = $gridRaw[$u['id']][$t['id']] ?? '';
                $raw = is_string($raw) ? trim($raw) : '';
                $v = $raw === '' ? 0.0 : parse_decimal($raw, 4);
                if ($v === null || $v < 0) {
                    $errors[] = 'Valore non valido per ' . unita_label($u) . ', tabella "' . $t['nome'] . '": ' . $raw;
                    continue;
                }
                $parsed[$u['id']][$t['id']] = $v;
            }
        }
        if (!$errors) {
            condominio_update($c['id'], function (array $cur) use ($parsed) {
                foreach ($cur['unita'] as &$u) {
                    if (!isset($parsed[$u['id']])) {
                        continue; // unità aggiunta nel frattempo: non toccarla
                    }
                    foreach ($parsed[$u['id']] as $tid => $v) {
                        if ($v > 0) {
                            $u['millesimi'][$tid] = $v;
                        } else {
                            unset($u['millesimi'][$tid]);
                        }
                    }
                }
                unset($u);
                return $cur;
            });
            flash('success', 'Millesimi salvati.');
            $back();
        }
    }
}

$unita = unita_sorted($c);
$title = 'Tabelle millesimali · ' . $c['nome'];
?>
<?= condominio_header($c, 'millesimi') ?>

<?= errors_box($errors) ?>

<div class="toolbar"><h2>Millesimi per unità</h2></div>
<?php if (!$unita || !$c['tabelle']): ?>
    <div class="card empty">
        <p><?= !$unita ? 'Aggiungi prima le unità del condominio.' : 'Crea almeno una tabella millesimale.' ?></p>
        <?php if (!$unita): ?>
            <p><a class="btn btn-primary" href="<?= e(url('unita_form', ['id' => $c['id']])) ?>">+ Nuova unità</a></p>
        <?php endif; ?>
    </div>
<?php else: ?>
    <form method="post" action="<?= e(url('millesimi', ['id' => $c['id']])) ?>" class="card">
        <?= csrf_field() ?>
        <input type="hidden" name="action" value="save_grid">
        <div class="table-wrap">
            <table class="table-compact grid-input">
                <thead>
                <tr>
                    <th>Unità</th>
                    <th>Proprietario</th>
                    <?php foreach ($c['tabelle'] as $t): ?>
                        <th class="num"><?= e($t['nome']) ?></th>
                    <?php endforeach; ?>
                </tr>
                </thead>
                <tbody>
                <?php foreach ($unita as $u): ?>
                    <tr>
                        <td><strong><?= e(unita_label($u)) ?></strong></td>
                        <td><?= e($u['proprietario']['nome']) ?></td>
                        <?php foreach ($c['tabelle'] as $t):
                            $val = $gridRaw !== null
                                ? (string) ($gridRaw[$u['id']][$t['id']] ?? '')
                                : millesimi_input($u['millesimi'][$t['id']] ?? 0); ?>
                            <td class="num">
                                <input type="text" inputmode="decimal" class="input-num"
                                       name="m[<?= e($u['id']) ?>][<?= e($t['id']) ?>]" value="<?= e($val) ?>"
                                       aria-label="<?= e($t['nome'] . ' ' . unita_label($u)) ?>" data-col="<?= e($t['id']) ?>">
                            </td>
                        <?php endforeach; ?>
                    </tr>
                <?php endforeach; ?>
                </tbody>
                <tfoot>
                <tr>
                    <th colspan="2">Totale</th>
                    <?php foreach ($c['tabelle'] as $t):
                        $tot = tabella_totale($c, $t['id']); ?>
                        <th class="num <?= abs($tot - 1000) < 0.001 ? 'pos' : 'warn' ?>" data-total="<?= e($t['id']) ?>"><?= e(millesimi_fmt($tot)) ?></th>
                    <?php endforeach; ?>
                </tr>
                </tfoot>
            </table>
        </div>
        <div class="actions form-actions">
            <button type="submit" class="btn btn-primary">Salva millesimi</button>
            <span class="muted">Usa la virgola per i decimali (es. 83,25). Celle vuote = l'unità non partecipa.</span>
        </div>
    </form>
<?php endif; ?>

<div class="toolbar"><h2>Tabelle</h2></div>
<div class="card table-wrap">
    <table class="table-compact">
        <thead>
        <tr><th>Nome</th><th class="num">Unità coinvolte</th><th class="num">Totale</th><th></th></tr>
        </thead>
        <tbody>
        <?php foreach ($c['tabelle'] as $t):
            $n = 0;
            foreach ($c['unita'] as $u) {
                $n += ($u['millesimi'][$t['id']] ?? 0) > 0 ? 1 : 0;
            } ?>
            <tr>
                <td>
                    <form method="post" action="<?= e(url('millesimi', ['id' => $c['id']])) ?>" class="inline-form">
                        <?= csrf_field() ?>
                        <input type="hidden" name="action" value="rename_table">
                        <input type="hidden" name="t" value="<?= e($t['id']) ?>">
                        <input type="text" name="nome" value="<?= e($t['nome']) ?>" maxlength="60" required aria-label="Nome tabella">
                        <button type="submit" class="btn btn-small">Rinomina</button>
                    </form>
                </td>
                <td class="num"><?= e($n) ?></td>
                <td class="num"><?= e(millesimi_fmt(tabella_totale($c, $t['id']))) ?></td>
                <td class="actions-cell">
                    <form method="post" action="<?= e(url('millesimi', ['id' => $c['id']])) ?>"
                          data-confirm="Eliminare la tabella &quot;<?= e($t['nome']) ?>&quot; e i relativi millesimi di tutte le unità?">
                        <?= csrf_field() ?>
                        <input type="hidden" name="action" value="delete_table">
                        <input type="hidden" name="t" value="<?= e($t['id']) ?>">
                        <button type="submit" class="btn btn-small btn-danger">Elimina</button>
                    </form>
                </td>
            </tr>
        <?php endforeach; ?>
        </tbody>
    </table>
    <form method="post" action="<?= e(url('millesimi', ['id' => $c['id']])) ?>" class="inline-form add-row">
        <?= csrf_field() ?>
        <input type="hidden" name="action" value="add_table">
        <input type="text" name="nome" placeholder="Nuova tabella (es. Scala B, Ascensore)" maxlength="60" required aria-label="Nome nuova tabella">
        <button type="submit" class="btn btn-primary btn-small">+ Aggiungi tabella</button>
    </form>
</div>
