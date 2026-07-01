# Rádio Ponta do Mato — App Android

App Android nativa que mostra o site completo (`pontadomato.likesyou.org`)
num WebView, com um serviço de rádio nativo em paralelo que garante
reprodução contínua em background (ecrã apagado, app minimizada, troca
de apps) — coisa que um WebView puro não consegue fazer de forma fiável.

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

## Passo 1 — Adicionar o snippet de integração ao site

Abre `android-integration-snippet.html` (incluído nesta entrega) e
copia o `<script>` para dentro do teu `index.html`, **depois** do
script do player existente (depois da linha `})();` que fecha o
"PLAYER SCRIPT"), por exemplo logo antes do `</body>` ou a seguir ao
bloco do SPA router.

**Importante:** este snippet não altera nada do comportamento atual —
só liga aos eventos que já existem (`classList.contains('playing')`).
Se abrires o site num browser normal (Chrome, etc.), o snippet detecta
que `window.AndroidRadio` não existe e não faz nada.

Faz upload do `index.html` atualizado para o InfinityFree como sempre.

## Passo 2 — Compilar a app no Android Studio

1. Instala o [Android Studio](https://developer.android.com/studio) (gratuito).
2. Abre a pasta `PontaDoMatoApp/` como projeto existente (File → Open).
3. Deixa o Gradle sincronizar (vai descarregar o Media3/ExoPlayer automaticamente).
4. Liga um telemóvel Android por USB com "Depuração USB" ativada, ou usa
   um emulador.
5. Clica em **Run ▶**.

Isto instala e testa a app diretamente no teu telemóvel.

## Passo 3 — Gerar o APK/AAB para distribuir

Para teres um ficheiro instalável (APK) a dar a amigos, ou para submeter
à Google Play:

1. No Android Studio: **Build → Generate Signed Bundle / APK**.
2. Escolhe **APK** (para partilhar diretamente) ou **Android App Bundle**
   (formato exigido pela Google Play).
3. Cria uma **keystore** nova (guarda a password e o ficheiro `.jks` em
   local seguro — vais precisar dele para todas as atualizações futuras
   da app; perdê-lo significa não poderes atualizar a app já publicada).
4. O APK final aparece em `app/release/app-release.apk`.

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

## O que falta decidir/ajustar

- **Ícone**: criei um ícone placeholder (triângulo violeta/cyan, alusivo
  ao psytrance). Se tiveres um logo oficial, é só substituir os ficheiros
  em `res/mipmap-anydpi-v26/` e `res/drawable/ic_launcher_*.xml`.
- **Nome do pacote**: usei `org.pontadomato.radio`. Se quiseres outro
  (ex: `com.pontadomato.app`), tem de ser alterado no `build.gradle`,
  no `AndroidManifest.xml` (pacote implícito) e na pasta `java/`.
- **Publicação na Google Play**: requer conta de developer (pagamento
  único de 25 USD) e cumprir as políticas de conteúdo — não deve haver
  problema para uma rádio, mas vale a pena rever a política de "apps de
  música/áudio em background" da Play Store antes de submeter.
