<?php
declare(strict_types=1);
defined('APP') || exit;

// ----------------------------------------------------------------------
// Output

/** Escape HTML: da usare per OGNI valore stampato nelle pagine. */
function e($value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

/** Importo in centesimi -> "1.234,56 €" */
function money(int $cents, bool $symbol = true): string
{
    $sign = $cents < 0 ? '-' : '';
    $abs = abs($cents);
    $str = $sign . number_format(intdiv($abs, 100), 0, ',', '.') . ',' . str_pad((string) ($abs % 100), 2, '0', STR_PAD_LEFT);
    return $symbol ? $str . ' €' : $str;
}

/** Importo per i campi dei form: "1234,56" */
function money_input(?int $cents): string
{
    if ($cents === null) {
        return '';
    }
    $sign = $cents < 0 ? '-' : '';
    $abs = abs($cents);
    return $sign . intdiv($abs, 100) . ',' . str_pad((string) ($abs % 100), 2, '0', STR_PAD_LEFT);
}

/**
 * Interpreta un importo digitato ("1.234,56", "1234.5", "12", "€ 3,00")
 * e restituisce i centesimi, oppure null se non valido.
 * Con la virgola presente i punti sono separatori delle migliaia; senza
 * virgola un unico punto è il separatore decimale.
 */
function parse_money(?string $input): ?int
{
    $s = str_replace(['€', ' ', "\u{00A0}", "'"], '', trim((string) $input));
    if ($s === '') {
        return null;
    }
    if (strpos($s, ',') !== false) {
        $s = str_replace('.', '', $s);
        $s = str_replace(',', '.', $s);
    } elseif (substr_count($s, '.') > 1) {
        $s = str_replace('.', '', $s);
    }
    if (!preg_match('/^(-?)(\d{1,12})(?:\.(\d{1,2}))?$/', $s, $m)) {
        return null;
    }
    $cents = (int) $m[2] * 100 + (int) str_pad($m[3] ?? '', 2, '0');
    return $m[1] === '-' ? -$cents : $cents;
}

/** "2026-10-07" -> "07/10/2026" */
function date_it(?string $ymd): string
{
    if (!$ymd || !preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', $ymd, $m)) {
        return '';
    }
    return "$m[3]/$m[2]/$m[1]";
}

/** Timestamp -> "07/10/2026 14:30" */
function datetime_it(?int $ts): string
{
    return $ts ? date('d/m/Y H:i', $ts) : '';
}

/** Accetta "gg/mm/aaaa" (anche con - o .) oppure "aaaa-mm-gg"; restituisce "aaaa-mm-gg" o null. */
function parse_date(?string $input): ?string
{
    $s = trim((string) $input);
    if (preg_match('/^(\d{4})-(\d{1,2})-(\d{1,2})$/', $s, $m)) {
        [$y, $mo, $d] = [(int) $m[1], (int) $m[2], (int) $m[3]];
    } elseif (preg_match('#^(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4})$#', $s, $m)) {
        [$d, $mo, $y] = [(int) $m[1], (int) $m[2], (int) $m[3]];
    } else {
        return null;
    }
    if (!checkdate($mo, $d, $y)) {
        return null;
    }
    return sprintf('%04d-%02d-%02d', $y, $mo, $d);
}

function today(): string
{
    return date('Y-m-d');
}

// ----------------------------------------------------------------------
// URL, redirect, messaggi flash

function base_path(): string
{
    $dir = str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/'));
    return rtrim($dir, '/') . '/';
}

function url(string $page, array $params = []): string
{
    return 'index.php?' . http_build_query(['p' => $page] + $params);
}

function asset(string $file): string
{
    $path = ROOT_DIR . '/assets/' . $file;
    $v = is_file($path) ? filemtime($path) : APP_VERSION;
    return 'assets/' . rawurlencode($file) . '?v=' . $v;
}

/** @return never */
function redirect(string $page, array $params = []): void
{
    header('Location: ' . base_path() . url($page, $params), true, 303);
    exit;
}

function flash(string $type, string $message): void
{
    $_SESSION['flash'][] = ['type' => $type, 'msg' => $message];
}

function take_flashes(): array
{
    $f = $_SESSION['flash'] ?? [];
    unset($_SESSION['flash']);
    return $f;
}

function is_https(): bool
{
    return (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || (int) ($_SERVER['SERVER_PORT'] ?? 0) === 443;
}

function is_post(): bool
{
    return ($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST';
}

function post(string $key, string $default = ''): string
{
    $v = $_POST[$key] ?? $default;
    return is_string($v) ? trim($v) : $default;
}

function query(string $key, string $default = ''): string
{
    $v = $_GET[$key] ?? $default;
    return is_string($v) ? trim($v) : $default;
}

function client_ip(): string
{
    return (string) ($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0');
}

function send_security_headers(): void
{
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Referrer-Policy: same-origin');
    header("Content-Security-Policy: default-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
    header('Cache-Control: no-store, private');
}

function format_bytes(int $bytes): string
{
    if ($bytes < 1024) {
        return $bytes . ' B';
    }
    if ($bytes < 1048576) {
        return number_format($bytes / 1024, 1, ',', '.') . ' KB';
    }
    return number_format($bytes / 1048576, 1, ',', '.') . ' MB';
}

/**
 * Numero decimale digitato all'italiana ("1.234,5678" o "12.5") con al massimo
 * $decimals cifre decimali. Restituisce null se non valido.
 */
function parse_decimal(?string $input, int $decimals = 4): ?float
{
    $s = str_replace([' ', "\u{00A0}"], '', trim((string) $input));
    if (strpos($s, ',') !== false) {
        $s = str_replace('.', '', $s);
        $s = str_replace(',', '.', $s);
    } elseif (substr_count($s, '.') > 1) {
        $s = str_replace('.', '', $s);
    }
    if (!preg_match('/^-?\d{1,9}(?:\.\d{1,' . $decimals . '})?$/', $s)) {
        return null;
    }
    return round((float) $s, $decimals);
}

// ----------------------------------------------------------------------
// Piccoli aiuti per i form

function selected(bool $cond): string
{
    return $cond ? ' selected' : '';
}

function checked(bool $cond): string
{
    return $cond ? ' checked' : '';
}

/** Elenco degli errori di validazione. */
function errors_box(array $errors): string
{
    if (!$errors) {
        return '';
    }
    $html = '<div class="alert alert-error" role="alert"><ul class="plain">';
    foreach ($errors as $err) {
        $html .= '<li>' . e($err) . '</li>';
    }
    return $html . '</ul></div>';
}
