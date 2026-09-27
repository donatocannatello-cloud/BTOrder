#!/usr/bin/env python3
"""Controlla che le librerie native (.so) di un APK/AAB siano allineate a pagine da 16 KB.

Google Play lo richiede per le app che puntano ad Android 15 o successivi: ogni segmento
PT_LOAD delle librerie ELF a 64 bit deve avere un allineamento di almeno 16384 byte.
Stampa l'esito per ogni libreria; esce con codice 1 se qualcuna non è conforme.
"""
import struct
import sys
import zipfile

PT_LOAD = 1


def allineamento_minimo(dati: bytes):
    if dati[:4] != b"\x7fELF":
        return None
    classe64 = dati[4] == 2
    if not classe64:
        return None  # a 32 bit il requisito non si applica
    e_phoff = struct.unpack_from("<Q", dati, 0x20)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", dati, 0x36)
    minimo = None
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", dati, off)[0]
        if p_type == PT_LOAD:
            p_align = struct.unpack_from("<Q", dati, off + 0x30)[0]
            minimo = p_align if minimo is None else min(minimo, p_align)
    return minimo


def main(percorso: str) -> int:
    problemi = 0
    with zipfile.ZipFile(percorso) as z:
        for nome in sorted(z.namelist()):
            if not nome.endswith(".so") or "arm64" not in nome:
                continue
            allineamento = allineamento_minimo(z.read(nome))
            if allineamento is None:
                continue
            ok = allineamento >= 16384
            problemi += 0 if ok else 1
            print(f"{'OK ' if ok else 'NO '} {allineamento:>6}  {nome}")
    print("Tutte le librerie sono compatibili con 16 KB" if problemi == 0 else f"{problemi} librerie NON compatibili con 16 KB")
    return 1 if problemi else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
