<?php
declare(strict_types=1);
defined('APP') || exit;

function auth_start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    // Sessioni in una cartella privata (data/sessions, protetta da .htaccess):
    // sull'hosting condiviso la cartella di default è comune e ripulita con
    // tempi che non controlliamo.
    if (is_dir(SESSION_DIR) && is_writable(SESSION_DIR)) {
        session_save_path(SESSION_DIR);
        ini_set('session.gc_probability', '1');
        ini_set('session.gc_divisor', '100');
    }
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    ini_set('session.use_trans_sid', '0');
    ini_set('session.gc_maxlifetime', (string) (config_timeout_minutes() * 60 + 600));
    session_name('CONDOSESS');
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => base_path(),
        'secure' => is_https(),
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
    session_start();
}

/** Verifica login e timeout di inattività; aggiorna l'orario dell'ultima attività. */
function auth_is_logged_in(): bool
{
    static $checked = null;
    if ($checked !== null) {
        return $checked;
    }
    if (empty($_SESSION['auth'])) {
        return $checked = false;
    }
    $now = time();
    $expired = $now - (int) ($_SESSION['last_activity'] ?? 0) > config_timeout_minutes() * 60;
    // Un cambio password invalida tutte le sessioni aperte prima.
    $stale = (int) ($_SESSION['login_at'] ?? 0) < (int) config()['password_changed_at'];
    if ($expired || $stale) {
        auth_logout();
        flash('info', $expired ? 'Sessione scaduta per inattività: accedi di nuovo.' : 'La password è cambiata: accedi di nuovo.');
        return $checked = false;
    }
    $_SESSION['last_activity'] = $now;
    return $checked = true;
}

/**
 * Tenta il login. Restituisce null se riuscito, altrimenti il messaggio d'errore.
 */
function auth_login(string $password): ?string
{
    $key = 'login:' . client_ip();
    $wait = throttle_remaining($key);
    if ($wait > 0) {
        return 'Troppi tentativi falliti. Riprova tra ' . throttle_human($wait) . '.';
    }

    $hash = (string) config()['password_hash'];
    if ($hash === '' || !password_verify($password, $hash)) {
        throttle_fail($key);
        usleep(random_int(400000, 900000));
        $wait = throttle_remaining($key);
        return $wait > 0
            ? 'Password errata. Accesso bloccato per ' . throttle_human($wait) . '.'
            : 'Password errata.';
    }

    throttle_clear($key);
    if (password_needs_rehash($hash, PASSWORD_DEFAULT)) {
        config_update(['password_hash' => password_hash($password, PASSWORD_DEFAULT)]);
    }

    session_regenerate_id(true);
    $_SESSION = [
        'auth' => true,
        'login_at' => time(),
        'last_activity' => time(),
    ];
    return null;
}

function auth_logout(): void
{
    $_SESSION = [];
    session_regenerate_id(true);
}

/** Imposta una nuova password e invalida le altre sessioni (resta attiva quella corrente). */
function auth_set_password(string $password): void
{
    $now = time();
    config_update([
        'password_hash' => password_hash($password, PASSWORD_DEFAULT),
        'password_changed_at' => $now,
    ]);
    if (!empty($_SESSION['auth'])) {
        session_regenerate_id(true);
        $_SESSION['login_at'] = $now;
    }
}

function password_problem(string $password, string $confirm): ?string
{
    if (strlen($password) < MIN_PASSWORD_LENGTH) {
        return 'La password deve avere almeno ' . MIN_PASSWORD_LENGTH . ' caratteri.';
    }
    if ($password !== $confirm) {
        return 'Le due password non coincidono.';
    }
    return null;
}

// ----------------------------------------------------------------------
// Limite ai tentativi (data/login_attempts.json, senza backup)

function throttle_remaining(string $key): int
{
    $all = Store::read('login_attempts', []);
    $until = (int) ($all[$key]['locked_until'] ?? 0);
    return max(0, $until - time());
}

function throttle_fail(string $key): void
{
    Store::update('login_attempts', function (array $all) use ($key) {
        $now = time();
        // Pulizia delle voci vecchie (oltre 24 ore senza blocchi attivi).
        foreach ($all as $k => $r) {
            if (($r['locked_until'] ?? 0) < $now && ($r['last'] ?? 0) < $now - 86400) {
                unset($all[$k]);
            }
        }
        $r = $all[$key] ?? ['fails' => 0, 'first' => $now, 'locks' => 0, 'locked_until' => 0];
        if ($now - $r['first'] > LOGIN_WINDOW) {
            $r['fails'] = 0;
            $r['first'] = $now;
        }
        $r['fails']++;
        $r['last'] = $now;
        if ($r['fails'] >= LOGIN_MAX_ATTEMPTS) {
            $r['locked_until'] = $now + min(86400, LOGIN_LOCKOUT * (2 ** $r['locks']));
            $r['locks']++;
            $r['fails'] = 0;
            $r['first'] = $now;
            error_log('Accesso bloccato per ' . $key);
        }
        $all[$key] = $r;
        return $all;
    }, [], false);
}

function throttle_clear(string $key): void
{
    Store::update('login_attempts', function (array $all) use ($key) {
        unset($all[$key]);
        return $all;
    }, [], false);
}

function throttle_human(int $seconds): string
{
    $min = (int) ceil($seconds / 60);
    return $min <= 1 ? 'un minuto' : $min . ' minuti';
}
