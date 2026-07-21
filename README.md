# Rádio Ponta do Mato — App Android

App Android nativa que mostra o site completo (`pontadomato.likesyou.org`)
num WebView, com um serviço de rádio nativo em paralelo que garante
reprodução contínua em background (ecrã apagado, app minimizada, troca
de apps).

## Estrutura do projeto

```
PontaDoMatoApp/
├── app/
│   ├── build.gradle                  → dependências (Media3/ExoPlayer)
│   └── src/main/
│       ├── AndroidManifest.xml       → permissões + serviço + activity
│       ├── java/org/pontadomato/radio/
│       │   ├── MainActivity.java     → WebView + ponte JS↔Android
│       │   └── RadioPlaybackService.java → player nativo + notificação
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml, styles.xml
│           ├── drawable/ic_launcher_*.xml
│           └── mipmap-anydpi-v26/ic_launcher*.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Notas sobre o comportamento de background

- O `RadioPlaybackService` liga **diretamente** ao stream
  (`a13.asurahosting.com/listen/ponta_do_mato/radio.mp3`), independente
  do `<audio>` do WebView. Por isso o som não corta quando bloqueias o
  ecrã ou trocas de app.
- Os metadados (música a tocar) são obtidos do mesmo
  `status-json.xsl` que o site já usa, a cada 15 segundos.
- A notificação de media controls (play/pause, capa do álbum) aparece
  automaticamente — é gerida pelo Media3, não precisas de código extra.
- Se o utilizador fechar a app (swipe na lista de recentes) **enquanto
  não está a tocar**, o serviço encerra. Se estiver a tocar, mantém-se
  vivo — comportamento normal de apps de rádio.
- Esta apk não requer nenhuma permissão, é simplesmente uma webview do
  website oficial da rádio. 
- Este projeto não tem fins lucrativos pelo que a app está e estará sempre livre de publicidade.
