package com.emi.cordova.admob.nextgen;

import static com.emi.cordova.admob.nextgen.AdMobNextGen.isInitialized;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView; 
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;

import java.util.Date;

public class InterstitialExecutor {

    private static final String TAG = "AdMobInterstitial";
    private CordovaInterface cordova;
    private CordovaWebView webView; 

    private InterstitialAd interstitialAd;

    private boolean isLoading = false;
    private boolean isAutoShow = true;

    private long lastLoadTime = 0;
    private long minLoadInterval = 5000; 

    public InterstitialExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void createInterstitial(JSONArray args, CallbackContext callbackContext) {
        try {
            JSONObject options = args.getJSONObject(0);
            String adUnitId = options.getString("adUnitId");

            if (options.has("isAutoShow")) {
                this.isAutoShow = options.getBoolean("isAutoShow");
            } else {
                this.isAutoShow = true;
            }

            if (options.has("retryInterval")) {
                this.minLoadInterval = options.getLong("retryInterval");
            }

            loadInterstitial(adUnitId, callbackContext);

        } catch (JSONException e) {
            callbackContext.error("Invalid JSON Args: " + e.getMessage());
        }
    }

    private void loadInterstitial(String adUnitId, CallbackContext requestCallback) {
        if (!isInitialized) {
            requestCallback.error("SDK not initialized");
            return;
        }
        long currentTime = new Date().getTime();

        if (isLoading) {

            return;
        }

        if (interstitialAd != null) {

            if (requestCallback != null) {
                 requestCallback.success("Ad already loaded and ready"); 
            }
            fireEvent("on.interstitial.loaded", null);

            if (isAutoShow) {
                showInterstitialAd();
            }

            return;
        }

        if ((currentTime - lastLoadTime) < minLoadInterval) {

            if (requestCallback != null) requestCallback.error("Request too fast. Please wait " + minLoadInterval + " ms to prevent invalid traffic.");
            return;
        }

        isLoading = true;
        lastLoadTime = currentTime;

        PluginResult r = new PluginResult(PluginResult.Status.OK, "Loading started...");
        requestCallback.sendPluginResult(r);

        cordova.getThreadPool().execute(() -> {
            AdRequest request = new AdRequest.Builder(adUnitId).build();

            InterstitialAd.load(
                    request,
                    new AdLoadCallback<InterstitialAd>() {
                        @Override
                        public void onAdLoaded(@NonNull InterstitialAd ad) {
                            isLoading = false;
                            interstitialAd = ad;

                            fireEvent("on.interstitial.loaded", null);

                            if (isAutoShow) {
                                showInterstitialAd();
                            }

                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            isLoading = false;

                            try {
                                JSONObject errData = new JSONObject();
                                errData.put("code", loadAdError.getCode());
                                errData.put("message", loadAdError.getMessage());
                                fireEvent("on.interstitial.failed.load", errData);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
            );
        });
    }

    public void showInterstitial(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (interstitialAd != null) {
                showInterstitialAd();
                callbackContext.success();
            } else {
                callbackContext.error("Ad not ready");
            }
        });
    }

    private void showInterstitialAd() {
        if (interstitialAd == null) return;

        Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {

            if (!AdMobNextGen.isAppInForeground) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("code", "APP_IN_BACKGROUND");
                    errData.put("message", "Ad presentation blocked: Application is currently in the background.");
                    fireEvent("on.interstitial.failed.show", errData);
                } catch (JSONException e) {}
                return; 
            }

            interstitialAd.setAdEventCallback(new InterstitialAdEventCallback() {

                @Override
                public void onAdPaid(@NonNull AdValue adValue) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("value", adValue.getValueMicros());
                        data.put("currency", adValue.getCurrencyCode());
                        data.put("precision", adValue.getPrecisionType());

                        fireEvent("on.interstitial.revenue", data);
                    } catch (JSONException e) {

                    }
                }

                @Override
                public void onAdShowedFullScreenContent() {

                    fireEvent("on.interstitial.shown", null);
                }

                @Override
                public void onAdDismissedFullScreenContent() {

                    interstitialAd = null;
                    isLoading = false;
                    fireEvent("on.interstitial.dismissed", null);
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {

                    interstitialAd = null;
                    try {
                        JSONObject errData = new JSONObject();
                        errData.put("message", error.getMessage());
                        fireEvent("on.interstitial.failed.show", errData);
                    } catch (JSONException e) {}
                }

                @Override
                public void onAdImpression() {
                    fireEvent("on.interstitial.impression", null);
                }

                @Override
                public void onAdClicked() {
                    fireEvent("on.interstitial.clicked", null);
                }
            });
                interstitialAd.show(activity);
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

            String jsCommand = js.toString();

            if (webView != null) {
                webView.loadUrl(jsCommand);
            }
        });
    }
}
