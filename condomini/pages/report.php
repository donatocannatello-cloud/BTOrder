<?php
declare(strict_types=1);
defined('APP') || exit;

// Report: anteprima a video, versione stampabile (Salva come PDF) ed export CSV.
$c = condominio_or_redirect(query('id'));
$tipo = isset(REPORT_TIPI[query('tipo')]) ? query('tipo') : 'rendiconto';
$anno = preg_match('/^\d{4}$/', query('anno')) ? query('anno') : (isset($_GET['anno']) ? '' : date('Y'));
$scelte = estratto_scelte($c);
$chi = query('chi');
if ($tipo === 'estratto' && $chi !== 'tutti' && !isset($scelte[$chi])) {
    $chi = (string) array_key_first($scelte);
}
$formato = query('formato');
$sezioni = report_genera($c, $tipo, $anno, $chi);
$titolo = REPORT_TIPI[$tipo] . ($tipo === 'situazione' ? '' : ' - ' . periodo_label($anno));
$params = array_filter(['id' => $c['id'], 'tipo' => $tipo, 'anno' => $anno, 'chi' => $tipo === 'estratto' ? $chi : '']);
if ($anno === '' && $tipo !== 'situazione') {
    $params['anno'] = 'tutti';
}

if ($formato === 'csv') {
    while (ob_get_level() > 0) {
        ob_end_clean();
    }
    $file = preg_replace('/[^a-z0-9]+/', '_', strtolower($c['nome'] . '_' . $tipo . '_' . ($anno ?: 'tutti')));
    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename="' . trim($file, '_') . '.csv"');
    echo report_csv($c, $titolo, $sezioni);
    exit;
}

if ($formato === 'stampa') {
    $layout = 'print';
    $title = $titolo . ' · ' . $c['nome'];
    ?>
    <div class="print-bar no-print">
        <button type="button" class="btn btn-primary" data-print>Stampa / Salva come PDF</button>
        <a class="btn" href="<?= e(url('report', $params)) ?>">‹ Torna al report</a>
        <span class="muted">Nella finestra di stampa scegli "Salva come PDF" come stampante.</span>
    </div>
    <header class="print-head">
        <h1><?= e($c['nome']) ?></h1>
        <p><?= e($c['indirizzo']) ?><?= $c['codice_fiscale'] !== '' ? ' · C.F. ' . e($c['codice_fiscale']) : '' ?></p>
        <p><strong><?= e($titolo) ?></strong> · stampato il <?= e(date('d/m/Y')) ?></p>
    </header>
    <?= report_html($sezioni) ?>
    <?php
    return;
}

$anni = anni_movimenti($c);
$title = 'Report · ' . $c['nome'];
?>
<?= condominio_header($c, 'report') ?>

<form method="get" action="index.php" class="card filters report-filters">
    <input type="hidden" name="p" value="report">
    <input type="hidden" name="id" value="<?= e($c['id']) ?>">
    <label>Report
        <select name="tipo" data-autosubmit>
            <?php foreach (REPORT_TIPI as $k => $l): ?>
                <option value="<?= e($k) ?>"<?= selected($tipo === $k) ?>><?= e($l) ?></option>
            <?php endforeach; ?>
        </select>
    </label>
    <?php if ($tipo !== 'situazione'): ?>
        <label>Periodo
            <select name="anno">
                <?php foreach ($anni as $a): ?>
                    <option value="<?= e($a) ?>"<?= selected($anno === $a) ?>>Anno <?= e($a) ?></option>
                <?php endforeach; ?>
                <option value="tutti"<?= selected($anno === '') ?>>Tutti gli anni</option>
            </select>
        </label>
    <?php endif; ?>
    <?php if ($tipo === 'estratto'): ?>
        <label>Condòmino
            <select name="chi">
                <option value="tutti"<?= selected($chi === 'tutti') ?>>Tutti (un estratto per pagina)</option>
                <?php foreach ($scelte as $k => $l): ?>
                    <option value="<?= e($k) ?>"<?= selected($chi === $k) ?>><?= e($l) ?></option>
                <?php endforeach; ?>
            </select>
        </label>
    <?php endif; ?>
    <button type="submit" class="btn btn-small">Mostra</button>
    <span class="spacer"></span>
    <a class="btn btn-small" href="<?= e(url('report', $params + ['formato' => 'csv'])) ?>">⬇ Esporta CSV</a>
    <a class="btn btn-small btn-primary" href="<?= e(url('report', $params + ['formato' => 'stampa'])) ?>" target="_blank" rel="noopener">🖨 Versione stampabile / PDF</a>
</form>

<?php if (!$sezioni): ?>
    <div class="card empty"><p>Nessun dato per questo report.</p></div>
<?php else: ?>
    <?= report_html($sezioni) ?>
<?php endif; ?>
