<?php
declare(strict_types=1);
defined('APP') || exit;

/**
 * Archivio su file JSON in DATA_DIR.
 *
 * - Ogni "documento" è un file <nome>.json (nome: minuscole, cifre, _ e -).
 * - Ogni accesso è serializzato con flock() su un file di lock dedicato.
 * - Le scritture avvengono su un file temporaneo nella stessa cartella e poi
 *   con rename(), che è atomico: il file non è mai visibile a metà.
 * - Dopo ogni scrittura il nuovo contenuto viene copiato in data/backup con
 *   data e ora nel nome; si tengono gli ultimi BACKUP_KEEP per documento.
 */
final class Store
{
    private const NAME_RE = '/^[a-z0-9]+(?:[_-][a-z0-9]+)*$/';
    private const DENY_HTACCESS = "# Accesso diretto vietato: i file sono serviti solo dagli script PHP\n"
        . "<IfModule mod_authz_core.c>\n    Require all denied\n</IfModule>\n"
        . "<IfModule !mod_authz_core.c>\n    Order allow,deny\n    Deny from all\n</IfModule>\n";

    /** Crea le cartelle di lavoro e le protezioni .htaccess se mancano. */
    public static function ensureDirs(): void
    {
        foreach ([DATA_DIR, BACKUP_DIR, LOCK_DIR, SESSION_DIR, LOG_DIR, UPLOAD_DIR] as $dir) {
            if (!is_dir($dir) && !@mkdir($dir, 0750, true) && !is_dir($dir)) {
                throw new RuntimeException('Impossibile creare la cartella ' . basename($dir));
            }
        }
        // Alcuni client FTP non caricano i file nascosti: rigeneriamo le protezioni.
        foreach ([DATA_DIR, UPLOAD_DIR] as $dir) {
            $ht = $dir . '/.htaccess';
            if (!is_file($ht)) {
                @file_put_contents($ht, self::DENY_HTACCESS);
            }
        }
    }

    public static function exists(string $name): bool
    {
        return is_file(self::path($name));
    }

    /** Legge un documento; restituisce $default se il file non esiste. */
    public static function read(string $name, ?array $default = null): ?array
    {
        $lock = self::lock($name, LOCK_SH);
        try {
            return self::readUnlocked($name, $default);
        } finally {
            self::unlock($lock);
        }
    }

    /** Sostituisce interamente un documento. */
    public static function write(string $name, array $data, bool $backup = true): void
    {
        $lock = self::lock($name, LOCK_EX);
        try {
            self::writeUnlocked($name, $data, $backup);
        } finally {
            self::unlock($lock);
        }
    }

    /**
     * Legge-modifica-scrive sotto un unico lock esclusivo.
     * $fn riceve il documento corrente (o $default) e restituisce quello nuovo.
     */
    public static function update(string $name, callable $fn, array $default = [], bool $backup = true): array
    {
        $lock = self::lock($name, LOCK_EX);
        try {
            $current = self::readUnlocked($name, $default);
            $new = $fn($current);
            if (!is_array($new)) {
                throw new LogicException('La funzione di aggiornamento deve restituire un array');
            }
            self::writeUnlocked($name, $new, $backup);
            return $new;
        } finally {
            self::unlock($lock);
        }
    }

    public static function delete(string $name): void
    {
        $lock = self::lock($name, LOCK_EX);
        try {
            $path = self::path($name);
            if (is_file($path)) {
                self::backup($name, $path); // ultima copia, per poter recuperare
            }
            if (is_file($path) && !unlink($path)) {
                throw new RuntimeException("Impossibile eliminare $name");
            }
        } finally {
            self::unlock($lock);
        }
    }

    /** Nomi dei documenti che iniziano con $prefix (es. "condominio_"). */
    public static function listNames(string $prefix): array
    {
        $names = [];
        foreach (glob(DATA_DIR . '/' . $prefix . '*.json') ?: [] as $file) {
            $name = basename($file, '.json');
            if (preg_match(self::NAME_RE, $name)) {
                $names[] = $name;
            }
        }
        sort($names);
        return $names;
    }

    /** Elenco dei backup di un documento, dal più recente. */
    public static function backups(string $name): array
    {
        self::path($name); // valida il nome
        $files = glob(BACKUP_DIR . '/' . $name . '__*.json') ?: [];
        rsort($files, SORT_STRING);
        return $files;
    }

    /** Data/ora dell'ultimo backup tra tutti i documenti, o null. */
    public static function lastBackupTime(): ?int
    {
        $last = null;
        foreach (glob(BACKUP_DIR . '/*.json') ?: [] as $file) {
            $t = filemtime($file);
            if ($t !== false && ($last === null || $t > $last)) {
                $last = $t;
            }
        }
        return $last;
    }

    public static function newId(): string
    {
        return bin2hex(random_bytes(8));
    }

    // ------------------------------------------------------------------

    private static function path(string $name): string
    {
        if (!preg_match(self::NAME_RE, $name)) {
            throw new InvalidArgumentException('Nome documento non valido');
        }
        return DATA_DIR . '/' . $name . '.json';
    }

    /** @return resource */
    private static function lock(string $name, int $mode)
    {
        self::path($name);
        $h = fopen(LOCK_DIR . '/' . $name . '.lock', 'c');
        if ($h === false || !flock($h, $mode)) {
            throw new RuntimeException("Impossibile ottenere il lock su $name");
        }
        return $h;
    }

    /** @param resource $h */
    private static function unlock($h): void
    {
        flock($h, LOCK_UN);
        fclose($h);
    }

    private static function readUnlocked(string $name, ?array $default): ?array
    {
        $path = self::path($name);
        if (!is_file($path)) {
            return $default;
        }
        $raw = file_get_contents($path);
        if ($raw === false) {
            throw new RuntimeException("Impossibile leggere $name");
        }
        $data = json_decode($raw, true);
        if (!is_array($data)) {
            // Mai restituire il default qui: una scrittura successiva cancellerebbe i dati.
            throw new RuntimeException("Il file $name.json è danneggiato: ripristinalo da data/backup");
        }
        return $data;
    }

    private static function writeUnlocked(string $name, array $data, bool $backup): void
    {
        $path = self::path($name);
        $json = json_encode(
            $data,
            JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR
        );

        $tmp = DATA_DIR . '/.tmp_' . $name . '_' . bin2hex(random_bytes(4));
        $h = fopen($tmp, 'xb');
        if ($h === false) {
            throw new RuntimeException("Impossibile scrivere $name (cartella data non scrivibile?)");
        }
        $ok = fwrite($h, $json) === strlen($json) && fflush($h);
        if ($ok && function_exists('fsync')) {
            fsync($h);
        }
        fclose($h);
        if (!$ok || !rename($tmp, $path)) {
            @unlink($tmp);
            throw new RuntimeException("Salvataggio di $name non riuscito");
        }

        if ($backup) {
            self::backup($name, $path);
        }
    }

    private static function backup(string $name, string $path): void
    {
        $stamp = (new DateTime())->format('Ymd-His-u'); // ordinabile come stringa
        if (!@copy($path, BACKUP_DIR . '/' . $name . '__' . $stamp . '.json')) {
            error_log("Backup di $name non riuscito");
            return;
        }
        $files = self::backups($name);
        foreach (array_slice($files, BACKUP_KEEP) as $old) {
            @unlink($old);
        }
    }
}

// ----------------------------------------------------------------------
// Configurazione persistente (data/config.json)

function config(bool $reload = false): array
{
    static $cache = null;
    if ($cache === null || $reload) {
        $cache = Store::read('config', []) + [
            'password_hash' => null,
            'password_changed_at' => 0,
            'session_timeout' => DEFAULT_SESSION_TIMEOUT,
        ];
    }
    return $cache;
}

function config_update(array $changes): void
{
    Store::update('config', function (array $cfg) use ($changes) {
        return array_merge($cfg, $changes);
    });
    config(true);
}

function config_has_password(): bool
{
    return is_string(config()['password_hash']) && config()['password_hash'] !== '';
}

function config_timeout_minutes(): int
{
    $t = (int) config()['session_timeout'];
    return max(5, min(480, $t ?: DEFAULT_SESSION_TIMEOUT));
}
