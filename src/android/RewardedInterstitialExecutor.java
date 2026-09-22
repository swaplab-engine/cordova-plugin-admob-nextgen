package com.emi.cordova.admob.nextgen;

import static com.emi.cordova.admob.nextgen.AdMobNextGen.isInitialized;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue; 
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback;

public class RewardedInterstitialExecutor {

    private static final String TAG = "AdMobRewInter";
    private CordovaInterface cordova;
    private CordovaWebView webView;

    private RewardedInterstitialAd mRewardedInterstitialAd;

    private boolean isAutoShow = false;

    private boolean isLoading = false;
    private long lastLoadTime = 0;
    private long minLoadInterval = 5000;
    private boolean isRewardEarned = false;

    public RewardedInterstitialExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void createRewardedInterstitial(JSONArray args, CallbackContext callbackContext) {
        if (!isInitialized) {
            callbackContext.error("SDK not initialized");
            return;
        }
        try {
            JSONObject options = args.getJSONObject(0);
            String adUnitId = options.getString("adUnitId");
            this.isAutoShow = options.optBoolean("isAutoShow", false);

            if (options.has("retryInterval")) {
                this.minLoadInterval = options.getLong("retryInterval");
            }

            long currentTime = new Date().getTime();

            if (isLoading) {

                callbackContext.error("Ad is loading");
                return;
            }

            if (mRewardedInterstitialAd != null) {

                fireEvent("on.rewardedInter.loaded", null);
                if (callbackContext != null) {
                    callbackContext.success("Ad already loaded");
                }

                if (isAutoShow) {
                    showRewardedInterstitial(null);
                }

                return;
            }

            if ((currentTime - lastLoadTime) < minLoadInterval) {

                if (callbackContext != null) callbackContext.error("Request too fast. Please wait " + minLoadInterval + " ms to prevent invalid traffic.");
                callbackContext.error("Request too fast");
                return;
            }

            isLoading = true;
            lastLoadTime = currentTime;

            cordova.getActivity().runOnUiThread(() -> {
                loadAd(adUnitId, callbackContext);
            });

        } catch (JSONException e) {
            callbackContext.error("Invalid Args: " + e.getMessage());
        }
    }

    private void loadAd(String adUnitId, CallbackContext callbackContext) {
        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();

        RewardedInterstitialAd.load(
                adRequest,
                new AdLoadCallback<RewardedInterstitialAd>() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                        isLoading = false;
                        mRewardedInterstitialAd = ad;

                        fireEvent("on.rewardedInter.loaded", null);

                        if (isAutoShow) {
                            showRewardedInterstitial(callbackContext);
                        } else {
                            callbackContext.success("Ad Loaded");
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoading = false;

                        try {
                            JSONObject err = new JSONObject();
                            err.put("code", loadAdError.getCode());
                            err.put("message", loadAdError.getMessage());
                            fireEvent("on.rewardedInter.failed.load", err);
                        } catch (JSONException e) {}

                        callbackContext.error(loadAdError.getMessage());
                    }
                }
        );
    }

    public void showRewardedInterstitial(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (mRewardedInterstitialAd != null) {

                isRewardEarned = false;

                if (!AdMobNextGen.isAppInForeground) {
                    try {
                        JSONObject errData = new JSONObject();
                        errData.put("code", "APP_IN_BACKGROUND");
                        errData.put("message", "Ad presentation blocked: Application is currently in the background.");
                        fireEvent("on.rewardedInter.failed.show", errData);
                    } catch (JSONException e) {}
                    return; 
                }

                mRewardedInterstitialAd.setAdEventCallback(new RewardedInterstitialAdEventCallback() {

                    @Override
                    public void onAdPaid(@NonNull AdValue adValue) {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("value", adValue.getValueMicros());
                            data.put("currency", adValue.getCurrencyCode());
                            data.put("precision", adValue.getPrecisionType());

                            fireEvent("on.rewardedInter.revenue", data);
                        } catch (JSONException e) {

                        }
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {

                        fireEvent("on.rewardedInter.shown", null);
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {

                        mRewardedInterstitialAd = null;

                        if (!isRewardEarned) {
                            fireEvent("on.rewardedInter.canceled", null);
                        }

                        fireEvent("on.rewardedInter.dismissed", null);
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {

                        mRewardedInterstitialAd = null;

                        try {
                            JSONObject err = new JSONObject();
                            err.put("message", error.getMessage());
                            fireEvent("on.rewardedInter.failed.show", err);
                        } catch (JSONException e) {}
                    }

                    @Override
                    public void onAdImpression() {
                        fireEvent("on.rewardedInter.impression", null);
                    }

                    @Override
                    public void onAdClicked() {
                        fireEvent("on.rewardedInter.clicked", null);
                    }
                });

                Activity activity = cordova.getActivity();
                mRewardedInterstitialAd.show(activity, rewardItem -> {

                    isRewardEarned = true;

                    try {
                        JSONObject data = new JSONObject();
                        data.put("amount", rewardItem.getAmount());
                        data.put("type", rewardItem.getType());
                        fireEvent("on.rewardedInter.earned", data);
                    } catch (JSONException e) {}
                });

                if (callbackContext != null) callbackContext.success("Ad Shown");

            } else {

                if (callbackContext != null) callbackContext.error("Ad Not Ready");
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
