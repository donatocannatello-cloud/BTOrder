<?php
declare(strict_types=1);
defined('APP') || exit;

/** @var array $routes */

if (auth_is_logged_in()) {
    redirect('dashboard');
}

$error = null;
if (is_post()) {
    $target = $_SESSION['after_login'] ?? null; // auth_login() azzera la sessione
    $error = auth_login((string) ($_POST['password'] ?? ''));
    if ($error === null) {
        $p = is_array($target) ? (string) ($target['p'] ?? '') : '';
        if (isset($routes[$p]) && !$routes[$p]['public'] && $p !== 'logout' && $p !== 'export') {
            unset($target['p']);
            redirect($p, $target);
        }
        redirect('dashboard');
    }
}

$title = 'Accesso';
?>
<div class="card login-card">
    <h1><?= e(APP_NAME) ?></h1>
    <?php if ($error): ?>
        <div class="alert alert-error" role="alert"><?= e($error) ?></div>
    <?php endif; ?>
    <form method="post" action="<?= e(url('login')) ?>" class="form">
        <?= csrf_field() ?>
        <label>Password
            <input type="password" name="password" required autofocus autocomplete="current-password">
        </label>
        <button type="submit" class="btn btn-primary btn-block">Entra</button>
    </form>
</div>
