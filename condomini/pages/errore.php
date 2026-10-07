<?php
declare(strict_types=1);
defined('APP') || exit;

/** @var string $errorTitle */
/** @var string $errorMessage */

$title = $errorTitle;
?>
<div class="card">
    <h1><?= e($errorTitle) ?></h1>
    <p><?= e($errorMessage) ?></p>
    <p><a class="btn" href="<?= e(url('dashboard')) ?>">Torna alla dashboard</a></p>
</div>
