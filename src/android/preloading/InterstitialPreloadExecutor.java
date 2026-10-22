package com.emi.cordova.admob.nextgen.preloading;
import static com.emi.cordova.admob.nextgen.AdMobNextGen.isInitialized;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

public class InterstitialPreloadExecutor {

    private static final String TAG = "AdMobInterstitialPreload";
    private final CordovaInterface cordova;
    private final CordovaWebView webView;

    private boolean isPreloaderActive = false;
    private boolean isAutoShow = false;
    private String currentAdUnitId;

    private long lastShowTime = 0;
    private long minShowInterval = 5000; 

    public InterstitialPreloadExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void startPreload(JSONArray args, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("SDK not initialized");
            return;
        }
        try {
            JSONObject options = args.getJSONObject(0);
            String adUnitId = options.getString("adUnitId");

            if (isPreloaderActive && adUnitId.equals(currentAdUnitId)) {
                if (callbackContext != null) callbackContext.success("Interstitial Preloader already active");
                return;
            }

            if (options.has("retryInterval")) {
                this.minShowInterval = options.getLong("retryInterval");
            }

            int bufferSize = 1; 
            if (options.has("bufferSize")) {
                int requestedSize = options.getInt("bufferSize");

                bufferSize = Math.max(1, Math.min(requestedSize, 3));

                if (requestedSize > 3) {
                    if (callbackContext != null) callbackContext.error("WARNING: Requested bufferSize (" + requestedSize + ") exceeds the maximum safe limit. Automatically clamped to 3 to prevent OOM and AdMob policy violations.");
                } else if (requestedSize < 1) {
                    if (callbackContext != null) callbackContext.error("WARNING: Requested bufferSize (" + requestedSize + ") is invalid. Automatically clamped to 1.");
                }
            }

            if (options.has("isAutoShow")) {
                this.isAutoShow = options.getBoolean("isAutoShow");
            }

            this.currentAdUnitId = adUnitId;
            this.isPreloaderActive = true;
            final int finalBufferSize = bufferSize;

            cordova.getActivity().runOnUiThread(() -> {
                AdRequest adRequest = new AdRequest.Builder(adUnitId).build();
                PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest, finalBufferSize);

                PreloadCallback preloadCallback = new PreloadCallback() {
                    @Override
                    public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {

                        try {
                            JSONObject data = new JSONObject();
                            data.put("adUnitId", adUnitId);
                            data.put("source", "preloader");
                            fireEvent("on.interstitial.loaded", data);
                        } catch (JSONException ignored) {}

                        if (isAutoShow) {

                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                showPolledAd(null, null);
                            });
                        }

                    }

                    @Override
                    public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {

                        try {
                            JSONObject err = new JSONObject();
                            err.put("message", adError.getMessage());
                            err.put("code", adError.getCode());
                            err.put("adUnitId", adUnitId);
                            err.put("source", "preloader");
                            fireEvent("on.interstitial.failed", err);
                        } catch (JSONException ignored) {
                        }
                    }

                    @Override
                    public void onAdsExhausted(@NonNull String preloadId) {

                        try {
                            JSONObject data = new JSONObject();
                            data.put("adUnitId", adUnitId);
                            data.put("source", "preloader");
                            fireEvent("on.interstitial.preload.exhausted", data);
                        } catch (JSONException ignored) {}
                    }
                };

                InterstitialAdPreloader.start(adUnitId, preloadConfig, preloadCallback);
                if (callbackContext != null) {
                    callbackContext.success("Interstitial Preloader Started");
                }
            });

        } catch (JSONException e) {
            isPreloaderActive = false;
            if (callbackContext != null) {
                callbackContext.error("Invalid arguments: " + e.getMessage());
            }
        }
    }

    public void showPolledAd(JSONArray args, CallbackContext callbackContext) {
        if (currentAdUnitId == null || currentAdUnitId.isEmpty() || !isPreloaderActive) {
            if (callbackContext != null) {
                callbackContext.error("Interstitial Preloader engine is not running.");
            }
            return;
        }

        long currentTime = new Date().getTime();
        if ((currentTime - lastShowTime) < minShowInterval) {
            if (callbackContext != null) callbackContext.error("Request too fast. Please wait " + minShowInterval + " ms to prevent invalid traffic.");
            return;
        }

        cordova.getActivity().runOnUiThread(() -> {

            final InterstitialAd ad = InterstitialAdPreloader.pollAd(currentAdUnitId);

            if (ad == null) {
                if (callbackContext != null) callbackContext.error("Interstitial Pool Empty");
                return;
            }

            this.lastShowTime = currentTime;
            setupAdEvents(ad);
            ad.show(cordova.getActivity());
            if (callbackContext != null){
                callbackContext.success("Interstitial Ad Shown from Pool");
            }
        });
    }

    public void checkAdAvailable(JSONArray args, CallbackContext callbackContext) {
        if (currentAdUnitId == null || currentAdUnitId.isEmpty()) {
            if (callbackContext != null) callbackContext.success(0); 
            return;
        }

        cordova.getActivity().runOnUiThread(() -> {
            boolean isAvailable = InterstitialAdPreloader.isAdAvailable(currentAdUnitId);
            if (callbackContext != null) {
                callbackContext.success(isAvailable ? 1 : 0);
            }
        });
    }

    public void stopPreloadAndClear(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            isPreloaderActive = false;
            if (currentAdUnitId != null) {
                InterstitialAdPreloader.destroy(currentAdUnitId);

                currentAdUnitId = null;
            }
            if (callbackContext != null) {
                callbackContext.success("Interstitial Preloader stopped and cleared");
            }
        });
    }

    public void setAutoShow(boolean shouldAutoShow) {
        this.isAutoShow = shouldAutoShow;
    }

    public boolean shouldAutoShow() {
        return this.isAutoShow;
    }

    private void setupAdEvents(InterstitialAd ad) {
        ad.setAdEventCallback(new InterstitialAdEventCallback() {
            @Override
            public void onAdImpression() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.interstitial.impression", data);
                } catch (JSONException ignored) {}
            }

            @Override
            public void onAdShowedFullScreenContent() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.interstitial.shown", data);
                } catch (JSONException ignored) {}
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.interstitial.dismissed", data);
                } catch (JSONException ignored) {}

            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("message", error.getMessage());
                    errData.put("code", error.getCode());
                    errData.put("source", "preloader");
                    fireEvent("on.interstitial.failed.show", errData);
                } catch (JSONException ignored) {}
            }

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("value", adValue.getValueMicros());
                    data.put("currency", adValue.getCurrencyCode());
                    data.put("precision", adValue.getPrecisionType());
                    data.put("source", "preloader");
                    fireEvent("on.interstitial.revenue", data);
                } catch (JSONException ignored) {}
            }
        });
    }

    private void fireEvent(String eventName, JSONObject data) {
        cordova.getActivity().runOnUiThread(() -> {
            StringBuilder js = new StringBuilder();
            js.append("javascript:cordova.fireDocumentEvent('");
            js.append(eventName);
            js.append("'");
            if (data != null) {
                js.append(", ");
                js.append(data.toString());
            }
            js.append(");");
            if (webView != null) webView.loadUrl(js.toString());
        });
    }
}
