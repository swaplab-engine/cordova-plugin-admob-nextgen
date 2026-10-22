package com.emi.cordova.admob.nextgen.preloading;

import static com.emi.cordova.admob.nextgen.AdMobNextGen.isInitialized;

import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;

public class AppOpenAdPreloadExecutor {

    private static final String TAG = "AdMobAppOpenPreload";

    private CordovaInterface cordova;
    private CordovaWebView webView;

    private String currentAdUnitId = null;

    private boolean isAutoShow = false;
    private long retryInterval = 5000;

    private long lastLoadTime = 0;

    private boolean isShowingAd = false;
    private long lastAdDismissTime = 0;

    public AppOpenAdPreloadExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void startPreload(JSONArray args, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("SDK not initialized");
            return;
        }
        try {
            long currentTime = new Date().getTime();
            String adUnitId = null;

            if (args != null && args.length() > 0) {
                JSONObject options = args.optJSONObject(0);
                if (options != null) {
                    if (options.has("adUnitId")) {
                        adUnitId = options.getString("adUnitId");
                    }
                    if (options.has("isAutoShow")) {
                        this.isAutoShow = options.getBoolean("isAutoShow");
                    }
                    if (options.has("retryInterval")) {
                        this.retryInterval = options.getLong("retryInterval");
                    }
                } else {
                    adUnitId = args.getString(0);
                }
            } else {

                if (callbackContext != null) callbackContext.error("adUnitId is required");
                return;
            }

            if (adUnitId != null) {
                this.currentAdUnitId = adUnitId;
            }

            if ((currentTime - lastLoadTime) < retryInterval) {
                if (callbackContext != null) {
                    callbackContext.error("Request too fast. Please wait " + retryInterval + " ms to prevent invalid traffic.");
                }
                return;
            }

            lastLoadTime = currentTime;

            PreloadCallback preloadCallback = new PreloadCallback() {
                @Override
                public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError loadAdError) {
                    try {
                        JSONObject errData = new JSONObject();
                        errData.put("preloadId", preloadId);
                        errData.put("code", loadAdError.getCode());
                        errData.put("message", loadAdError.getMessage());
                        errData.put("source", "preloader");
                        fireEvent("on.appopen.failed.load", errData);
                    } catch (JSONException ignored) {}
                }

                @Override
                public void onAdsExhausted(@NonNull String preloadId) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("preloadId", preloadId);
                        data.put("source", "preloader");
                        fireEvent("on.appopen.preload.exhausted", data);
                    } catch (JSONException ignored) {}
                }

                @Override
                public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("preloadId", preloadId);
                        data.put("source", "preloader");
                        fireEvent("on.appopen.loaded", data);
                    } catch (JSONException ignored) {}
                }
            };

            AdRequest adRequest = new AdRequest.Builder(this.currentAdUnitId).build();
            PreloadConfiguration preloadConfiguration = new PreloadConfiguration(adRequest);

            cordova.getActivity().runOnUiThread(() -> {
                AppOpenAdPreloader.start(this.currentAdUnitId, preloadConfiguration, preloadCallback);
                if (callbackContext != null) {
                    callbackContext.success("App open preload started");
                }
            });

        } catch (Exception e) {
            if (callbackContext != null) {
                callbackContext.error("Exception: " + e.getMessage());
            }
        }
    }

    public void showPolledAd(JSONArray args, CallbackContext callbackContext) {
        if (isShowingAd) {

            if (callbackContext != null) callbackContext.error("Ad already showing.");
            return;
        }

        if (new Date().getTime() - lastAdDismissTime < 1000) {

            if (callbackContext != null) callbackContext.error("Request too fast. Please wait " + lastAdDismissTime + " ms to prevent invalid traffic.");
            return;
        }

        try {
            String adUnitId = this.currentAdUnitId;

            if (args != null && args.length() > 0) {
                JSONObject options = args.optJSONObject(0);
                if (options != null && options.has("adUnitId")) {
                    adUnitId = options.getString("adUnitId");
                } else {
                    String stringArg = args.optString(0, null);
                    if (stringArg != null && !stringArg.isEmpty() && !stringArg.equals("null")) {
                        adUnitId = stringArg;
                    }
                }
            }

            if (adUnitId == null) {
                if (callbackContext != null) callbackContext.error("No Ad Unit ID available.");
                return;
            }

            final String finalAdUnitId = adUnitId;

            this.isShowingAd = true;

            cordova.getActivity().runOnUiThread(() -> {
                AppOpenAd ad = AppOpenAdPreloader.pollAd(finalAdUnitId);

                if (ad == null) {

                    this.isShowingAd = false;

                    try {
                        JSONObject errData = new JSONObject();
                        errData.put("message", "No preloaded app open ads available.");
                        errData.put("source", "preloader");
                        fireEvent("on.appopen.failed.show", errData);
                    } catch (JSONException ignored) {}

                    if (callbackContext != null) {
                        callbackContext.error("No preloaded app open ads available.");
                    }
                    return;
                }

                ad.setAdEventCallback(new AppOpenAdEventCallback() {
                    @Override
                    public void onAdPaid(@NonNull AdValue adValue) {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("value", adValue.getValueMicros());
                            data.put("currency", adValue.getCurrencyCode());
                            data.put("precision", adValue.getPrecisionType());
                            data.put("source", "preloader");
                            fireEvent("on.appopen.revenue", data);
                        } catch (JSONException ignored) {}
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("source", "preloader");
                            fireEvent("on.appopen.shown", data);
                        } catch (JSONException ignored) {}
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        isShowingAd = false;
                        lastAdDismissTime = new Date().getTime();
                        try {
                            JSONObject data = new JSONObject();
                            data.put("source", "preloader");
                            fireEvent("on.appopen.dismissed", data);
                        } catch (JSONException ignored) {}
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                        isShowingAd = false;
                        try {
                            JSONObject errData = new JSONObject();
                            errData.put("code", error.getCode());
                            errData.put("message", error.getMessage());
                            errData.put("source", "preloader");
                            fireEvent("on.appopen.failed.show", errData);
                        } catch (JSONException ignored) {}
                    }

                    @Override
                    public void onAdImpression() {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("source", "preloader");
                            fireEvent("on.appopen.impression", data);
                        } catch (JSONException ignored) {}
                    }

                    @Override
                    public void onAdClicked() {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("source", "preloader");
                            fireEvent("on.appopen.clicked", data);
                        } catch (JSONException ignored) {}
                    }
                });

                ad.show(cordova.getActivity());

                if (callbackContext != null) {
                    callbackContext.success("Showing preloaded app open ad");
                }
            });

        } catch (Exception e) {
            this.isShowingAd = false; 

            if (callbackContext != null) callbackContext.error("Exception: " + e.getMessage());
        }
    }

    public void checkAdAvailable(JSONArray args, CallbackContext callbackContext) {
        boolean available = hasAvailableAd();
        if (callbackContext != null) {
            callbackContext.success(available ? 1 : 0);
        }
    }

    public boolean shouldAutoShow() {

        return this.isAutoShow;
    }

    public boolean hasAvailableAd() {
        if (this.currentAdUnitId == null) return false;
        return AppOpenAdPreloader.isAdAvailable(this.currentAdUnitId);
    }

    public void stopPreloadAndClear(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            isShowingAd = false; 
            if (currentAdUnitId != null) {
                AppOpenAdPreloader.destroy(currentAdUnitId);
                currentAdUnitId = null;
            }
            if (callbackContext != null) {
                callbackContext.success("App Open Preloader stopped and cleared");
            }
        });
    }

    private void fireEvent(String eventName, JSONObject data) {
        if (cordova == null) return;
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
            if (webView != null && webView.getView() != null) {
                webView.loadUrl(js.toString());
            }
        });
    }
}
