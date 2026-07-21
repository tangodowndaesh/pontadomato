package org.pontadomato.radio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Activity principal: mostra o site completo da Rádio Ponta do Mato
 * dentro de um WebView. A reprodução de áudio robusta (background,
 * ecrã bloqueado) é delegada ao RadioPlaybackService através da
 * ponte JS "AndroidRadio" injetada no WebView.
 */
@UnstableApi
public class MainActivity extends AppCompatActivity {

    private static final String SITE_URL = "https://pontadomato.likesyou.org/";

    private WebView webView;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        setupWebView();
        connectToPlaybackService();

        if (savedInstanceState == null) {
            webView.loadUrl(SITE_URL);
        }
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidRadioBridge(), "AndroidRadio");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();
                // mantém navegação dentro do site no WebView; links externos abrem no browser
                if (host != null && host.contains("pontadomato.likesyou.org")) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
    }

    /** Liga ao RadioPlaybackService via MediaController (Media3). */
    private void connectToPlaybackService() {
        SessionToken sessionToken = new SessionToken(
                this, new ComponentName(this, RadioPlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Ponte exposta ao JavaScript do site (window.AndroidRadio.xxx()).
     * O player do site (botão #pdmPlayBtn) continua a tocar normalmente
     * no WebView para o visual/equalizador; esta ponte garante que o
     * serviço nativo assume o stream para continuidade em background.
     */
    private class AndroidRadioBridge {

        @JavascriptInterface
        public void onPlay() {
            runOnUiThread(() -> {
                if (mediaController != null) {
                    mediaController.play();
                } else {
                    Intent intent = new Intent(MainActivity.this, RadioPlaybackService.class);
                    ActivityCompat.startForegroundService(MainActivity.this, intent);
                }
            });
        }

        @JavascriptInterface
        public void onPause() {
            runOnUiThread(() -> {
                if (mediaController != null) {
                    mediaController.pause();
                }
            });
        }

        @JavascriptInterface
        public void onVolumeChange(int percent) {
            runOnUiThread(() -> {
                if (mediaController != null) {
                    mediaController.setVolume(percent / 100f);
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        webView.destroy();
        super.onDestroy();
    }
}
