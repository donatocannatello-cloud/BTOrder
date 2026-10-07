<?php
declare(strict_types=1);
defined('APP') || exit;

/*
 * Modello dati di un condominio (data/condominio_<id>.json):
 *
 * {
 *   id, nome, indirizzo, codice_fiscale, ruolo ('amministratore'|'proprietario'),
 *   saldo_iniziale (centesimi), data_saldo_iniziale (aaaa-mm-gg), note,
 *   tabelle: [ {id, nome} ],                       tabelle millesimali
 *   unita:   [ {id, interno, scala, piano, descrizione,
 *               proprietario: {nome, email, telefono},
 *               inquilino:    {nome, email, telefono},
 *               millesimi: { <tabella_id>: float }, note } ],
 *   uscite: [...], rate: [...],                     (fase 3)
 *   created_at, updated_at
 * }
 */

const RUOLI = [
    'amministratore' => 'Amministratore',
    'proprietario' => 'Proprietario',
];

const TABELLE_PREDEFINITE = ['Generale', 'Scale', 'Riscaldamento'];

function is_id($id): bool
{
    return is_string($id) && preg_match('/^[a-f0-9]{16}$/', $id) === 1;
}

function condominio_doc(string $id): string
{
    return 'condominio_' . $id;
}

/** Completa un condominio letto da file con i campi mancanti. */
function condominio_normalize(array $c): array
{
    $c += [
        'nome' => '', 'indirizzo' => '', 'codice_fiscale' => '', 'ruolo' => 'amministratore',
        'saldo_iniziale' => 0, 'data_saldo_iniziale' => '', 'note' => '',
        'tabelle' => [], 'unita' => [], 'uscite' => [], 'rate' => [],
        'created_at' => 0, 'updated_at' => 0,
    ];
    foreach ($c['unita'] as &$u) {
        $u += ['interno' => '', 'scala' => '', 'piano' => '', 'descrizione' => '', 'note' => '', 'millesimi' => []];
        foreach (['proprietario', 'inquilino'] as $k) {
            $u[$k] = (is_array($u[$k] ?? null) ? $u[$k] : []) + ['nome' => '', 'email' => '', 'telefono' => ''];
        }
    }
    unset($u);
    return $c;
}

/** @return array<int, array> condomini ordinati per nome */
function condomini_all(): array
{
    $list = [];
    foreach (Store::listNames('condominio_') as $name) {
        $c = Store::read($name);
        if ($c !== null && is_id($c['id'] ?? null)) {
            $list[] = condominio_normalize($c);
        }
    }
    usort($list, function ($a, $b) {
        return strnatcasecmp($a['nome'], $b['nome']);
    });
    return $list;
}

function condominio_get(string $id): ?array
{
    if (!is_id($id)) {
        return null;
    }
    $c = Store::read(condominio_doc($id));
    return $c === null ? null : condominio_normalize($c);
}

/** Come condominio_get(), ma se non esiste torna all'elenco con un messaggio. */
function condominio_or_redirect(string $id): array
{
    $c = condominio_get($id);
    if ($c === null) {
        flash('error', 'Condominio non trovato.');
        redirect('condomini');
    }
    return $c;
}

function condominio_create(array $fields): string
{
    $id = Store::newId();
    $now = time();
    $tabelle = [];
    foreach (TABELLE_PREDEFINITE as $nome) {
        $tabelle[] = ['id' => Store::newId(), 'nome' => $nome];
    }
    $c = condominio_normalize(['id' => $id, 'tabelle' => $tabelle, 'created_at' => $now, 'updated_at' => $now] + $fields);
    Store::write(condominio_doc($id), $c);
    return $id;
}

/** Aggiornamento sotto lock: $fn riceve il condominio e restituisce quello modificato. */
function condominio_update(string $id, callable $fn): array
{
    if (!is_id($id)) {
        throw new InvalidArgumentException('Id condominio non valido');
    }
    $doc = condominio_doc($id);
    if (!Store::exists($doc)) {
        throw new RuntimeException('Il condominio non esiste più.');
    }
    return Store::update($doc, function (array $c) use ($fn) {
        $c = $fn(condominio_normalize($c));
        $c['updated_at'] = time();
        return $c;
    });
}

function condominio_delete(string $id): void
{
    if (is_id($id)) {
        Store::delete(condominio_doc($id));
    }
}

/**
 * Valida i campi anagrafici inviati da un form.
 * @return array{0: array, 1: string[]} [campi puliti, errori]
 */
function condominio_validate(array $in): array
{
    $errors = [];
    $f = [
        'nome' => trim((string) ($in['nome'] ?? '')),
        'indirizzo' => trim((string) ($in['indirizzo'] ?? '')),
        'codice_fiscale' => strtoupper(str_replace(' ', '', (string) ($in['codice_fiscale'] ?? ''))),
        'ruolo' => (string) ($in['ruolo'] ?? ''),
        'note' => trim((string) ($in['note'] ?? '')),
    ];
    if ($f['nome'] === '') {
        $errors[] = 'Il nome è obbligatorio.';
    }
    if ($f['codice_fiscale'] !== '' && !preg_match('/^(\d{11}|[A-Z0-9]{16})$/', $f['codice_fiscale'])) {
        $errors[] = 'Codice fiscale non valido: 11 cifre (condominio) oppure 16 caratteri.';
    }
    if (!isset(RUOLI[$f['ruolo']])) {
        $errors[] = 'Scegli il tuo ruolo nel condominio.';
    }

    $saldo = trim((string) ($in['saldo_iniziale'] ?? ''));
    $f['saldo_iniziale'] = $saldo === '' ? 0 : parse_money($saldo);
    if ($f['saldo_iniziale'] === null) {
        $errors[] = 'Saldo iniziale non valido (es. 1.234,56).';
    }
    $data = trim((string) ($in['data_saldo_iniziale'] ?? ''));
    $f['data_saldo_iniziale'] = $data === '' ? '' : parse_date($data);
    if ($f['data_saldo_iniziale'] === null) {
        $errors[] = 'Data del saldo iniziale non valida (gg/mm/aaaa).';
    }
    return [$f, $errors];
}

// ----------------------------------------------------------------------
// Unità

function unita_sorted(array $c): array
{
    $u = $c['unita'];
    usort($u, function ($a, $b) {
        return strnatcasecmp($a['scala'], $b['scala']) ?: strnatcasecmp($a['interno'], $b['interno']);
    });
    return $u;
}

function unita_find(array $c, string $uid): ?array
{
    foreach ($c['unita'] as $u) {
        if ($u['id'] === $uid) {
            return $u;
        }
    }
    return null;
}

/** Etichetta breve: "Scala A - Int. 3" */
function unita_label(array $u): string
{
    $s = 'Int. ' . $u['interno'];
    return $u['scala'] !== '' ? 'Sc. ' . $u['scala'] . ' - ' . $s : $s;
}

/** Chi risulta a pagare/abitare: inquilino se presente, altrimenti proprietario. */
function unita_occupante(array $u): string
{
    return $u['inquilino']['nome'] !== '' ? $u['inquilino']['nome'] : $u['proprietario']['nome'];
}

/** Motivo per cui l'unità non si può eliminare, o null. */
function unita_in_uso(array $c, string $uid): ?string
{
    foreach ($c['rate'] as $r) {
        if (($r['unita_id'] ?? '') === $uid) {
            return "ci sono rate registrate per questa unità";
        }
    }
    return null;
}

/**
 * @return array{0: array, 1: string[]} [unità pulita (senza id), errori]
 */
function unita_validate(array $in, array $c, ?string $selfId): array
{
    $errors = [];
    $person = function ($p): array {
        $p = is_array($p) ? $p : [];
        return [
            'nome' => trim((string) ($p['nome'] ?? '')),
            'email' => trim((string) ($p['email'] ?? '')),
            'telefono' => trim((string) ($p['telefono'] ?? '')),
        ];
    };
    $u = [
        'interno' => trim((string) ($in['interno'] ?? '')),
        'scala' => trim((string) ($in['scala'] ?? '')),
        'piano' => trim((string) ($in['piano'] ?? '')),
        'descrizione' => trim((string) ($in['descrizione'] ?? '')),
        'proprietario' => $person($in['proprietario'] ?? []),
        'inquilino' => $person($in['inquilino'] ?? []),
        'note' => trim((string) ($in['note'] ?? '')),
        'millesimi' => [],
    ];
    if ($u['interno'] === '') {
        $errors[] = "L'interno è obbligatorio.";
    }
    if ($u['proprietario']['nome'] === '') {
        $errors[] = 'Il nome del proprietario è obbligatorio.';
    }
    foreach (['proprietario' => 'del proprietario', 'inquilino' => "dell'inquilino"] as $k => $chi) {
        if ($u[$k]['email'] !== '' && !filter_var($u[$k]['email'], FILTER_VALIDATE_EMAIL)) {
            $errors[] = "Email $chi non valida.";
        }
    }
    foreach ($c['unita'] as $other) {
        if ($other['id'] !== $selfId
            && strcasecmp($other['interno'], $u['interno']) === 0
            && strcasecmp($other['scala'], $u['scala']) === 0) {
            $errors[] = 'Esiste già un\'unità con lo stesso interno' . ($u['scala'] !== '' ? ' nella stessa scala' : '') . '.';
            break;
        }
    }

    $mill = is_array($in['millesimi'] ?? null) ? $in['millesimi'] : [];
    foreach ($c['tabelle'] as $t) {
        $raw = trim((string) ($mill[$t['id']] ?? ''));
        $v = $raw === '' ? 0.0 : parse_decimal($raw, 4);
        if ($v === null || $v < 0) {
            $errors[] = 'Millesimi non validi per la tabella "' . $t['nome'] . '".';
            continue;
        }
        if ($v > 0) {
            $u['millesimi'][$t['id']] = $v;
        }
    }
    return [$u, $errors];
}

// ----------------------------------------------------------------------
// Tabelle millesimali

function tabella_find(array $c, string $tid): ?array
{
    foreach ($c['tabelle'] as $t) {
        if ($t['id'] === $tid) {
            return $t;
        }
    }
    return null;
}

/** Somma dei millesimi di una tabella su tutte le unità. */
function tabella_totale(array $c, string $tid): float
{
    $tot = 0.0;
    foreach ($c['unita'] as $u) {
        $tot += (float) ($u['millesimi'][$tid] ?? 0);
    }
    return round($tot, 4);
}

/** Motivo per cui la tabella non si può eliminare, o null. */
function tabella_in_uso(array $c, string $tid): ?string
{
    foreach ($c['uscite'] as $s) {
        if (($s['tabella_id'] ?? '') === $tid) {
            return 'è usata da almeno una spesa';
        }
    }
    return null;
}

function millesimi_fmt(float $v): string
{
    $s = number_format($v, 4, ',', '.');
    return rtrim(rtrim($s, '0'), ',');
}

/** Valore per i campi dei form (vuoto se zero). */
function millesimi_input($v): string
{
    $v = (float) $v;
    return $v == 0.0 ? '' : str_replace('.', ',', rtrim(rtrim(number_format($v, 4, '.', ''), '0'), '.'));
}

// ----------------------------------------------------------------------
// Interfaccia comune alle pagine di un condominio

/** Titolo del condominio con le schede di navigazione. */
function condominio_header(array $c, string $active): string
{
    $tabs = [
        'condominio' => 'Unità',
        'millesimi' => 'Tabelle millesimali',
        'condominio_form' => 'Anagrafica',
    ];
    $html = '<div class="page-head"><div>'
        . '<p class="breadcrumb"><a href="' . e(url('condomini')) . '">Condomini</a> ›</p>'
        . '<h1>' . e($c['nome']) . '</h1>'
        . '<p class="muted">' . e($c['indirizzo'])
        . ($c['codice_fiscale'] !== '' ? ' · C.F. ' . e($c['codice_fiscale']) : '')
        . ' · <span class="badge badge-info">' . e(RUOLI[$c['ruolo']] ?? $c['ruolo']) . '</span></p>'
        . '</div></div><nav class="tabs">';
    foreach ($tabs as $p => $label) {
        $html .= '<a href="' . e(url($p, ['id' => $c['id']])) . '"' . ($p === $active ? ' class="active"' : '') . '>' . e($label) . '</a>';
    }
    return $html . '</nav>';
}
