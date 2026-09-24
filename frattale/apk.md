# Come si genera e pubblica l'APK

Riferimento rapido per chi (persona o altra chat) deve capire come questo
progetto produce l'APK Android, senza dover ricostruire il ragionamento da
zero leggendo la CI riga per riga.

## In breve

Ogni push su `frattale/**` (qualsiasi branch) fa partire il workflow
`.github/workflows/frattale-android.yml`, che builda il sito, lo impacchetta
con Capacitor in un progetto Android, compila un APK di debug e lo pubblica
sulla release GitHub taggata `latest`. Il link di download **non cambia
mai**:

```
https://github.com/donatocannatello-cloud/BTOrder/releases/download/latest/frattale.apk
```

Ogni build sovrascrive quella release: chi ha già il link non deve
aggiornarlo, gli basta riscaricare l'APK.

## Pipeline passo per passo

Il job (`build-apk`, `ubuntu-latest`, working directory `frattale/`):

1. **Checkout + Node 22**
2. `npm ci && npm run build` — build Vite del sito in `frattale/dist`.
   - `vite.config.ts` inietta `__BUILD_ID__` (i primi 7 caratteri dello SHA
     del commit, da `GITHUB_SHA`) come stringa globale, mostrata in un
     angolo della schermata iniziale (`main.ts`). Serve a verificare a colpo
     d'occhio se l'APK appena installato è davvero l'ultima build o un file
     vecchio riscaricato per sbaglio con lo stesso nome.
2. `npx cap sync android` — copia `dist/` dentro il progetto Android nativo
   (`frattale/android/`) e sincronizza i plugin Capacitor.
3. **Setup Java 21 (Temurin) + Android SDK** (`android-actions/setup-android`).
4. `./gradlew assembleDebug --no-daemon` dentro `frattale/android/`, con
   `ANDROID_VERSION_CODE=${{ github.run_number }}`.
   - `applicationId`/`versionName` restano fissi (vedi `build.gradle` e
     `capacitor.config.json`, `appId: com.donatocannatello.frattale`); solo
     il `versionCode` sale a ogni run, perché Android rifiuta di installare
     un aggiornamento con lo stesso `versionCode` di quello già presente.
   - La build usa una **firma di debug fissa e committata nel repo**
     (`android/app/debug.keystore`, credenziali standard
     `androiddebugkey`/`android` — non è una chiave di release, è la stessa
     identità pubblica che genera Android Studio in locale). Senza questo,
     ogni run della CI userebbe un keystore di debug generato al volo su un
     runner effimero, diverso ogni volta, e Android rifiuterebbe di
     installare l'update sopra una versione già presente sul telefono
     ("conflitto con un pacchetto esistente").
5. `cp android/app/build/outputs/apk/debug/app-debug.apk frattale.apk` — nome
   fisso, cosi il link di download non cambia mai.
6. `ncipollo/release-action` pubblica (o sovrascrive) la release con tag
   `latest`, `allowUpdates: true`, `replacesArtifacts: true`,
   `makeLatest: true`. Il corpo della release riporta lo SHA del commit e il
   numero di run, per tracciare da quale build proviene.

## Come si triggera manualmente

Il workflow ha anche `workflow_dispatch`, quindi può essere lanciato a mano
dalla tab Actions di GitHub senza dover fare un push a vuoto.

## Come verificare che una build è quella giusta (da un'altra chat)

1. `mcp__github__actions_list` (o `get_job_logs`) sul branch/commit di
   interesse, per controllare che `conclusion == "success"`.
2. `mcp__github__get_release_by_tag` con `tag: "latest"` — il campo `body`
   riporta lo SHA del commit e il numero di run; il timestamp
   dell'asset `frattale.apk` conferma quando è stato ricaricato.
3. Il numero mostrato nell'angolo della schermata iniziale dell'app
   (`build <sha corto>`) deve combaciare con i primi 7 caratteri dello SHA
   appena pushato.

## Sviluppo/anteprima locali (senza Android)

`npm run dev` (o `npm run build && npm run preview`) dentro `frattale/`
serve il sito via Vite/browser — utile per iterare e per verificare con
Playwright, ma **non** è l'APK: è solo il livello web, senza passare da
Capacitor/Gradle. Per generare davvero l'APK localmente servirebbero Java 21
e l'Android SDK installati, poi le stesse istruzioni dei passi 2–5 sopra
eseguite a mano.
