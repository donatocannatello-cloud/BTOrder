<?php
declare(strict_types=1);
defined('APP') || exit;

// Impostazione della password iniziale: disponibile solo finché non ne esiste una.
if (config_has_password()) {
    redirect(auth_is_logged_in() ? 'dashboard' : 'login');
}

$keyConfigured = SETUP_KEY !== '' && SETUP_KEY !== 'CAMBIAMI' && strlen(SETUP_KEY) >= 8;
$error = null;

if (is_post() && $keyConfigured) {
    $throttleKey = 'setup:' . client_ip();
    $wait = throttle_remaining($throttleKey);
    if ($wait > 0) {
        $error = 'Troppi tentativi falliti. Riprova tra ' . throttle_human($wait) . '.';
    } elseif (!hash_equals(SETUP_KEY, (string) ($_POST['setup_key'] ?? ''))) {
        throttle_fail($throttleKey);
        $error = 'Chiave di installazione errata.';
    } else {
        $pw = (string) ($_POST['password'] ?? '');
        $error = password_problem($pw, (string) ($_POST['password2'] ?? ''));
        if ($error === null) {
            throttle_clear($throttleKey);
            auth_set_password($pw);
            flash('success', 'Password impostata. Ora puoi accedere.');
            redirect('login');
        }
    }
}

$title = 'Installazione';
?>
<div class="card login-card">
    <h1>Prima configurazione</h1>
    <?php if (!$keyConfigured): ?>
        <div class="alert alert-error">
            La chiave di installazione non è impostata. Apri <code>includes/config.php</code>,
            sostituisci <code>CAMBIAMI</code> in <code>SETUP_KEY</code> con una chiave di almeno 8 caratteri,
            ricarica il file sul server e aggiorna questa pagina.
        </div>
    <?php else: ?>
        <p>Scegli la password di accesso (almeno <?= e(MIN_PASSWORD_LENGTH) ?> caratteri).</p>
        <?php if ($error): ?>
            <div class="alert alert-error" role="alert"><?= e($error) ?></div>
        <?php endif; ?>
        <form method="post" action="<?= e(url('setup')) ?>" class="form">
            <?= csrf_field() ?>
            <label>Chiave di installazione (da <code>includes/config.php</code>)
                <input type="password" name="setup_key" required autocomplete="off">
            </label>
            <label>Nuova password
                <input type="password" name="password" required minlength="<?= e(MIN_PASSWORD_LENGTH) ?>" autocomplete="new-password">
            </label>
            <label>Ripeti password
                <input type="password" name="password2" required minlength="<?= e(MIN_PASSWORD_LENGTH) ?>" autocomplete="new-password">
            </label>
            <button type="submit" class="btn btn-primary btn-block">Imposta password</button>
        </form>
    <?php endif; ?>
</div>
