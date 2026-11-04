package com.emi.cordova.admob.nextgen;

import static com.emi.cordova.admob.nextgen.AdMobNextGen.isInitialized;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;

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
import com.google.android.libraries.ads.mobile.sdk.common.ExperimentalApi;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAd;
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdOptions;
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdPosition;
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdPresentationScope;
import com.google.android.libraries.ads.mobile.sdk.pip.PictureInPictureAdRequest;

import java.util.Date;

@OptIn(markerClass = ExperimentalApi.class)
public class PictureInPictureExecutor {
    private static final String TAG = "AdMobPip";
    private CordovaInterface cordova;
    private CordovaWebView webView;
    private PictureInPictureAd pipAd;
    private PictureInPictureAdOptions pipOptions;

    private boolean isLoading = false;
    private boolean isAutoShow = false;
    private String currentAdUnitId = "";

    private long lastLoadTime = 0;
    private long minLoadInterval = 5000;

    public PictureInPictureExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void createPictureInPicture(JSONArray args, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("SDK not initialized");
            return;
        }

        try {
            JSONObject options = args.getJSONObject(0);
            String adUnitId = options.getString("adUnitId");

            if (options.has("isAutoShow")) {
                this.isAutoShow = options.getBoolean("isAutoShow");
            } else {
                this.isAutoShow = false;
            }

            if (options.has("retryInterval")) {
                this.minLoadInterval = options.getLong("retryInterval");
            }

            PictureInPictureAdPosition position = PictureInPictureAdPosition.DEFAULT;
            String posString = options.optString("position", "DEFAULT");
            if ("TOP_LEFT".equalsIgnoreCase(posString)) position = PictureInPictureAdPosition.TOP_LEFT;
            else if ("TOP_RIGHT".equalsIgnoreCase(posString)) position = PictureInPictureAdPosition.TOP_RIGHT;
            else if ("BOTTOM_LEFT".equalsIgnoreCase(posString)) position = PictureInPictureAdPosition.BOTTOM_LEFT;
            else if ("BOTTOM_RIGHT".equalsIgnoreCase(posString)) position = PictureInPictureAdPosition.BOTTOM_RIGHT;

            PictureInPictureAdPresentationScope scope = PictureInPictureAdPresentationScope.SCREEN;
            if ("APPLICATION".equalsIgnoreCase(options.optString("presentationScope", "SCREEN"))) {
                scope = PictureInPictureAdPresentationScope.APPLICATION;
            }

            this.pipOptions = new PictureInPictureAdOptions.Builder()
                    .setPosition(position)
                    .setPresentationScope(scope)
                    .build();

            loadPictureInPicture(adUnitId, callbackContext);
        } catch (JSONException e) {
            if(callbackContext != null) callbackContext.error("Invalid JSON Args: " + e.getMessage());
        }
    }

    private void loadPictureInPicture(String adUnitId, CallbackContext requestCallback) {
        long currentTime = new Date().getTime();

        if (isLoading) {

            return;
        }

        if (pipAd != null && this.currentAdUnitId.equals(adUnitId)) {

            if (requestCallback != null) {
                requestCallback.success("Ad already loaded and ready");
            }
            fireEvent("on.pip.loaded", null);
            if (isAutoShow) {
                showPictureInPictureAd();
            }
            return;
        }

        if ((currentTime - lastLoadTime) < minLoadInterval) {

            if (requestCallback != null) requestCallback.error("Request too fast. Please wait " + minLoadInterval + " ms to prevent invalid traffic.");
            return;
        }

        isLoading = true;
        lastLoadTime = currentTime;
        this.currentAdUnitId = adUnitId;

        if (requestCallback != null) {
            PluginResult r = new PluginResult(PluginResult.Status.OK, "Loading started...");
            r.setKeepCallback(true);
            requestCallback.sendPluginResult(r);
        }

        cordova.getThreadPool().execute(() -> {
            PictureInPictureAdRequest request = new PictureInPictureAdRequest.Builder(adUnitId).build();

            PictureInPictureAd.load(
                    request,
                    new AdLoadCallback<PictureInPictureAd>() {
                        @Override
                        public void onAdLoaded(@NonNull PictureInPictureAd ad) {
                            isLoading = false;

                            if (pipAd != null) {
                                pipAd.destroy();
                            }

                            pipAd = ad;

                            setAdEventCallback(pipAd);

                            fireEvent("on.pip.loaded", null);

                            if (isAutoShow) {
                                showPictureInPictureAd();
                            }
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            isLoading = false;

                            try {
                                JSONObject errData = new JSONObject();
                                errData.put("code", loadAdError.getCode());
                                errData.put("message", loadAdError.getMessage());
                                fireEvent("on.pip.failed.load", errData);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
            );
        });
    }

    public void showPictureInPicture(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (pipAd != null) {
                showPictureInPictureAd();
                if(callbackContext != null) callbackContext.success();
            } else {
                if(callbackContext != null) callbackContext.error("Ad not ready");
            }
        });
    }

    private void showPictureInPictureAd() {
        if (pipAd == null) return;

        Activity activity = cordova.getActivity();
        activity.runOnUiThread(() -> {
            if (!AdMobNextGen.isAppInForeground) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("code", "APP_IN_BACKGROUND");
                    errData.put("message", "Ad presentation blocked: Application is currently in the background.");
                    fireEvent("on.pip.failed.show", errData);
                } catch (JSONException e) {}
                return;
            }

            pipAd.show(activity, pipOptions);
        });
    }

    public void hidePictureInPicture(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (pipAd != null) {
                pipAd.hide();
                if(callbackContext != null) callbackContext.success();
            } else {
                if(callbackContext != null) callbackContext.error("No PiP ad to hide");
            }
        });
    }

    public void destroy() {
        cordova.getActivity().runOnUiThread(() -> {
            if (pipAd != null) {
                pipAd.destroy();
                pipAd = null;
            }
        });
    }

    private void setAdEventCallback(PictureInPictureAd ad) {
        ad.setAdEventCallback(new PictureInPictureAdEventCallback() {
            @Override
            public void onAdShown() {
                fireEvent("on.pip.shown", null);
            }

            @Override
            public void onAdHidden() {
                fireEvent("on.pip.hidden", null);
            }

            @Override
            public void onAdImpression() {
                fireEvent("on.pip.impression", null);
            }

            @Override
            public void onAdClicked() {
                fireEvent("on.pip.clicked", null);
            }

            @Override
            public void onAdShowedFullScreenContent() {
                fireEvent("on.pip.opened", null);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                fireEvent("on.pip.closed", null);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("message", error.getMessage());
                    fireEvent("on.pip.failed.show", errData);
                } catch (JSONException e) {}
            }

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("value", adValue.getValueMicros());
                    data.put("currency", adValue.getCurrencyCode());
                    data.put("precision", adValue.getPrecisionType());
                    fireEvent("on.pip.revenue", data);
                } catch (JSONException e) {

                }
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

            String jsCommand = js.toString();

            if (webView != null) {
                webView.loadUrl(jsCommand);
            }
        });
    }
}
