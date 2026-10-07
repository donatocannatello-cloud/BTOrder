// Piccoli miglioramenti progressivi: l'app funziona anche senza JavaScript.
document.addEventListener('submit', function (ev) {
    var form = ev.target;
    var msg = form.getAttribute('data-confirm');
    if (msg && !window.confirm(msg)) {
        ev.preventDefault();
    }
});

document.addEventListener('click', function (ev) {
    var el = ev.target.closest('[data-print]');
    if (el) {
        ev.preventDefault();
        window.print();
    }
});
