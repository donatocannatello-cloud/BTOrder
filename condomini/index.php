<?php
declare(strict_types=1);

// Unico punto d'ingresso: index.php?p=<pagina>
define('APP', true);
require __DIR__ . '/includes/bootstrap.php';

// Pagine consentite. 'public' = accessibile senza login.
$routes = [
    'login'           => ['file' => 'login.php',           'public' => true],
    'setup'           => ['file' => 'setup.php',           'public' => true],
    'logout'          => ['file' => 'logout.php',          'public' => false],
    'dashboard'       => ['file' => 'dashboard.php',       'public' => false],
    'condomini'       => ['file' => 'condomini.php',       'public' => false],
    'condominio'      => ['file' => 'condominio.php',      'public' => false],
    'condominio_form' => ['file' => 'condominio_form.php', 'public' => false],
    'unita_form'      => ['file' => 'unita_form.php',      'public' => false],
    'millesimi'       => ['file' => 'millesimi.php',       'public' => false],
    'impostazioni'    => ['file' => 'impostazioni.php',    'public' => false],
    'export'          => ['file' => 'export.php',          'public' => false],
];

$page = query('p');
if ($page === '') {
    $page = 'dashboard';
}

if (!config_has_password() && $page !== 'setup') {
    redirect('setup');
}

if (!isset($routes[$page])) {
    http_response_code(404);
    $errorTitle = 'Pagina non trovata';
    $errorMessage = 'La pagina richiesta non esiste.';
    $route = ['file' => 'errore.php', 'public' => true];
} else {
    $route = $routes[$page];
}

if (!$route['public'] && !auth_is_logged_in()) {
    if (!is_post()) {
        // Dopo il login si torna alla pagina richiesta.
        $_SESSION['after_login'] = ['p' => $page] + array_filter($_GET, 'is_string');
    }
    redirect('login');
}

if (is_post() && !csrf_verify($_POST['_csrf'] ?? null)) {
    http_response_code(400);
    $errorTitle = 'Richiesta non valida';
    $errorMessage = 'Il modulo è scaduto o non valido (token di sicurezza). Torna indietro, ricarica la pagina e riprova.';
    $route = ['file' => 'errore.php', 'public' => true];
}

$title = APP_NAME;
$layout = auth_is_logged_in() ? 'app' : 'bare';

ob_start();
require PAGES_DIR . '/' . $route['file'];
$content = (string) ob_get_clean();

require INCLUDES_DIR . '/layout.php';
