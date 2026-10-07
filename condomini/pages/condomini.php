<?php
declare(strict_types=1);
defined('APP') || exit;

$condomini = condomini_all();
$title = 'Condomini';
?>
<div class="page-head">
    <h1>Condomini</h1>
    <a class="btn btn-primary" href="<?= e(url('condominio_form')) ?>">+ Nuovo condominio</a>
</div>

<?php if (!$condomini): ?>
    <div class="card empty">
        <p>Nessun condominio in archivio.</p>
        <p><a class="btn btn-primary" href="<?= e(url('condominio_form')) ?>">Crea il primo condominio</a></p>
    </div>
<?php else: ?>
    <div class="card table-wrap">
        <table>
            <thead>
            <tr>
                <th>Nome</th>
                <th>Indirizzo</th>
                <th>Codice fiscale</th>
                <th>Mio ruolo</th>
                <th class="num">Unità</th>
                <th class="num">Tabelle</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <?php foreach ($condomini as $c): ?>
                <tr>
                    <td><a href="<?= e(url('uscite', ['id' => $c['id']])) ?>"><strong><?= e($c['nome']) ?></strong></a></td>
                    <td><?= e($c['indirizzo']) ?></td>
                    <td><?= e($c['codice_fiscale']) ?></td>
                    <td><?= e(RUOLI[$c['ruolo']] ?? $c['ruolo']) ?></td>
                    <td class="num"><?= e(count($c['unita'])) ?></td>
                    <td class="num"><?= e(count($c['tabelle'])) ?></td>
                    <td class="actions-cell">
                        <a class="btn btn-small" href="<?= e(url('uscite', ['id' => $c['id']])) ?>">Apri</a>
                        <a class="btn btn-small" href="<?= e(url('condominio_form', ['id' => $c['id']])) ?>">Modifica</a>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
<?php endif; ?>
