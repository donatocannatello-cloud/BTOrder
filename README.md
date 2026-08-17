# BTOrder

Monorepo Gradle multi-modulo che ospita due app Android **indipendenti tra
loro**, ciascuna con il proprio `applicationId`, il proprio modulo Gradle e
il proprio README:

- **[`chiamatebt/`](chiamatebt/README.md)** — imposta un ordine di priorità
  personale tra i dispositivi audio Bluetooth/telefono e lo applica
  automaticamente a ogni chiamata (`it.example.chiamatebt`).
- **[`ripassofoto/`](ripassofoto/README.md)** — fotografa una pagina da
  studiare, ne estrae il testo con OCR on-device e genera domande di
  verifica per ripassare (`it.example.ripassofoto`).

## Struttura

```
BTOrder/
├── chiamatebt/     modulo Gradle indipendente, non toccare per lavorare su RipassoFoto
├── ripassofoto/    modulo Gradle indipendente, non toccare per lavorare su ChiamateBT
├── build.gradle.kts        dichiarazione plugin condivisi (AGP, Kotlin, KSP)
└── settings.gradle.kts     include(":chiamatebt") e include(":ripassofoto")
```

I due moduli non condividono codice: solo la dichiarazione dei plugin a
livello di root (`build.gradle.kts`) è comune, come da convenzione standard
dei progetti Gradle multi-modulo.

## Come compilare

```bash
./gradlew :chiamatebt:assembleDebug
./gradlew :ripassofoto:assembleDebug
```

Le build automatiche su GitHub Actions per RipassoFoto sono descritte nel
[README del modulo](ripassofoto/README.md#build-automatica-su-github-actions).
