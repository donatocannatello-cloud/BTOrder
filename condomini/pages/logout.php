<?php
declare(strict_types=1);
defined('APP') || exit;

if (is_post()) {
    auth_logout();
    flash('info', 'Sei uscito.');
    redirect('login');
}
redirect('dashboard');
