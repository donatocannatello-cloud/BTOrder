<?php
declare(strict_types=1);
defined('APP') || exit;

require __DIR__ . '/config.php';
require __DIR__ . '/helpers.php';
require __DIR__ . '/storage.php';
require __DIR__ . '/csrf.php';
require __DIR__ . '/auth.php';
require __DIR__ . '/condomini.php';
require __DIR__ . '/contabilita.php';
require __DIR__ . '/report.php';

date_default_timezone_set('Europe/Rome');
error_reporting(E_ALL);
ini_set('display_errors', '0');
ini_set('log_errors', '1');

set_error_handler(function (int $no, string $msg, string $file, int $line): bool {
    if (!(error_reporting() & $no)) {
        return false;
    }
    throw new ErrorException($msg, 0, $no, $file, $line);
});

set_exception_handler(function (Throwable $ex): void {
    error_log((string) $ex);
    while (ob_get_level() > 0) {
        ob_end_clean();
    }
    if (!headers_sent()) {
        http_response_code(500);
        header('Content-Type: text/html; charset=utf-8');
    }
    // I messaggi di RuntimeException sono scritti per l'utente; gli altri no.
    $msg = $ex instanceof RuntimeException ? $ex->getMessage() : 'Si è verificato un errore imprevisto. Dettagli nel log in data/logs.';
    echo '<!doctype html><html lang="it"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<title>Errore</title><link rel="stylesheet" href="assets/style.css">'
        . '<main class="container narrow"><div class="card"><h1>Errore</h1><p>' . e($msg) . '</p>'
        . '<p><a class="btn" href="index.php">Torna alla home</a></p></div></main></html>';
});

Store::ensureDirs();
ini_set('error_log', LOG_DIR . '/php_errors.log');

send_security_headers();
auth_start_session();
