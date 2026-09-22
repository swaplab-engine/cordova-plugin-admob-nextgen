package com.emi.cordova.admob.nextgen;

import com.emi.cordova.admob.nextgen.preloading.AppOpenAdPreloadExecutor;
import com.emi.cordova.admob.nextgen.preloading.BannerPreloadExecutor;
import com.emi.cordova.admob.nextgen.preloading.InterstitialPreloadExecutor;
import com.emi.cordova.admob.nextgen.preloading.RewardedPreloadExecutor;
import com.emi.cordova.admob.nextgen.preloading.RewardedInterstitialPreloadExecutor;

import android.app.Activity;
import android.app.Application; 
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build; 
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets; 
import android.view.WindowInsetsController; 

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AdMobNextGen extends CordovaPlugin {

    private static final String TAG = "AdMobNextGen";
    private BannerExecutor bannerExecutor;
    private AppOpenAdExecutor appOpenAdExecutor;
    private InterstitialExecutor interstitialExecutor;
    private RewardedExecutor rewardedExecutor;
    private RewardedInterstitialExecutor rewardedInterstitialExecutor;
    private ConsentExecutor consentExecutor;
    private GlobalSettingsExecutor globalSettingsExecutor;
    private NativeExecutor nativeExecutor;

    private BannerPreloadExecutor bannerPreloadExecutor;
    private AppOpenAdPreloadExecutor appOpenAdPreloadExecutor;
    private InterstitialPreloadExecutor interstitialPreloadExecutor;
    private RewardedPreloadExecutor rewardedPreloadExecutor;
    private RewardedInterstitialPreloadExecutor rewardedInterstitialPreloadExecutor;

    private boolean isNativeValidatorDisabled = true;
    public static boolean isInitialized = false;
    public static boolean isAppInForeground = true;

    @Override
    public void pluginInitialize() {
        super.pluginInitialize();

        applyAdMobAPI35WorkaroundIfNeeded(cordova.getActivity().getApplication());

        globalSettingsExecutor = new GlobalSettingsExecutor(cordova);
        consentExecutor = new ConsentExecutor(cordova, webView);
        appOpenAdExecutor = AppOpenAdExecutor.getInstance();
        appOpenAdExecutor.initialize(cordova, webView);

        bannerExecutor = new BannerExecutor(cordova, webView);
        interstitialExecutor = new InterstitialExecutor(cordova, webView);
        rewardedExecutor = new RewardedExecutor(cordova, webView);
        rewardedInterstitialExecutor = new RewardedInterstitialExecutor(cordova, webView);

        nativeExecutor = new NativeExecutor(cordova, webView);

        appOpenAdPreloadExecutor = new AppOpenAdPreloadExecutor(cordova, webView);
        bannerPreloadExecutor = new BannerPreloadExecutor(cordova, webView);
        interstitialPreloadExecutor = new InterstitialPreloadExecutor(cordova, webView);
        rewardedPreloadExecutor = new RewardedPreloadExecutor(cordova, webView);
        rewardedInterstitialPreloadExecutor = new RewardedInterstitialPreloadExecutor(cordova, webView);

    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if ("setAppVolume".equals(action)) {
            globalSettingsExecutor.setAppVolume(args, callbackContext);
            return true;
        }
        if ("setAppMuted".equals(action)) {
            globalSettingsExecutor.setAppMuted(args, callbackContext);
            return true;
        }
        if ("setRequestConfiguration".equals(action)) {
            globalSettingsExecutor.setRequestConfiguration(args, callbackContext);
            return true;
        }

        if ("requestConsentInfo".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                consentExecutor.requestConsentInfo(args, callbackContext);
            });
            return true;
        }
        if ("showPrivacyOptionsForm".equals(action)) {
            consentExecutor.showPrivacyOptionsForm(callbackContext);
            return true;
        }
        if ("canRequestAds".equals(action)) {
            consentExecutor.canRequestAds(callbackContext);
            return true;
        }
        if ("getTCData".equals(action)) {
            consentExecutor.getTCData(callbackContext);
            return true;
        }

        if ("initialize".equals(action)) {
            cordova.getThreadPool().execute(() -> {
                this.initializeSDK(args, callbackContext);
            });
            return true;
        }

        if ("createBanner".equals(action)) {
            bannerExecutor.createBanner(args, callbackContext);
            return true;
        }
        if ("hideBanner".equals(action)) {
            bannerExecutor.hideBanner(callbackContext);
            return true;
        }
        if ("showBanner".equals(action)) {
            bannerExecutor.showBanner(callbackContext);
            return true;
        }

        if ("removeBanner".equals(action)) {
            bannerExecutor.destroy();
            callbackContext.success();
            return true;
        }

        if ("loadAppOpenAd".equals(action)) {
            if (appOpenAdExecutor != null) {
                appOpenAdExecutor.loadAd(args, callbackContext);
            }
            return true;
        }

        if ("showAppOpenAd".equals(action)) {
            if (appOpenAdExecutor != null) {
                appOpenAdExecutor.showAdIfAvailable(cordova.getActivity());
            }
            callbackContext.success();
            return true;
        }

        if ("setAppOpenAutoShow".equals(action)) {
            boolean shouldShow = args.getBoolean(0);
            if (appOpenAdExecutor != null) {
                appOpenAdExecutor.setAutoShow(shouldShow);
            }
            callbackContext.success();
            return true;
        }

        if ("createNativeAd".equals(action)) {
            nativeExecutor.createNativeAd(args, callbackContext);
            return true;
        }

        if ("removeNativeAd".equals(action)) {
            nativeExecutor.removeNativeAd();
            callbackContext.success();
            return true;
        }

        if ("createInterstitial".equals(action)) {
            interstitialExecutor.createInterstitial(args, callbackContext);
            callbackContext.success();
            return true;
        }
        if ("showInterstitial".equals(action)) {
            interstitialExecutor.showInterstitial(callbackContext);
            return true;
        }

        if ("createRewarded".equals(action)) {
            rewardedExecutor.createRewarded(args, callbackContext);
            return true;
        }

        if ("showRewarded".equals(action)) {
            rewardedExecutor.showRewarded(callbackContext);
            return true;
        }

        if ("createRewardedInterstitial".equals(action)) {
            rewardedInterstitialExecutor.createRewardedInterstitial(args, callbackContext);
            return true;
        }

        if ("showRewardedInterstitial".equals(action)) {
            rewardedInterstitialExecutor.showRewardedInterstitial(callbackContext);
            return true;
        }

        if ("startAppOpenPreload".equals(action)) {
            appOpenAdPreloadExecutor.startPreload(args, callbackContext);
            return true;
        }
        if ("showPreloadedAppOpenAd".equals(action)) {
            appOpenAdPreloadExecutor.showPolledAd(args, callbackContext);
            return true;
        }
        if ("isAppOpenAdAvailable".equals(action)) {
            appOpenAdPreloadExecutor.checkAdAvailable(args, callbackContext);
            return true;
        }

        if ("startBannerPreload".equals(action)) {
            bannerPreloadExecutor.startPreload(args, callbackContext);
            return true;
        }
        if ("showPreloadedBanner".equals(action)) {
            bannerPreloadExecutor.showPolledAd(args, callbackContext);
            return true;
        }
        if ("stopBannerPreload".equals(action)) {
            bannerPreloadExecutor.stopPreloadAndClear();
            callbackContext.success();
            return true;
        }

        if ("hideBannerPreload".equals(action)) {
            bannerPreloadExecutor.hideBanner(callbackContext);
            return true;
        }

        if ("startInterstitialPreload".equals(action)) {
            interstitialPreloadExecutor.startPreload(args, callbackContext);
            return true;
        }
        if ("showPreloadedInterstitial".equals(action)) {
            interstitialPreloadExecutor.showPolledAd(args, callbackContext);
            return true;
        }
        if ("isInterstitialAdAvailable".equals(action)) {
            interstitialPreloadExecutor.checkAdAvailable(args, callbackContext);
            return true;
        }
        if ("stopInterstitialPreload".equals(action)) {
            interstitialPreloadExecutor.stopPreloadAndClear(callbackContext);
            return true;
        }

        if ("startRewardedPreload".equals(action)) {
            rewardedPreloadExecutor.startPreload(args, callbackContext);
            return true;
        }

        if ("showPreloadedRewarded".equals(action)) {
            rewardedPreloadExecutor.showPolledAd(args, callbackContext);
            return true;
        }

        if ("isRewardedAdAvailable".equals(action)) {
            rewardedPreloadExecutor.checkAdAvailable(args, callbackContext);
            return true;
        }

        if ("stopRewardedPreload".equals(action)) {
            rewardedPreloadExecutor.stopPreloadAndClear(callbackContext);
            return true;
        }

        if ("startRewardedInterstitialPreload".equals(action)) {
            rewardedInterstitialPreloadExecutor.startPreload(args, callbackContext);
            return true;
        }

        if ("showPreloadedRewardedInterstitial".equals(action)) {
            rewardedInterstitialPreloadExecutor.showPolledAd(args, callbackContext);
            return true;
        }

        if ("isRewardedInterstitialAdAvailable".equals(action)) {
            rewardedInterstitialPreloadExecutor.checkAdAvailable(args, callbackContext);
            return true;
        }

        if ("stopRewardedInterstitialPreload".equals(action)) {
            rewardedInterstitialPreloadExecutor.stopPreloadAndClear(callbackContext);
            return true;
        }

        return false;
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        isAppInForeground = true;
        if (appOpenAdExecutor != null && appOpenAdExecutor.shouldAutoShow()) {
            appOpenAdExecutor.showAdIfAvailable(cordova.getActivity());
        }
        if (appOpenAdPreloadExecutor != null && appOpenAdPreloadExecutor.shouldAutoShow()) {
            if (appOpenAdPreloadExecutor.hasAvailableAd()) {
                appOpenAdPreloadExecutor.showPolledAd(null, null);
            }
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        isAppInForeground = false;
    }

    private void initializeSDK(JSONArray args, CallbackContext callbackContext) {
    String appId = getAppIdFromManifest();
    if (appId == null) {
        callbackContext.error("AdMob App ID missing");
        return;
    }

        try {
            InitializationConfig.Builder initConfigBuilder = new InitializationConfig.Builder(appId);

            JSONObject configJson = args.optJSONObject(0);

            if (configJson != null) {
                RequestConfiguration requestConfig = GlobalSettingsExecutor.buildRequestConfiguration(configJson);
                initConfigBuilder.setRequestConfiguration(requestConfig);

                this.isNativeValidatorDisabled = configJson.optBoolean("isNativeValidatorDisabled", true);
            } else {
                this.isNativeValidatorDisabled = false;
            }

            if (this.isNativeValidatorDisabled) {
                initConfigBuilder.setNativeValidatorDisabled();
            }

            InitializationConfig config = initConfigBuilder.build();

            MobileAds.initialize(
                    cordova.getActivity(),
                    config,
                    initializationStatus -> {
                        isInitialized = true;
                        cordova.getActivity().runOnUiThread(() -> callbackContext.success("Initialized"));
                    }
            );
        } catch (Exception e) {
            cordova.getActivity().runOnUiThread(() -> callbackContext.error(e.getMessage()));
        }
}

    private String getAppIdFromManifest() {
        try {
            Context context = cordova.getActivity().getApplicationContext();
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            return bundle.getString("com.google.android.gms.ads.APPLICATION_ID");
        } catch (Exception e) { return null; }
    }

    @Override
    public void onDestroy() {
        if (bannerExecutor != null){ 
            bannerExecutor.destroy();
        }
        if (nativeExecutor != null) {
            nativeExecutor.destroy();
        }
        super.onDestroy();
    }

    public static void applyAdMobAPI35WorkaroundIfNeeded(Application application) {

        if (Build.VERSION.SDK_INT < 35) {
            return;
        }

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                applyAPI35WorkaroundToActivity(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                applyAPI35WorkaroundToActivity(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static void applyAPI35WorkaroundToActivity(Activity activity) {

        if (!"com.google.android.gms.ads.AdActivity".equals(activity.getClass().getName())) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = activity.getWindow().getInsetsController();
            if (c != null) {

                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
            }
        }
    }
}
