<?php
declare(strict_types=1);
defined('APP') || exit;

// "Esporta tutto": ZIP con i file JSON di data/ e tutti gli allegati di uploads/.
if (!is_post()) {
    redirect('impostazioni');
}
if (!class_exists('ZipArchive')) {
    throw new RuntimeException("L'estensione PHP zip non è attiva: abilitala dal pannello di controllo Aruba.");
}

$withBackups = !empty($_POST['backup']);
$tmp = DATA_DIR . '/.export_' . bin2hex(random_bytes(6)) . '.zip';
register_shutdown_function(function () use ($tmp) {
    if (is_file($tmp)) {
        @unlink($tmp);
    }
});

$zip = new ZipArchive();
if ($zip->open($tmp, ZipArchive::CREATE | ZipArchive::EXCL) !== true) {
    throw new RuntimeException('Impossibile creare il file ZIP.');
}

// Dati: si legge ogni documento sotto lock per avere una copia coerente.
foreach (Store::listNames('') as $name) {
    if ($name === 'login_attempts') {
        continue;
    }
    $data = Store::read($name);
    if ($data !== null) {
        $zip->addFromString('data/' . $name . '.json', json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    }
}

if ($withBackups) {
    foreach (glob(BACKUP_DIR . '/*.json') ?: [] as $file) {
        $zip->addFile($file, 'data/backup/' . basename($file));
    }
}

// Allegati
$uploadsReal = realpath(UPLOAD_DIR);
if ($uploadsReal !== false) {
    $it = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($uploadsReal, FilesystemIterator::SKIP_DOTS)
    );
    foreach ($it as $file) {
        /** @var SplFileInfo $file */
        if (!$file->isFile() || $file->getFilename() === '.htaccess') {
            continue;
        }
        $rel = str_replace('\\', '/', substr($file->getPathname(), strlen($uploadsReal) + 1));
        $zip->addFile($file->getPathname(), 'uploads/' . $rel);
    }
}

$zip->setArchiveComment(APP_NAME . ' - esportazione del ' . date('d/m/Y H:i'));
if (!$zip->close()) {
    throw new RuntimeException('Errore durante la creazione dello ZIP.');
}

while (ob_get_level() > 0) {
    ob_end_clean();
}
header('Content-Type: application/zip');
header('Content-Disposition: attachment; filename="condomini_' . date('Ymd_His') . '.zip"');
header('Content-Length: ' . filesize($tmp));
readfile($tmp);
exit;
