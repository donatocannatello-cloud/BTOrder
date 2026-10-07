<?php
declare(strict_types=1);
defined('APP') || exit;

$errors = [];

if (is_post()) {
    $action = post('action');

    if ($action === 'password') {
        $current = (string) ($_POST['current'] ?? '');
        $new = (string) ($_POST['password'] ?? '');
        if (!password_verify($current, (string) config()['password_hash'])) {
            $errors[] = 'La password attuale non è corretta.';
        } elseif ($problem = password_problem($new, (string) ($_POST['password2'] ?? ''))) {
            $errors[] = $problem;
        } else {
            auth_set_password($new);
            flash('success', 'Password aggiornata.');
            redirect('impostazioni');
        }
    } elseif ($action === 'timeout') {
        $min = (int) post('session_timeout');
        if ($min < 5 || $min > 480) {
            $errors[] = 'Il timeout deve essere tra 5 e 480 minuti.';
        } else {
            config_update(['session_timeout' => $min]);
            flash('success', 'Timeout di sessione aggiornato.');
            redirect('impostazioni');
        }
    }
}

// Diagnostica dell'installazione
$checks = [
    ['PHP ' . PHP_VERSION, version_compare(PHP_VERSION, '8.0.0', '>=')],
    ['Cartella data scrivibile', is_writable(DATA_DIR)],
    ['Cartella uploads scrivibile', is_writable(UPLOAD_DIR)],
    ['Protezione data/.htaccess presente', is_file(DATA_DIR . '/.htaccess')],
    ['Protezione uploads/.htaccess presente', is_file(UPLOAD_DIR . '/.htaccess')],
    ['Estensione zip (per "Esporta tutto")', class_exists('ZipArchive')],
    ['Estensione fileinfo (per gli allegati)', function_exists('finfo_open')],
    ['Estensione mbstring', function_exists('mb_strlen')],
    ['Limite upload di PHP: ' . format_bytes(upload_limit_bytes()), upload_limit_bytes() >= 2 * 1048576],
    ['Connessione HTTPS', is_https()],
];
$backupCount = count(glob(BACKUP_DIR . '/*.json') ?: []);
$lastBackup = Store::lastBackupTime();

$title = 'Impostazioni';
?>
<h1>Impostazioni</h1>

<?php foreach ($errors as $err): ?>
    <div class="alert alert-error" role="alert"><?= e($err) ?></div>
<?php endforeach; ?>

<div class="grid grid-2">
    <section class="card">
        <h2>Esporta tutto</h2>
        <p>Scarica uno ZIP con tutti i dati (file JSON) e gli allegati. Conservalo in un posto sicuro.</p>
        <form method="post" action="<?= e(url('export')) ?>" class="form">
            <?= csrf_field() ?>
            <label class="check"><input type="checkbox" name="backup" value="1"> Includi anche i backup automatici</label>
            <button type="submit" class="btn btn-primary">Scarica ZIP</button>
        </form>
        <p class="muted">Backup automatici presenti: <?= e($backupCount) ?>
            <?php if ($lastBackup): ?> · ultimo: <?= e(datetime_it($lastBackup)) ?><?php endif; ?>
            (ne vengono conservati <?= e(BACKUP_KEEP) ?> per ciascun file).</p>
    </section>

    <section class="card">
        <h2>Cambia password</h2>
        <form method="post" action="<?= e(url('impostazioni')) ?>" class="form">
            <?= csrf_field() ?>
            <input type="hidden" name="action" value="password">
            <label>Password attuale
                <input type="password" name="current" required autocomplete="current-password">
            </label>
            <label>Nuova password (min. <?= e(MIN_PASSWORD_LENGTH) ?> caratteri)
                <input type="password" name="password" required minlength="<?= e(MIN_PASSWORD_LENGTH) ?>" autocomplete="new-password">
            </label>
            <label>Ripeti nuova password
                <input type="password" name="password2" required minlength="<?= e(MIN_PASSWORD_LENGTH) ?>" autocomplete="new-password">
            </label>
            <button type="submit" class="btn">Aggiorna password</button>
        </form>
    </section>

    <section class="card">
        <h2>Sessione</h2>
        <form method="post" action="<?= e(url('impostazioni')) ?>" class="form">
            <?= csrf_field() ?>
            <input type="hidden" name="action" value="timeout">
            <label>Disconnessione dopo minuti di inattività
                <input type="number" name="session_timeout" min="5" max="480" value="<?= e(config_timeout_minutes()) ?>" required>
            </label>
            <button type="submit" class="btn">Salva</button>
        </form>
    </section>

    <section class="card">
        <h2>Verifica installazione</h2>
        <ul class="checklist">
            <?php foreach ($checks as [$label, $ok]): ?>
                <li class="<?= $ok ? 'ok' : 'ko' ?>"><?= $ok ? '✔' : '✖' ?> <?= e($label) ?></li>
            <?php endforeach; ?>
        </ul>
        <p class="muted">Verifica anche dal browser che
            <a href="data/config.json" target="_blank" rel="noopener">data/config.json</a>
            risponda con errore 403 (accesso negato).</p>
    </section>
</div>
