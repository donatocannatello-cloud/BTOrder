package com.donatocannatello.frattale;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

/**
 * Schermo intero reale, lato nativo.
 *
 * <p>La chiamata a {@code requestFullscreen()} che il codice web fa entrando
 * non basta dentro una WebView di Capacitor: la Fullscreen API del browser
 * agisce su un elemento del documento, non sulla finestra dell'activity, e
 * quindi barra di stato e barra di navigazione di Android restano comunque
 * visibili sopra e sotto. L'unico modo per toglierle davvero e' chiederlo
 * qui, all'activity.
 *
 * <p>L'immersivo e' permanente e non solo all'ingresso: l'app e' a schermo
 * intero anche sulla schermata iniziale, dato che non c'e' nessuna UI di
 * sistema che serva in nessun momento.
 */
public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawIntoDisplayCutout();
        enableImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Le barre di sistema tornano a mostrarsi dopo uno swipe dal bordo o
        // al rientro da un'altra app: senza rinasconderle qui l'immersivo
        // durerebbe solo fino al primo gesto dell'utente.
        if (hasFocus) {
            enableImmersiveMode();
        }
    }

    private void enableImmersiveMode() {
        // Il contenuto disegna sotto le barre di sistema (edge-to-edge), poi
        // le barre vengono nascoste.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // Uno swipe dal bordo le mostra solo temporaneamente, in overlay,
        // senza ridimensionare la WebView. E' importante qui: un resize del
        // canvas a meta' navigazione costringerebbe a riallocare il
        // framebuffer WebGL e a ricalcolare la scala di rendering, con uno
        // scatto visibile proprio mentre si sta scorrendo la mappa.
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void drawIntoDisplayCutout() {
        // Sui telefoni con notch/foro la finestra si fermerebbe al di sotto
        // di esso lasciando una fascia nera: qui si chiede di disegnare fino
        // ai bordi corti. Il layout web tiene comunque i comandi al sicuro
        // dall'intaglio tramite env(safe-area-inset-*), che la WebView
        // popola grazie a viewport-fit=cover nel meta viewport.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }
    }
}
