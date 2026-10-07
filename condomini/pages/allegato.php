<?php
declare(strict_types=1);
defined('APP') || exit;

// Serve un allegato di una spesa. La pagina è raggiungibile solo dopo il login
// (controllo in index.php); il file deve risultare registrato nella spesa indicata.
$c = condominio_get(query('id'));
$s = $c ? uscita_find($c, query('s')) : null;
$a = null;
foreach ($s['allegati'] ?? [] as $x) {
    if ($x['id'] === query('a')) {
        $a = $x;
    }
}
$path = $a && $c ? allegato_path($c['id'], $a) : null;
if ($path === null || !isset(ALLEGATI_MIME[$a['mime']])) {
    http_response_code(404);
    $errorTitle = 'Allegato non trovato';
    $errorMessage = 'Il file richiesto non esiste o è stato eliminato.';
    require PAGES_DIR . '/errore.php';
    return;
}

while (ob_get_level() > 0) {
    ob_end_clean();
}
$download = query('download') === '1';
$fallback = preg_replace('/[^A-Za-z0-9._-]+/', '_', $a['nome']);
header('Content-Type: ' . $a['mime']);
header('Content-Length: ' . filesize($path));
header('Content-Disposition: ' . ($download ? 'attachment' : 'inline')
    . '; filename="' . $fallback . '"; filename*=UTF-8\'\'' . rawurlencode($a['nome']));
// Le immagini sono servite in sandbox (nessuno script). Per i PDF niente sandbox:
// il visualizzatore PDF di Chrome si rifiuta di funzionare con quella direttiva.
if ($a['mime'] === 'application/pdf') {
    header_remove('Content-Security-Policy');
} else {
    header("Content-Security-Policy: default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
}
header('Cache-Control: private, max-age=0, no-store');
readfile($path);
exit;
