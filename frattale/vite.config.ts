import { defineConfig } from "vite";
import { execSync } from "node:child_process";

// Breve id di build mostrato nella schermata iniziale (vedi main.ts): in CI
// e' il commit short SHA (GITHUB_SHA e' gia' impostata di default su ogni
// step di GitHub Actions, nessuna modifica al workflow necessaria); in
// locale ricade sull'HEAD di git. Serve a confermare a colpo d'occhio se
// l'APK appena installato e' davvero l'ultima build o un file scaricato in
// precedenza e riaperto per sbaglio (Android/i download manager a volte
// riusano un file con lo stesso nome invece di riscaricarlo).
function buildId(): string {
  const sha = process.env.GITHUB_SHA;
  if (sha) return sha.slice(0, 7);
  try {
    return execSync("git rev-parse --short HEAD").toString().trim();
  } catch {
    return "dev";
  }
}

export default defineConfig({
  base: "./",
  build: {
    target: "es2020",
  },
  define: {
    __BUILD_ID__: JSON.stringify(buildId()),
  },
});
