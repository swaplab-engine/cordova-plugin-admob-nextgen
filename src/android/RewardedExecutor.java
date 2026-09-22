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
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;

import java.util.Date;

public class RewardedExecutor {

    private static final String TAG = "AdMobRewarded";
    private CordovaInterface cordova;
    private CordovaWebView webView;

    private RewardedAd rewardedAd;

    private boolean isLoading = false;
    private boolean isAutoShow = false;
    private boolean isRewardEarned = false;

    private long lastLoadTime = 0;
    private long minLoadInterval = 5000;

    public RewardedExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void createRewarded(JSONArray args, CallbackContext callbackContext) {
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

            loadRewarded(adUnitId, callbackContext);

        } catch (JSONException e) {
            callbackContext.error("Invalid JSON Args: " + e.getMessage());
        }
    }

    private void loadRewarded(String adUnitId, CallbackContext requestCallback) {
        long currentTime = new Date().getTime();

        if (isLoading) {

            return;
        }

        if (rewardedAd != null) {

            fireEvent("on.rewarded.loaded", null);

            if (isAutoShow) {
                showRewardedAd();
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

            RewardedAd.load(
                    request,
                    new AdLoadCallback<RewardedAd>() {
                        @Override
                        public void onAdLoaded(@NonNull RewardedAd ad) {
                            isLoading = false;
                            rewardedAd = ad;

                            fireEvent("on.rewarded.loaded", null);

                            if (isAutoShow) {
                                showRewardedAd();
                            }
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            isLoading = false;
                            rewardedAd = null;

                            try {
                                JSONObject errData = new JSONObject();
                                errData.put("code", loadAdError.getCode());
                                errData.put("message", loadAdError.getMessage());
                                fireEvent("on.rewarded.failed.load", errData);
                            } catch (JSONException e) {}
                        }
                    }
            );
        });
    }

    public void showRewarded(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (rewardedAd != null) {
                showRewardedAd();
                callbackContext.success();
            } else {
                callbackContext.error("Rewarded ad not ready yet");
            }
        });
    }

    private void showRewardedAd() {
        if (rewardedAd == null) return;

        Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {

            isRewardEarned = false;

            if (!AdMobNextGen.isAppInForeground) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("code", "APP_IN_BACKGROUND");
                    errData.put("message", "Ad presentation blocked: Application is currently in the background.");
                    fireEvent("on.rewarded.failed.show", errData);
                } catch (JSONException e) {}
                return; 
            }

            rewardedAd.setAdEventCallback(new RewardedAdEventCallback() {

                @Override
                public void onAdPaid(@NonNull AdValue adValue) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("value", adValue.getValueMicros());
                        data.put("currency", adValue.getCurrencyCode());
                        data.put("precision", adValue.getPrecisionType());

                        fireEvent("on.rewarded.revenue", data);
                    } catch (JSONException e) {

                    }
                }

                @Override
                public void onAdShowedFullScreenContent() {

                    fireEvent("on.rewarded.shown", null);
                }

                @Override
                public void onAdDismissedFullScreenContent() {

                    rewardedAd = null;
                    isLoading = false;

                    if (!isRewardEarned) {
                        fireEvent("on.rewarded.canceled", null);
                    }

                    fireEvent("on.rewarded.dismissed", null);
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError error) {

                    rewardedAd = null;

                    try {
                        JSONObject errData = new JSONObject();
                        errData.put("message", error.getMessage());
                        fireEvent("on.rewarded.failed.show", errData);
                    } catch (JSONException e) {}
                }

                @Override
                public void onAdImpression() {
                    fireEvent("on.rewarded.impression", null);
                }

                @Override
                public void onAdClicked() {
                    fireEvent("on.rewarded.clicked", null);
                }
            });

            rewardedAd.show(activity, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {

                    isRewardEarned = true;

                    try {
                        JSONObject rewardData = new JSONObject();
                        rewardData.put("amount", rewardItem.getAmount());
                        rewardData.put("type", rewardItem.getType());

                        fireEvent("on.rewarded.earned", rewardData);
                    } catch (JSONException e) {

                    }
                }
            });
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
