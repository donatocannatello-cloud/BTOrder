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

// Modulo spesa: scegliendo la tipologia si propongono tabella e % inquilino.
(function () {
    var cat = document.querySelector('select[data-categoria]');
    if (!cat) {
        return;
    }
    var tab = document.querySelector('select[data-tabella-select]');
    var quota = document.querySelector('input[data-quota-input]');
    cat.addEventListener('change', function () {
        var opt = cat.options[cat.selectedIndex];
        if (opt && opt.getAttribute('data-tabella')) {
            tab.value = opt.getAttribute('data-tabella');
            quota.value = opt.getAttribute('data-quota');
        }
    });
})();

// Select che aggiornano subito la pagina (es. tipo di report).
document.addEventListener('change', function (ev) {
    if (ev.target.matches('select[data-autosubmit]')) {
        ev.target.form.submit();
    }
});
