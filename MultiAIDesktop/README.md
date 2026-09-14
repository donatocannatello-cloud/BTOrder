# Multi AI Desktop

App Windows che raggruppa in un'unica finestra le interfacce web di Claude, ChatGPT e Gemini,
una per tab, così non serve saltare da un'app all'altra.

Non usa API né automatizza il login: ogni tab è un browser (WebView2/Chromium) che carica
il sito ufficiale del servizio. Il login lo fai tu, normalmente, la prima volta; la sessione
resta salvata tra un avvio e l'altro (i cookie vengono salvati in
`%LOCALAPPDATA%\MultiAIDesktop\WebView2`).

## Requisiti

- Windows 10/11
- [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
- Runtime WebView2 (già preinstallato su Windows 10/11 aggiornati; se manca, Windows lo scarica
  automaticamente oppure si trova su https://developer.microsoft.com/microsoft-edge/webview2/)

## Build ed esecuzione

Da terminale (PowerShell o cmd), nella cartella `MultiAIDesktop`:

```
dotnet restore
dotnet run
```

Oppure apri `MultiAIDesktop.csproj` con Visual Studio 2022+ e premi F5.

Per generare un eseguibile standalone da distribuire:

```
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

L'eseguibile finale sarà in `bin\Release\net8.0-windows\win-x64\publish\`.

## Personalizzare i servizi

I servizi mostrati nelle tab sono definiti in `services.json`:

```json
[
  { "Name": "Claude", "Url": "https://claude.ai" },
  { "Name": "ChatGPT", "Url": "https://chatgpt.com" },
  { "Name": "Gemini", "Url": "https://gemini.google.com" }
]
```

Puoi aggiungere altri servizi (es. Perplexity, Copilot, Mistral...) aggiungendo righe a questo
file: verranno caricati come nuove tab al prossimo avvio.

## Scorciatoie

- `Ctrl+1`, `Ctrl+2`, `Ctrl+3`, ... — passa alla tab N
- Toolbar in alto: Indietro / Avanti / Ricarica / Zoom +/-
