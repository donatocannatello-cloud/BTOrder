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

// Griglia millesimi: totali per colonna aggiornati mentre si digita.
(function () {
    var inputs = document.querySelectorAll('input[data-col]');
    if (!inputs.length) {
        return;
    }
    function parse(v) {
        v = v.replace(/\s/g, '');
        if (v.indexOf(',') !== -1) {
            v = v.replace(/\./g, '').replace(',', '.');
        }
        var n = parseFloat(v);
        return isNaN(n) ? 0 : n;
    }
    function recalc(col) {
        var tot = 0;
        document.querySelectorAll('input[data-col="' + col + '"]').forEach(function (i) {
            tot += parse(i.value);
        });
        var cell = document.querySelector('[data-total="' + col + '"]');
        if (cell) {
            tot = Math.round(tot * 10000) / 10000;
            cell.textContent = tot.toLocaleString('it-IT', { maximumFractionDigits: 4 });
            cell.classList.toggle('pos', Math.abs(tot - 1000) < 0.001);
            cell.classList.toggle('warn', Math.abs(tot - 1000) >= 0.001);
        }
    }
    inputs.forEach(function (i) {
        i.addEventListener('input', function () {
            recalc(i.getAttribute('data-col'));
        });
    });
})();
