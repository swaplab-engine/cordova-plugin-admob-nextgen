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

import java.util.Date;

import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

public class AppOpenAdExecutor {

    private static final String TAG = "AdMobAppOpen";
    private static AppOpenAdExecutor instance;

    private CordovaInterface cordova;
    private CordovaWebView webView;

    private AppOpenAd appOpenAd;

    private boolean isLoadingAd = false;
    private boolean isShowingAd = false;
    private long loadTime = 0;

    private boolean isAutoShow = false;
    private String currentAdUnitId = null;

    private long lastLoadTime = 0;
    private long minLoadInterval = 5000;

    public static synchronized AppOpenAdExecutor getInstance() {
        if (instance == null) {
            instance = new AppOpenAdExecutor();
        }
        return instance;
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void loadAd(JSONArray args, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("SDK not initialized");
            return;
        }
        try {
            JSONObject options = args.optJSONObject(0);
            String adUnitId;

            if (options != null) {
                adUnitId = options.getString("adUnitId");
                if (options.has("isAutoShow")) this.isAutoShow = options.getBoolean("isAutoShow");
                if (options.has("retryInterval")) this.minLoadInterval = options.getLong("retryInterval");
            } else {
                adUnitId = args.getString(0);
                this.isAutoShow = false;
            }

            this.currentAdUnitId = adUnitId;
            loadAdInternal(adUnitId, callbackContext);

        } catch (JSONException e) {
            if (callbackContext != null) callbackContext.error("Invalid Args: " + e.getMessage());
        }
    }

    private void loadAdInternal(String adUnitId, CallbackContext callbackContext) {
        long currentTime = new Date().getTime();

        if (isLoadingAd || isAdAvailable()) {
            if (isAdAvailable()) {
                fireEvent("on.appopen.loaded", null);
                if (callbackContext != null) callbackContext.success("Ad Ready");
            }
            return;
        }

        if ((currentTime - lastLoadTime) < minLoadInterval) {
            if (callbackContext != null) callbackContext.error("Request too fast. Please wait " + minLoadInterval + " ms to prevent invalid traffic.");
            return;
        }

        isLoadingAd = true;
        lastLoadTime = currentTime;

        if (callbackContext != null) {
            PluginResult r = new PluginResult(PluginResult.Status.OK, "Loading started...");
            r.setKeepCallback(true);
            callbackContext.sendPluginResult(r);
        }

        cordova.getActivity().runOnUiThread(() -> {

            AdRequest request = new AdRequest.Builder(adUnitId).build();

            AppOpenAd.load(
                    request,
                    new AdLoadCallback<AppOpenAd>() {
                        @Override
                        public void onAdLoaded(@NonNull AppOpenAd ad) {
                            isLoadingAd = false;
                            appOpenAd = ad;
                            loadTime = new Date().getTime();

                            fireEvent("on.appopen.loaded", null);
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            isLoadingAd = false;

                            try {
                                JSONObject errData = new JSONObject();
                                errData.put("code", loadAdError.getCode());
                                errData.put("message", loadAdError.getMessage());
                                fireEvent("on.appopen.failed.load", errData);
                            } catch (JSONException e) {}
                        }
                    }
            );
        });
    }

    public void showAdIfAvailable(@NonNull Activity activity) {
        if (isShowingAd) return;

        if (!isAdAvailable()) {
            try {
                JSONObject errData = new JSONObject();
                errData.put("message", "Ad not ready or expired");
                fireEvent("on.appopen.failed.show", errData);
            } catch (JSONException e) {}

            if (currentAdUnitId != null) loadAdInternal(currentAdUnitId, null);
            return;
        }

        appOpenAd.setAdEventCallback(new AppOpenAdEventCallback() {

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("value", adValue.getValueMicros());
                    data.put("currency", adValue.getCurrencyCode());
                    data.put("precision", adValue.getPrecisionType());
                    fireEvent("on.appopen.revenue", data);
                } catch (JSONException e) {}
            }

            @Override
            public void onAdShowedFullScreenContent() {
                isShowingAd = true;
                fireEvent("on.appopen.shown", null);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                appOpenAd = null;
                isShowingAd = false;
                fireEvent("on.appopen.dismissed", null);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                appOpenAd = null;
                isShowingAd = false;
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("code", error.getCode());
                    errData.put("message", error.getMessage());
                    fireEvent("on.appopen.failed.show", errData);
                } catch (JSONException e) {}
            }

            @Override
            public void onAdImpression() {
                fireEvent("on.appopen.impression", null);
            }

            @Override
            public void onAdClicked() {
                fireEvent("on.appopen.clicked", null);
            }
        });

        cordova.getActivity().runOnUiThread(() -> {

            if (!AdMobNextGen.isAppInForeground) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("code", "APP_IN_BACKGROUND");
                    errData.put("message", "Ad presentation blocked: Application is currently in the background.");
                    fireEvent("on.appopen.failed.show", errData);
                } catch (JSONException e) {}

                return;
            }

            isShowingAd = true;
            appOpenAd.show(activity);
        });

    }

    public boolean shouldAutoShow() {
        return this.isAutoShow;
    }

    public void setAutoShow(boolean value) {
        this.isAutoShow = value;
    }

    private boolean wasLoadTimeLessThanNHoursAgo(long numHours) {
        long dateDifference = new Date().getTime() - loadTime;
        long numMilliSecondsPerHour = 3600000L;
        return dateDifference < (numMilliSecondsPerHour * numHours);
    }

    private boolean isAdAvailable() {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4);
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
            if (webView != null && webView.getView() != null) webView.loadUrl(js.toString());
        });
    }
}
