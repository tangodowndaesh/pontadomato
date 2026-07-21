package org.pontadomato.radio;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Serviço que mantém o stream da Rádio Ponta do Mato a tocar em
 * background (ecrã apagado, app minimizada, troca de apps), com
 * notificação de media controls (play/pause, título, artista, capa).
 *
 * Independente do player do site (WebView): este serviço liga
 * diretamente ao stream MP3 e ao endpoint de metadados do AzuraCast.
 */
public class RadioPlaybackService extends MediaSessionService {

    private static final String TAG = "RadioPlaybackService";

    public static final String STREAM_URL =
            "https://a13.asurahosting.com/listen/ponta_do_mato/radio.mp3";
    public static final String STATUS_URL =
            "https://a13.asurahosting.com/status-json.xsl";
    public static final String MOUNT_MATCH = "radio.mp3";
    public static final String STATION_NAME = "Rádio Ponta do Mato";
    public static final String FALLBACK_ART =
            "https://pontadomato.likesyou.org/channels4_profile.jpg";
    private static final long POLL_MS = 15000L;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler bgHandler = new Handler(Looper.getMainLooper());

    private String lastTitle = "";
    private String lastArtist = "";
    private final String lastArtUrl = FALLBACK_ART;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchNowPlayingAsync();
            bgHandler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();
        player.setMediaItem(buildMediaItem(STATION_NAME, "A ligar…", lastArtUrl));
        player.setPlayWhenReady(false);
        player.prepare();

        mediaSession = new MediaSession.Builder(this, player).build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_IDLE) {
                    // erro/desconexão: tenta religar
                    mainHandler.postDelayed(() -> {
                        if (player != null && player.getPlayWhenReady()) {
                            reconnect();
                        }
                    }, 3000);
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (playWhenReady) {
                    bgHandler.removeCallbacks(pollRunnable);
                    bgHandler.post(pollRunnable);
                } else {
                    bgHandler.removeCallbacks(pollRunnable);
                }
            }
        });
    }

    private MediaItem buildMediaItem(String title, String artist, String artUrl) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(android.net.Uri.parse(artUrl))
                .setIsPlayable(true)
                .build();

        return new MediaItem.Builder()
                .setUri(STREAM_URL)
                .setMediaMetadata(metadata)
                .build();
    }

    /** Reentra no stream a partir do "live edge" — usado para play() inicial e reconexão. */
    private void reconnect() {
        player.stop();
        player.setMediaItem(buildMediaItem(
                lastTitle.isEmpty() ? STATION_NAME : lastTitle,
                lastArtist.isEmpty() ? "RÁDIO PONTA DO MATO" : lastArtist,
                lastArtUrl));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    /** Chamado pela MainActivity quando o utilizador dá play no site (WebView). */
    public void startPlayback() {
        reconnect();
    }

    public void stopPlayback() {
        player.setPlayWhenReady(false);
        player.stop();
        bgHandler.removeCallbacks(pollRunnable);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        bgHandler.removeCallbacks(pollRunnable);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(@Nullable android.content.Intent rootIntent) {
        // Se não estiver a tocar quando o utilizador "fecha" a app (swipe), encerra o serviço.
        // Se estiver a tocar, o MediaSessionService mantém-se vivo (comportamento esperado de rádio).
        if (player == null || !player.isPlaying()) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    // ---- Polling de metadados (status-json.xsl) ----

    private void fetchNowPlayingAsync() {
        new Thread(() -> {
            try {
                String json = httpGet(STATUS_URL);
                if (json == null) return;
                parseAndApply(json);
            } catch (Exception e) {
                Log.w(TAG, "Falha ao obter metadados: " + e.getMessage());
            }
        }).start();
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        try (InputStream is = conn.getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private void parseAndApply(String jsonStr) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        if (!root.has("icestats")) return;
        JSONObject icestats = root.getJSONObject("icestats");
        if (!icestats.has("source")) return;

        Object srcObj = icestats.get("source");
        JSONObject mount = null;

        if (srcObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) srcObj;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.getJSONObject(i);
                String listenUrl = s.optString("listenurl", "");
                if (listenUrl.contains(MOUNT_MATCH)) {
                    mount = s;
                    break;
                }
            }
            if (mount == null && arr.length() > 0) mount = arr.getJSONObject(0);
        } else if (srcObj instanceof JSONObject) {
            mount = (JSONObject) srcObj;
        }

        if (mount == null) return;

        String title = "";
        String artist = "";

        String songTitle = mount.optString("title", mount.optString("yp_currently_playing", ""));
        if (!songTitle.isEmpty()) {
            // formato comum Icecast: "Artista - Título"
            int idx = songTitle.indexOf(" - ");
            if (idx > -1) {
                artist = songTitle.substring(0, idx).trim();
                title = songTitle.substring(idx + 3).trim();
            } else {
                title = songTitle.trim();
            }
        }

        if (title.isEmpty()) title = STATION_NAME;
        if (artist.isEmpty()) artist = "RÁDIO PONTA DO MATO";

        if (title.equals(lastTitle) && artist.equals(lastArtist)) return;

        lastTitle = title;
        lastArtist = artist;

        final String finalTitle = title;
        final String finalArtist = artist;
        mainHandler.post(() -> updateNowPlayingMetadata(finalTitle, finalArtist, lastArtUrl));
    }

    private void updateNowPlayingMetadata(String title, String artist, String artUrl) {
        if (player == null) return;
        MediaItem current = player.getCurrentMediaItem();
        if (current == null) return;

        MediaMetadata updated = current.mediaMetadata.buildUpon()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(android.net.Uri.parse(artUrl))
                .build();

        MediaItem newItem = current.buildUpon().setMediaMetadata(updated).build();

        int idx = player.getCurrentMediaItemIndex();
        boolean wasPlaying = player.isPlaying();
        player.replaceMediaItem(idx, newItem);
        if (wasPlaying) player.play();
    }
}
