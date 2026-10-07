<?php
declare(strict_types=1);
defined('APP') || exit;

// Riepilogo. Saldi di cassa, rate scadute e spese da pagare arriveranno con la fase 3.
$condomini = condomini_all();
$lastBackup = Store::lastBackupTime();

$title = 'Dashboard';
?>
<h1>Dashboard</h1>

<div class="grid grid-3">
    <div class="card stat">
        <span class="stat-label">Condomini</span>
        <span class="stat-value"><?= e(count($condomini)) ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Ultimo backup</span>
        <span class="stat-value small"><?= $lastBackup ? e(datetime_it($lastBackup)) : '—' ?></span>
    </div>
    <div class="card stat">
        <span class="stat-label">Oggi</span>
        <span class="stat-value small"><?= e(date_it(today())) ?></span>
    </div>
</div>

<?php if (!$condomini): ?>
    <div class="card empty">
        <p>Non ci sono ancora condomini in archivio.</p>
        <p><a class="btn btn-primary" href="<?= e(url('condominio_form')) ?>">Crea il primo condominio</a></p>
    </div>
<?php else: ?>
    <div class="card">
        <h2>I tuoi condomini</h2>
        <ul class="cond-list">
            <?php foreach ($condomini as $c): ?>
                <li>
                    <span><a href="<?= e(url('condominio', ['id' => $c['id']])) ?>"><strong><?= e($c['nome']) ?></strong></a>
                        <span class="muted"><?= e($c['indirizzo']) ?></span></span>
                    <span class="muted"><?= e(count($c['unita'])) ?> unità · <?= e(RUOLI[$c['ruolo']] ?? '') ?></span>
                </li>
            <?php endforeach; ?>
        </ul>
    </div>
<?php endif; ?>
