<?php
declare(strict_types=1);
defined('APP') || exit;

// Fase 1: riepilogo dell'archivio. Saldi, rate scadute e spese da pagare
// arriveranno con le fasi successive.
$condomini = Store::listNames('condominio_');
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
        <p class="muted">La gestione di condomini e unità sarà disponibile nella prossima fase.</p>
    </div>
<?php endif; ?>
