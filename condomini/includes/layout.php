<?php
declare(strict_types=1);
defined('APP') || exit;

/** @var string $content  HTML prodotto dalla pagina */
/** @var string $title */
/** @var string $layout   'app' | 'bare' */
/** @var string $page     pagina corrente */

$nav = [
    'dashboard' => 'Dashboard',
    'condomini' => 'Condomini',
    'impostazioni' => 'Impostazioni',
];
$navActive = in_array($page, ['condominio', 'condominio_form', 'unita_form', 'millesimi'], true) ? 'condomini' : $page;
$flashes = take_flashes();
?>
<!doctype html>
<html lang="it">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title><?= e($title === APP_NAME ? APP_NAME : $title . ' · ' . APP_NAME) ?></title>
    <link rel="stylesheet" href="<?= e(asset('style.css')) ?>">
    <script src="<?= e(asset('app.js')) ?>" defer></script>
</head>
<body class="layout-<?= e($layout) ?>">
<?php if ($layout === 'app'): ?>
<header class="topbar">
    <div class="container topbar-inner">
        <a class="brand" href="<?= e(url('dashboard')) ?>"><?= e(APP_NAME) ?></a>
        <input type="checkbox" id="nav-toggle" class="nav-toggle" aria-label="Apri menu">
        <label for="nav-toggle" class="nav-burger" aria-hidden="true"><span></span><span></span><span></span></label>
        <nav class="nav">
            <?php foreach ($nav as $p => $label): ?>
                <a href="<?= e(url($p)) ?>"<?= $p === $navActive ? ' class="active"' : '' ?>><?= e($label) ?></a>
            <?php endforeach; ?>
            <form method="post" action="<?= e(url('logout')) ?>" class="nav-logout">
                <?= csrf_field() ?>
                <button type="submit" class="linklike">Esci</button>
            </form>
        </nav>
    </div>
</header>
<?php endif; ?>
<main class="container<?= $layout === 'bare' ? ' narrow' : '' ?>">
    <?php foreach ($flashes as $f): ?>
        <div class="alert alert-<?= e($f['type']) ?>" role="status"><?= e($f['msg']) ?></div>
    <?php endforeach; ?>
    <?= $content ?>
</main>
<?php if ($layout === 'app'): ?>
<footer class="footer container">
    <small><?= e(APP_NAME) ?> v<?= e(APP_VERSION) ?> · disconnessione automatica dopo <?= e(config_timeout_minutes()) ?> min di inattività</small>
</footer>
<?php endif; ?>
</body>
</html>
