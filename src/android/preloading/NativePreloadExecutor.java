package com.emi.cordova.admob.nextgen.preloading;

import static com.emi.cordova.admob.nextgen.AdMobNextGen.isInitialized;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView;

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoadResult;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoadResult.NativeAdSuccess;

public class NativePreloadExecutor {

    private static final String TAG = "AdMobNativePreload";
    private final CordovaInterface cordova;
    private final CordovaWebView webView;

    private NativeAdView nativeAdView;
    private RelativeLayout adContainer;
    private NativeAd mNativeAd;

    private boolean isPreloaderActive = false;
    private String currentAdUnitId;
    private boolean isAutoShow = false;
    private long lastShowTime = 0;
    private long minShowInterval = 5000;

    private boolean isOverlapping = true;
    private boolean isCapacitor = false;
    private boolean isCordova15 = false;
    private String currentPreset = "custom";
    private int currentAdHeightPixels = 0;
    private int reqX = 0, reqY = 0, reqW = 300, reqH = 300;

    private boolean isNativeVisible = false;
    private int requestGeneration = 0; 

    private int systemSafeTop = 0;
    private int systemSafeBottom = 0;

    public NativePreloadExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
    }

    public void startPreload(JSONArray args, CallbackContext callbackContext) {
        if (!isInitialized) {
            if (callbackContext != null) callbackContext.error("SDK not initialized");
            return;
        }

        try {
            JSONObject options = args.getJSONObject(0);
            String adUnitId = options.getString("adUnitId");

            if (isPreloaderActive && adUnitId.equals(currentAdUnitId)) {

                parseLayoutOptions(options);

                if (options.has("retryInterval")) {
                    this.minShowInterval = options.getLong("retryInterval");
                }
                if (options.has("isAutoShow")) {
                    this.isAutoShow = options.getBoolean("isAutoShow");
                }

                if (this.isAutoShow) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {

                        showPolledAd(args, null);
                    });
                }

                if (callbackContext != null) callbackContext.success("Native Preloader already active, options updated.");
                return;
            }

            parseLayoutOptions(options);

            if (options.has("retryInterval")) {
                this.minShowInterval = options.getLong("retryInterval");
            }
            if (options.has("isAutoShow")) {
                this.isAutoShow = options.getBoolean("isAutoShow");
            }

            int bufferSize = 1;
            if (options.has("bufferSize")) {
                bufferSize = Math.max(1, Math.min(options.getInt("bufferSize"), 3));
            }

            this.currentAdUnitId = adUnitId;
            this.isPreloaderActive = true;

            final int finalBufferSize = bufferSize;

            cordova.getActivity().runOnUiThread(() -> {
                VideoOptions videoOptions = new VideoOptions.Builder().setStartMuted(true).build();
                List<NativeAd.NativeAdType> types = new ArrayList<>();
                types.add(NativeAd.NativeAdType.NATIVE);

                NativeAdRequest adRequest = new NativeAdRequest.Builder(adUnitId, types)
                        .setVideoOptions(videoOptions)
                        .build();

                PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest, finalBufferSize);

                PreloadCallback preloadCallback = new PreloadCallback() {
                    @Override
                    public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {

                        try {
                            JSONObject data = new JSONObject();
                            data.put("adUnitId", adUnitId);
                            data.put("source", "preloader");
                            fireEvent("on.native.loaded", data);
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
                            err.put("source", "preloader");
                            fireEvent("on.native.failed.load", err);
                        } catch (JSONException ignored) {}
                    }

                    @Override
                    public void onAdsExhausted(@NonNull String preloadId) {

                        fireEvent("on.native.preload.exhausted", null);
                    }
                };

                NativeAdPreloader.start(adUnitId, preloadConfig, preloadCallback);
                if (callbackContext != null) callbackContext.success("Native Preloader Started");
            });

        } catch (JSONException e) {
            isPreloaderActive = false;
            if (callbackContext != null) callbackContext.error(e.getMessage());
        }
    }

    public void showPolledAd(JSONArray args, CallbackContext callbackContext) {
        if (currentAdUnitId == null || currentAdUnitId.isEmpty() || !isPreloaderActive) {
            if (callbackContext != null) callbackContext.error("Native Preloader engine is not running.");
            return;
        }

        if (args != null && args.length() > 0) {
            try {
                JSONObject options = args.getJSONObject(0);
                parseLayoutOptions(options); 
            } catch (JSONException ignored) {}
        }

        if (mNativeAd != null && nativeAdView != null) {
            cordova.getActivity().runOnUiThread(() -> {

                calculateDimensions();

                float density = cordova.getActivity().getResources().getDisplayMetrics().density;
                int finalW = (int) (reqW * density);
                int finalH = (int) (reqH * density);

                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(finalW, finalH);
                if ("banner_top".equalsIgnoreCase(currentPreset)) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                } else if ("modal_center".equalsIgnoreCase(currentPreset)) {
                    params.addRule(RelativeLayout.CENTER_IN_PARENT);
                } else {
                    params.leftMargin = (int) (reqX * density);
                    params.topMargin = (int) (reqY * density);
                }

                nativeAdView.setLayoutParams(params);
                nativeAdView.setVisibility(View.VISIBLE); 

                if (adContainer != null) {
                    adContainer.setVisibility(View.VISIBLE); 
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        adContainer.requestApplyInsets(); 
                    }
                }

                isNativeVisible = true;
                updateWebViewMargins(); 

                if (callbackContext != null) callbackContext.success("Native Ad Restored/Repositioned");
            });

            return; 
        }

        long currentTime = new Date().getTime();
        if ((currentTime - lastShowTime) < minShowInterval) {
            if (callbackContext != null) callbackContext.error("Request too fast. Wait " + minShowInterval + "ms");
            return;
        }

        requestGeneration++;
        final int currentGen = requestGeneration;

        cordova.getActivity().runOnUiThread(() -> {
            if (currentGen != requestGeneration) return; 

            NativeAdLoadResult result = NativeAdPreloader.pollAd(currentAdUnitId);

            if (!(result instanceof NativeAdSuccess)) {
                if (callbackContext != null) callbackContext.error("Native Pool Empty");
                return;
            }

            NativeAd ad = ((NativeAdSuccess) result).getAd();
            this.lastShowTime = currentTime;

            calculateDimensions();

            NativeAdView pendingView = buildNativeAdView(ad, reqX, reqY, reqW, reqH);

            if (adContainer == null) {
                adContainer = new RelativeLayout(cordova.getActivity());
                ViewGroup.LayoutParams containerParams = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                cordova.getActivity().addContentView(adContainer, containerParams);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    adContainer.setOnApplyWindowInsetsListener((v, insets) -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            android.graphics.Insets sysInsets = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                            systemSafeTop = sysInsets.top;
                            systemSafeBottom = sysInsets.bottom;
                        } else {
                            systemSafeTop = insets.getSystemWindowInsetTop();
                            systemSafeBottom = insets.getSystemWindowInsetBottom();
                        }

                        if (isCordova15) {
                            if ("banner_top".equalsIgnoreCase(currentPreset)) {
                                adContainer.setPadding(0, systemSafeTop, 0, 0);
                            } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
                                adContainer.setPadding(0, 0, 0, systemSafeBottom);
                            } else {
                                adContainer.setPadding(0, 0, 0, 0);
                            }
                        } else {
                            if (nativeAdView != null && nativeAdView.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                                RelativeLayout.LayoutParams viewParams = (RelativeLayout.LayoutParams) nativeAdView.getLayoutParams();
                                if ("banner_top".equalsIgnoreCase(currentPreset)) {
                                    viewParams.topMargin = systemSafeTop;
                                    viewParams.bottomMargin = 0;
                                } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
                                    viewParams.bottomMargin = systemSafeBottom;
                                    viewParams.topMargin = 0;
                                }
                                nativeAdView.setLayoutParams(viewParams);
                            }
                        }
                        updateWebViewMargins();
                        return insets;
                    });
                    adContainer.requestApplyInsets();
                }
            }

            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) pendingView.getLayoutParams();
            adContainer.addView(pendingView, layoutParams);
            adContainer.bringToFront();

            if (nativeAdView != null) {
                adContainer.removeView(nativeAdView);
                nativeAdView.destroy();
            }
            if (mNativeAd != null && mNativeAd != ad) {
                mNativeAd.destroy();
            }

            nativeAdView = pendingView;
            mNativeAd = ad;
            isNativeVisible = true;

            setupEventCallback(ad);
            updateWebViewMargins();

            if (callbackContext != null) callbackContext.success("Native Ad Shown from Pool");
        });
    }

    public void checkAdAvailable(JSONArray args, CallbackContext callbackContext) {
        if (currentAdUnitId == null || currentAdUnitId.isEmpty()) {
            if (callbackContext != null) callbackContext.success(0);
            return;
        }
        cordova.getActivity().runOnUiThread(() -> {

            if (mNativeAd != null) {
                if (callbackContext != null) callbackContext.success(1);
                return;
            }

            boolean isAvailable = NativeAdPreloader.isAdAvailable(currentAdUnitId);
            if (callbackContext != null) {
                callbackContext.success(isAvailable ? 1 : 0);
            }
        });
    }

    public void hideNativeAd(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (!isNativeVisible) {
                if (callbackContext != null) callbackContext.success();
                return;
            }

            isNativeVisible = false;
            isAutoShow = false;

            if (adContainer != null) {
                adContainer.setVisibility(View.GONE);
            }
            if (nativeAdView != null) {
                nativeAdView.setVisibility(View.GONE);
            }

            if (!isOverlapping) {
                updateWebViewMargins();
            }

            if (callbackContext != null) callbackContext.success("Native Ad Hidden");
        });
    }

    public void removeNativeAd() {
        cordova.getActivity().runOnUiThread(() -> {
            requestGeneration++; 

            isNativeVisible = false;

            if (nativeAdView != null) {
                nativeAdView.destroy();
                if (adContainer != null) {
                    adContainer.removeView(nativeAdView);
                }
                nativeAdView = null;
            }

            if (mNativeAd != null) {
                mNativeAd.destroy();
                mNativeAd = null;
            }

            updateWebViewMargins();
        });
    }

    public void stopPreloadAndClear(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            isPreloaderActive = false;
            removeNativeAd();

            if (currentAdUnitId != null) {
                NativeAdPreloader.destroy(currentAdUnitId);
                currentAdUnitId = null;
            }

            if (adContainer != null) {
                if (adContainer.getParent() != null) {
                    ((ViewGroup) adContainer.getParent()).removeView(adContainer);
                }
                adContainer.removeAllViews();
                adContainer = null;
            }

            if (callbackContext != null) callbackContext.success("Native Preloader stopped and cleared");
        });
    }

    private void parseLayoutOptions(JSONObject options) {
        if (options.has("view")) this.currentPreset = options.optString("view", "custom");
        if (options.has("isOverlapping")) this.isOverlapping = options.optBoolean("isOverlapping", true);
        if (options.has("isCapacitor")) this.isCapacitor = options.optBoolean("isCapacitor", false);
        if (options.has("isCordova15")) this.isCordova15 = options.optBoolean("isCordova15", false);

        this.reqX = options.optInt("x", 0);
        this.reqY = options.optInt("y", 0);
        this.reqW = options.optInt("width", 300);
        this.reqH = options.optInt("height", 300);
    }

    private void calculateDimensions() {
        DisplayMetrics metrics = cordova.getActivity().getResources().getDisplayMetrics();
        float density = metrics.density;
        int screenWidthDp = (int) (metrics.widthPixels / density);
        int screenHeightDp = (int) (metrics.heightPixels / density);

        if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
            reqH = 120;
            reqW = screenWidthDp;
            reqX = 0;
            reqY = screenHeightDp - reqH;
        } else if ("banner_top".equalsIgnoreCase(currentPreset)) {
            reqH = 120;
            reqW = screenWidthDp;
            reqX = 0;
            reqY = 0;
        } else if ("modal_center".equalsIgnoreCase(currentPreset)) {
            reqW = screenWidthDp - 40;
            reqH = 350;
            reqX = 20;
            reqY = (screenHeightDp - reqH) / 2;
        }
    }

    private NativeAdView buildNativeAdView(NativeAd nativeAd, int x, int y, int width, int height) {
        float density = cordova.getActivity().getResources().getDisplayMetrics().density;

        int finalX = (int) (x * density);
        int finalY = (int) (y * density);
        int finalW = (int) (width * density);
        int finalH = (int) (height * density);

        this.currentAdHeightPixels = finalH;
        boolean isSmallMode = (height < 150);

        NativeAdView newNativeAdView = new NativeAdView(cordova.getActivity());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(finalW, finalH);

        if ("banner_top".equalsIgnoreCase(currentPreset)) {
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        } else if ("modal_center".equalsIgnoreCase(currentPreset)) {
            params.addRule(RelativeLayout.CENTER_IN_PARENT);
        } else {
            params.leftMargin = finalX;
            params.topMargin = finalY;
        }

        newNativeAdView.setLayoutParams(params);
        newNativeAdView.setBackgroundColor(Color.WHITE);
        newNativeAdView.setElevation(8f);
        newNativeAdView.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));

        LinearLayout mainLayout = new LinearLayout(cordova.getActivity());
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headerLayout = new LinearLayout(cordova.getActivity());
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView iconView = new ImageView(cordova.getActivity());
        if (nativeAd.getIcon() != null) {
            iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    (int)(40*density), (int)(40*density));
            iconParams.setMargins(0, 0, (int)(8*density), 0);
            headerLayout.addView(iconView, iconParams);
        }

        LinearLayout textContainer = new LinearLayout(cordova.getActivity());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textContainerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);

        TextView headlineView = new TextView(cordova.getActivity());
        headlineView.setText(nativeAd.getHeadline());
        headlineView.setTypeface(null, Typeface.BOLD);
        headlineView.setTextColor(Color.BLACK);
        headlineView.setMaxLines(1);
        headlineView.setEllipsize(TextUtils.TruncateAt.END);
        textContainer.addView(headlineView);

        TextView adBadge = new TextView(cordova.getActivity());
        adBadge.setText("Ad");
        adBadge.setTextSize(10);
        adBadge.setTextColor(Color.WHITE);
        adBadge.setBackgroundColor(0xFFFCC133);
        adBadge.setPadding(5, 0, 5, 0);

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = (int)(2*density);
        textContainer.addView(adBadge, badgeParams);

        headerLayout.addView(textContainer, textContainerParams);
        mainLayout.addView(headerLayout);

        MediaView mediaView = new MediaView(cordova.getActivity());
        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);

        if (isSmallMode) {
            mediaParams.weight = 0;
            mediaParams.height = 0;
            mediaView.setVisibility(View.GONE);
        } else {
            mediaParams.weight = 1;
            mediaParams.topMargin = (int)(10*density);
            mediaParams.bottomMargin = (int)(10*density);
            mediaView.setVisibility(View.VISIBLE);
        }

        mainLayout.addView(mediaView, mediaParams);

        TextView bodyView = new TextView(cordova.getActivity());
        bodyView.setText(nativeAd.getBody());
        bodyView.setMaxLines(2);
        bodyView.setEllipsize(TextUtils.TruncateAt.END);
        bodyView.setTextSize(12);
        bodyView.setTextColor(Color.DKGRAY);
        mainLayout.addView(bodyView);

        Button ctaView = new Button(cordova.getActivity());
        ctaView.setText(nativeAd.getCallToAction());
        ctaView.setBackgroundColor(0xFF4285F4);
        ctaView.setTextColor(Color.WHITE);
        ctaView.setAllCaps(true);
        if (isSmallMode) {
            ctaView.setTextSize(12);
            ctaView.setPadding(0,0,0,0);
        }
        mainLayout.addView(ctaView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        newNativeAdView.addView(mainLayout);

        newNativeAdView.setIconView(iconView);
        newNativeAdView.setHeadlineView(headlineView);
        newNativeAdView.setBodyView(bodyView);
        newNativeAdView.setCallToActionView(ctaView);
        newNativeAdView.registerNativeAd(nativeAd, mediaView);

        return newNativeAdView;
    }

    private void setupEventCallback(NativeAd nativeAd) {
        nativeAd.setAdEventCallback(new NativeAdEventCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.native.shown", data);
                } catch (JSONException ignored) {}
            }
            @Override
            public void onAdDismissedFullScreenContent() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.native.dismissed", data);
                } catch (JSONException ignored) {}
            }
            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError error) {
                try {
                    JSONObject errData = new JSONObject();
                    errData.put("message", error.getMessage());
                    errData.put("source", "preloader");
                    fireEvent("on.native.show.failed", errData);
                } catch (JSONException ignored) {}
            }
            @Override
            public void onAdImpression() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.native.impression", data);
                } catch (JSONException ignored) {}
            }
            @Override
            public void onAdClicked() {
                try {
                    JSONObject data = new JSONObject();
                    data.put("source", "preloader");
                    fireEvent("on.native.clicked", data);
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
                    fireEvent("on.native.revenue", data);
                } catch (JSONException ignored) {}
            }
        });
    }

    private void updateWebViewMargins() {
        if (webView == null || webView.getView() == null) return;

        View webViewView = webView.getView();
        ViewGroup.LayoutParams lp = webViewView.getLayoutParams();

        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) lp;

            if (isCapacitor) {
                int screenHeightInPx = getScreenHeightInPx();
                if (!isNativeVisible || isOverlapping) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.topMargin = 0;
                    params.bottomMargin = 0;
                } else {
                    if ("banner_top".equalsIgnoreCase(currentPreset)) {
                        params.topMargin = currentAdHeightPixels;
                        params.bottomMargin = 0;
                        params.height = screenHeightInPx - currentAdHeightPixels;
                    } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
                        int webViewHeight = screenHeightInPx - currentAdHeightPixels;
                        params.height = webViewHeight;
                        params.topMargin = 0;
                    } else {
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        params.topMargin = 0;
                        params.bottomMargin = 0;
                    }
                }
                webViewView.setTranslationY(0);
            } else if (isCordova15) {
                webViewView.setTranslationY(0);
                if (!isNativeVisible || isOverlapping) {
                    params.setMargins(0, systemSafeTop, 0, systemSafeBottom);
                } else {
                    if ("banner_top".equalsIgnoreCase(currentPreset)) {
                        params.setMargins(0, systemSafeTop + currentAdHeightPixels, 0, systemSafeBottom);
                    } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
                        params.setMargins(0, systemSafeTop, 0, systemSafeBottom + currentAdHeightPixels);
                    } else {
                        params.setMargins(0, systemSafeTop, 0, systemSafeBottom);
                    }
                }
            } else {
                if ("banner_top".equalsIgnoreCase(currentPreset)) {
                    params.setMargins(0, 0, 0, 0);
                    if (isNativeVisible && !isOverlapping) {
                        float shift = (float) (currentAdHeightPixels + systemSafeTop);
                        webViewView.setTranslationY(shift);
                    } else {
                        webViewView.setTranslationY(0);
                    }
                } else if ("banner_bottom".equalsIgnoreCase(currentPreset)) {
                    webViewView.setTranslationY(0);
                    if (!isNativeVisible || isOverlapping) {
                        params.setMargins(0, 0, 0, 0);
                    } else {
                        int finalBottom = currentAdHeightPixels + systemSafeBottom;
                        params.setMargins(0, 0, 0, finalBottom);
                    }
                } else {
                    webViewView.setTranslationY(0);
                    params.setMargins(0, 0, 0, 0);
                }
            }
            webViewView.setLayoutParams(params);
            webViewView.requestLayout();
        }
    }

    private int getScreenHeightInPx() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowMetrics windowMetrics = cordova.getActivity().getWindowManager().getCurrentWindowMetrics();
            android.graphics.Insets insets = windowMetrics.getWindowInsets().getInsets(android.view.WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.heightPixels;
        }
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
