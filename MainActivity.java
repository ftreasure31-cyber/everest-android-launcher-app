package com.everest.launcher;

import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private WebView wv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        setContentView(wv);
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1);
        }
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(() -> r.grant(r.getResources()));
            }
        });
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    public void onBackPressed() {
        wv.evaluateJavascript("goBack()", null);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) wv.evaluateJavascript("goHome()", null);
    }

    private void js(final String code) {
        runOnUiThread(() -> wv.evaluateJavascript("window.onBio&&" + code, null));
    }

    private String icon(Drawable d) {
        Bitmap bm = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, 96, 96);
        d.draw(c);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, o);
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    private class Bridge {
        @JavascriptInterface
        public String listApps() {
            try {
                PackageManager pm = getPackageManager();
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                JSONArray a = new JSONArray();
                for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                    if (r.activityInfo.packageName.equals(getPackageName())) continue;
                    JSONObject o = new JSONObject();
                    o.put("n", r.loadLabel(pm).toString());
                    o.put("p", r.activityInfo.packageName);
                    o.put("i", icon(r.loadIcon(pm)));
                    a.put(o);
                }
                return a.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void launch(String p) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) startActivity(i);
        }

        @JavascriptInterface
        public void uninstall(String p) {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + p)));
        }

        @JavascriptInterface
        public void setDefaultHome() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        RoleManager rm = getSystemService(RoleManager.class);
                        if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1);
                            return;
                        }
                    }
                    startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            });
        }

        @JavascriptInterface
        public void openSettings(String k) {
            String a;
            switch (k) {
                case "wifi": a = Settings.ACTION_WIFI_SETTINGS; break;
                case "bluetooth": a = Settings.ACTION_BLUETOOTH_SETTINGS; break;
                case "display": a = Settings.ACTION_DISPLAY_SETTINGS; break;
                case "sound": a = Settings.ACTION_SOUND_SETTINGS; break;
                case "battery": a = Intent.ACTION_POWER_USAGE_SUMMARY; break;
                case "apps": a = Settings.ACTION_APPLICATION_SETTINGS; break;
                case "storage": a = Settings.ACTION_INTERNAL_STORAGE_SETTINGS; break;
                case "location": a = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
                case "security": a = Settings.ACTION_SECURITY_SETTINGS; break;
                case "accessibility": a = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
                default: a = Settings.ACTION_SETTINGS;
            }
            try {
                startActivity(new Intent(a));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }

        @JavascriptInterface
        public void torch(boolean on) {
            try {
                CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics c = cm.getCameraCharacteristics(id);
                    Boolean f = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer l = c.get(CameraCharacteristics.LENS_FACING);
                    if (f != null && f && l != null && l == CameraCharacteristics.LENS_FACING_BACK) {
                        cm.setTorchMode(id, on);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public boolean biometricAvailable() {
            return BiometricManager.from(MainActivity.this)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void biometric() {
            runOnUiThread(() -> {
                Executor ex = ContextCompat.getMainExecutor(MainActivity.this);
                BiometricPrompt bp = new BiometricPrompt(MainActivity.this, ex, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        js("onBio(true)");
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        js("onBio(false)");
                    }
                });
                bp.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock EVEREST")
                        .setNegativeButtonText("Cancel")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build());
            });
        }
    }
}
