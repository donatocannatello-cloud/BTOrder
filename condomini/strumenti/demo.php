<?php
declare(strict_types=1);

/*
 * Crea dati dimostrativi per provare l'app in locale (NON usarlo sul server di produzione).
 *
 *   php strumenti/demo.php            crea i dati se l'archivio è vuoto
 *   php strumenti/demo.php --force    sostituisce i condomini esistenti con quelli di prova
 *
 * Imposta anche la password di accesso "demo-condomini" se non ne esiste una.
 * Tutti i nomi sono inventati.
 */

if (PHP_SAPI !== 'cli') {
    http_response_code(403);
    exit('Solo da riga di comando.');
}

define('APP', true);
$root = dirname(__DIR__);
foreach (['config', 'helpers', 'storage', 'auth', 'condomini', 'contabilita'] as $f) {
    require "$root/includes/$f.php";
}
date_default_timezone_set('Europe/Rome');
Store::ensureDirs();

$force = in_array('--force', $argv, true);
if (Store::listNames('condominio_') && !$force) {
    fwrite(STDERR, "Ci sono già condomini in archivio: usa --force per sostituirli con i dati di prova.\n");
    exit(1);
}
foreach (Store::listNames('condominio_') as $name) {
    Store::delete($name);
}
if (!config_has_password()) {
    config_update(['password_hash' => password_hash('demo-condomini', PASSWORD_DEFAULT), 'password_changed_at' => time()]);
    echo "Password di accesso: demo-condomini\n";
}

$anno = (int) date('Y');
$d = function (int $m, int $g, int $deltaAnno = 0) use ($anno): string {
    return sprintf('%04d-%02d-%02d', $anno + $deltaAnno, $m, $g);
};
$persona = function (string $nome, string $tel = '', string $email = ''): array {
    return ['nome' => $nome, 'telefono' => $tel, 'email' => $email];
};

/** Crea un condominio completo; $spese: [data, tipologia, fornitore, descrizione, importo €, pagata il|''] */
$crea = function (array $anag, array $unita, array $spese, array $versamenti, array $entrate) {
    $id = condominio_create($anag);
    condominio_update($id, function (array $c) use ($unita, $spese, $versamenti, $entrate) {
        $tab = array_column($c['tabelle'], 'id', 'nome');
        foreach ($unita as $u) {
            $mill = [];
            foreach ($u['millesimi'] as $nomeTab => $v) {
                $mill[$tab[$nomeTab]] = $v;
            }
            $c['unita'][] = [
                'id' => Store::newId(), 'interno' => $u['interno'], 'scala' => $u['scala'], 'piano' => $u['piano'],
                'descrizione' => $u['descrizione'] ?? 'Appartamento', 'note' => '',
                'proprietario' => $u['proprietario'], 'inquilino' => $u['inquilino'] ?? ['nome' => '', 'telefono' => '', 'email' => ''],
                'millesimi' => $mill,
            ];
        }
        $c = condominio_normalize($c);
        $cat = array_column($c['categorie'], null, 'nome');
        foreach ($spese as [$data, $tipologia, $fornitore, $descr, $euro, $pagata]) {
            $k = $cat[$tipologia];
            $importo = (int) round($euro * 100);
            $c['uscite'][] = [
                'id' => Store::newId(), 'data' => $data, 'categoria_id' => $k['id'], 'tipo' => $k['tipo'],
                'fornitore' => $fornitore, 'descrizione' => $descr, 'importo' => $importo,
                'tabella_id' => $k['tabella_id'], 'quota_inquilino' => $k['quota_inquilino'], 'scadenza' => '',
                'pagata' => $pagata !== '', 'data_pagamento' => $pagata, 'allegati' => [],
                'riparto' => riparto_calcola($c, $k['tabella_id'], $importo, $k['quota_inquilino']),
            ];
        }
        // Versamenti: [interno, soggetto, data, rif trimestre, metodo, quota (1 = tutta la rata, 0.5 = metà)]
        $sit = situazione($c);
        foreach ($versamenti as [$interno, $sog, $data, $rif, $metodo, $quota]) {
            foreach ($c['unita'] as $u) {
                if ($u['interno'] !== $interno) {
                    continue;
                }
                $dovuto = 0;
                foreach ($sit[$u['id'] . '|' . $sog]['addebiti'] ?? [] as $a) {
                    $dovuto += $a['trimestre'] === $rif ? $a['importo'] : 0;
                }
                if ($dovuto > 0) {
                    $c['versamenti'][] = ['id' => Store::newId(), 'unita_id' => $u['id'], 'soggetto' => $sog, 'data' => $data,
                        'importo' => (int) round($dovuto * $quota), 'metodo' => $metodo, 'note' => '', 'rif' => $rif];
                }
            }
        }
        foreach ($entrate as $e) {
            $c['entrate'][] = ['id' => Store::newId(), 'unita_id' => '', 'note' => '', 'data_fine' => ''] + $e;
        }
        return $c;
    });
    return $id;
};

$T = function (int $q) use ($anno): string {
    return $anno . '-T' . $q;
};

// ---------------------------------------------------------------- Residenza I Glicini
$crea(
    ['nome' => 'Residenza I Glicini', 'indirizzo' => 'Via delle Magnolie 14, Bari', 'codice_fiscale' => '93012340721',
        'ruolo' => 'amministratore', 'saldo_iniziale' => 485000, 'data_saldo_iniziale' => $d(1, 1), 'note' => 'Dati dimostrativi.'],
    [
        ['interno' => '1', 'scala' => 'A', 'piano' => 'T', 'proprietario' => $persona('Marco Lionetti', '333 410 2211', 'm.lionetti@example.it'),
            'inquilino' => $persona('Giulia Ferrante', '340 118 7720'), 'millesimi' => ['Generale' => 182.5, 'Scale' => 160, 'Riscaldamento' => 175]],
        ['interno' => '2', 'scala' => 'A', 'piano' => '1', 'proprietario' => $persona('Rosa Carbonara', '328 555 0193'),
            'millesimi' => ['Generale' => 167.25, 'Scale' => 180, 'Riscaldamento' => 170]],
        ['interno' => '3', 'scala' => 'A', 'piano' => '2', 'proprietario' => $persona('Vito Dell\'Aquila', '347 222 9031', 'vito.da@example.it'),
            'millesimi' => ['Generale' => 171.5, 'Scale' => 200, 'Riscaldamento' => 170]],
        ['interno' => '4', 'scala' => 'B', 'piano' => 'T', 'proprietario' => $persona('Immobiliare Murgia srl', '080 552 1100'),
            'inquilino' => $persona('Studio Dentistico Palmieri', '080 552 9988'), 'descrizione' => 'Negozio',
            'millesimi' => ['Generale' => 210.75, 'Scale' => 140, 'Riscaldamento' => 165]],
        ['interno' => '5', 'scala' => 'B', 'piano' => '1', 'proprietario' => $persona('Annalisa Tarantino', '366 410 0072'),
            'millesimi' => ['Generale' => 152, 'Scale' => 170, 'Riscaldamento' => 160]],
        ['interno' => '6', 'scala' => 'B', 'piano' => '2', 'proprietario' => $persona('Pasquale Mininni', '339 870 4410'),
            'inquilino' => $persona('Elena Ruggiero', '320 664 1834', 'elena.r@example.it'), 'millesimi' => ['Generale' => 116, 'Scale' => 150, 'Riscaldamento' => 160]],
    ],
    [
        [$d(1, 31), 'Pulizia scale e parti comuni', 'Splendor Servizi', 'Pulizie gennaio-marzo', 690.00, $d(2, 5)],
        [$d(2, 12), 'Energia elettrica parti comuni', 'Enel Energia', 'Bolletta bimestrale', 214.38, $d(2, 28)],
        [$d(3, 3), 'Assicurazione fabbricato', 'Generali Bari Centro', 'Polizza globale fabbricati', 1240.00, $d(3, 10)],
        [$d(3, 20), 'Riscaldamento: combustibile e conduzione', 'Termoservizi Puglia', 'Gasolio e conduzione caldaia', 3820.00, $d(4, 2)],
        [$d(4, 30), 'Pulizia scale e parti comuni', 'Splendor Servizi', 'Pulizie aprile-giugno', 690.00, $d(5, 6)],
        [$d(5, 15), 'Ascensore: consumi e manutenzione ordinaria', 'Kone Italia', 'Canone manutenzione semestrale', 960.00, $d(5, 30)],
        [$d(6, 10), 'Compenso amministratore', 'Studio Amministrazioni', 'Primo semestre', 1500.00, $d(6, 20)],
        [$d(7, 8), 'Lavori straordinari', 'Edil Levante sas', 'Rifacimento impermeabilizzazione terrazzo - acconto', 6000.00, $d(7, 15)],
        [$d(7, 31), 'Pulizia scale e parti comuni', 'Splendor Servizi', 'Pulizie luglio-settembre', 690.00, $d(8, 4)],
        [$d(8, 18), 'Energia elettrica parti comuni', 'Enel Energia', 'Bolletta bimestrale', 198.12, $d(9, 1)],
        [$d(9, 25), 'Giardino e verde', 'Verde Murgia', 'Potatura e manutenzione aiuole', 420.00, ''],
        [$d(10, 2), 'Lavori straordinari', 'Edil Levante sas', 'Impermeabilizzazione terrazzo - saldo', 5400.00, ''],
        [$d(10, 4), 'Spese bancarie e postali', 'Banca Popolare di Bari', 'Canone conto trimestrale', 36.50, $d(10, 4)],
    ],
    [
        ['1', 'inquilino', $d(3, 25), $T(1), 'bonifico', 1], ['1', 'proprietario', $d(3, 28), $T(1), 'bonifico', 1],
        ['2', 'proprietario', $d(3, 30), $T(1), 'bonifico', 1], ['3', 'proprietario', $d(4, 18), $T(1), 'contanti', 1],
        ['4', 'proprietario', $d(3, 15), $T(1), 'bonifico', 1], ['4', 'inquilino', $d(3, 15), $T(1), 'bonifico', 1],
        ['5', 'proprietario', $d(3, 31), $T(1), 'bonifico', 0.5], ['6', 'proprietario', $d(3, 29), $T(1), 'bonifico', 1],
        ['6', 'inquilino', $d(3, 29), $T(1), 'rid', 1],
        ['1', 'inquilino', $d(6, 26), $T(2), 'bonifico', 1], ['1', 'proprietario', $d(6, 27), $T(2), 'bonifico', 1],
        ['2', 'proprietario', $d(6, 30), $T(2), 'assegno', 1], ['4', 'proprietario', $d(6, 12), $T(2), 'bonifico', 1],
        ['4', 'inquilino', $d(6, 12), $T(2), 'bonifico', 1], ['6', 'inquilino', $d(6, 29), $T(2), 'rid', 1],
        ['6', 'proprietario', $d(7, 20), $T(2), 'bonifico', 1],
        ['2', 'proprietario', $d(9, 28), $T(3), 'bonifico', 1], ['4', 'proprietario', $d(9, 20), $T(3), 'bonifico', 1],
        ['4', 'inquilino', $d(9, 20), $T(3), 'bonifico', 1], ['6', 'inquilino', $d(9, 30), $T(3), 'rid', 1],
    ],
    [
        ['descrizione' => 'Affitto locale ex portineria', 'debitore' => 'Caffè dei Glicini snc', 'importo' => 45000, 'frequenza' => 'mensile',
            'data_inizio' => $d(1, 5), 'in_cassa' => true,
            'pagamenti' => array_fill_keys(array_map(fn($m) => sprintf('%04d-%02d-05', $anno, $m), range(1, 8)), null)],
        ['descrizione' => 'Canone antenna telefonica sul lastrico', 'debitore' => 'TelcoTower Italia spa', 'importo' => 360000, 'frequenza' => 'annuale',
            'data_inizio' => $d(4, 1, -1), 'in_cassa' => true, 'pagamenti' => [$d(4, 1, -1) => null, $d(4, 1) => null]],
    ]
);

// ---------------------------------------------------------------- Condominio Via Garibaldi 37
$crea(
    ['nome' => 'Condominio Via Garibaldi 37', 'indirizzo' => 'Via Garibaldi 37, Monopoli', 'codice_fiscale' => '93055510726',
        'ruolo' => 'proprietario', 'saldo_iniziale' => 120000, 'data_saldo_iniziale' => $d(1, 1), 'note' => 'Dati dimostrativi.'],
    [
        ['interno' => '1', 'scala' => '', 'piano' => '1', 'proprietario' => $persona('Donato (io)'), 'inquilino' => $persona('Luca Semeraro', '348 330 1945'),
            'millesimi' => ['Generale' => 340, 'Scale' => 330, 'Riscaldamento' => 0]],
        ['interno' => '2', 'scala' => '', 'piano' => '2', 'proprietario' => $persona('Teresa Pugliese', '329 774 0021'),
            'millesimi' => ['Generale' => 330, 'Scale' => 335]],
        ['interno' => '3', 'scala' => '', 'piano' => '3', 'proprietario' => $persona('Nicola Abbracciavento', '335 610 9087'),
            'millesimi' => ['Generale' => 330, 'Scale' => 335]],
    ],
    [
        [$d(2, 20), 'Energia elettrica parti comuni', 'Edison Energia', 'Luce scale', 96.40, $d(3, 1)],
        [$d(5, 9), 'Pulizia scale e parti comuni', 'Pulito Facile', 'Pulizie primo semestre', 540.00, $d(5, 20)],
        [$d(8, 28), 'Piccola manutenzione parti comuni', 'Elettricista Lorusso', 'Sostituzione plafoniere', 185.00, ''],
    ],
    [
        ['1', 'inquilino', $d(3, 30), $T(1), 'bonifico', 1], ['1', 'proprietario', $d(3, 30), $T(1), 'bonifico', 1],
        ['2', 'proprietario', $d(3, 25), $T(1), 'bonifico', 1], ['3', 'proprietario', $d(3, 27), $T(1), 'contanti', 1],
        ['1', 'inquilino', $d(6, 28), $T(2), 'bonifico', 1], ['2', 'proprietario', $d(6, 29), $T(2), 'bonifico', 1],
    ],
    [
        ['descrizione' => 'Affitto appartamento int. 1', 'debitore' => 'Luca Semeraro', 'importo' => 65000, 'frequenza' => 'mensile',
            'data_inizio' => $d(1, 1), 'in_cassa' => false,
            'pagamenti' => array_fill_keys(array_map(fn($m) => sprintf('%04d-%02d-01', $anno, $m), range(1, 9)), null)],
    ]
);

// Converte i "pagamenti" segnaposto in incassi veri (data = scadenza + 2 giorni).
foreach (Store::listNames('condominio_') as $name) {
    Store::update($name, function (array $c) {
        foreach ($c['entrate'] as &$e) {
            foreach ($e['pagamenti'] as $data => $p) {
                if ($p === null) {
                    $e['pagamenti'][$data] = ['data' => date('Y-m-d', strtotime($data . ' +2 days')), 'importo' => $e['importo'], 'metodo' => 'bonifico', 'note' => ''];
                }
            }
        }
        unset($e);
        return $c;
    });
}

echo "Dati dimostrativi creati: " . count(Store::listNames('condominio_')) . " condomini.\n";
