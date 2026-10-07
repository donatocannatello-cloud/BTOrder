<?php
declare(strict_types=1);
defined('APP') || exit;

/*
 * Configurazione statica dell'applicazione.
 *
 * PRIMA DEL CARICAMENTO: sostituisci 'CAMBIAMI' con una chiave lunga a tua scelta.
 * Serve solo alla prima apertura, per impostare la password iniziale; dopo
 * che la password è stata impostata la pagina di installazione si disattiva.
 */
const SETUP_KEY = 'CAMBIAMI';

const APP_NAME = 'Gestione Condomini';
const APP_VERSION = '1.0.0';

const DEFAULT_SESSION_TIMEOUT = 30;   // minuti di inattività prima del logout
const MIN_PASSWORD_LENGTH = 10;
const BACKUP_KEEP = 30;               // backup conservati per ciascun file
const LOGIN_MAX_ATTEMPTS = 5;         // tentativi falliti consentiti...
const LOGIN_WINDOW = 900;             // ...in questo intervallo (secondi)
const LOGIN_LOCKOUT = 900;            // blocco iniziale (raddoppia a ogni blocco, max 24 ore)
const UPLOAD_MAX_BYTES = 10 * 1024 * 1024;

define('ROOT_DIR', dirname(__DIR__));
define('INCLUDES_DIR', ROOT_DIR . '/includes');
define('PAGES_DIR', ROOT_DIR . '/pages');
define('DATA_DIR', ROOT_DIR . '/data');
define('BACKUP_DIR', DATA_DIR . '/backup');
define('LOCK_DIR', DATA_DIR . '/locks');
define('SESSION_DIR', DATA_DIR . '/sessions');
define('LOG_DIR', DATA_DIR . '/logs');
define('UPLOAD_DIR', ROOT_DIR . '/uploads');
