package com.emi.cordova.admob.nextgen;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView; 
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

public class ConsentExecutor {

    private static final String TAG = "AdMobConsent";
    private CordovaInterface cordova;
    private CordovaWebView webView; 
    private ConsentInformation consentInformation;

    public ConsentExecutor(CordovaInterface cordova, CordovaWebView webView) {
        this.cordova = cordova;
        this.webView = webView;
        this.consentInformation = UserMessagingPlatform.getConsentInformation(cordova.getActivity());
    }

    public void requestConsentInfo(JSONArray args, CallbackContext callbackContext) {
            try {
                JSONObject options = args.optJSONObject(0);

                boolean debugMode = false;
                String manualTestDeviceId = "";
                boolean resetConsent = false;
                boolean tagForUnderAgeOfConsent = false;

                boolean tempShowFormIfRequired = true;
                if (options != null) {
                    debugMode = options.optBoolean("debug", false);
                    manualTestDeviceId = options.optString("testDeviceId", "");
                    resetConsent = options.optBoolean("reset", false);

                    if (options.has("tagForUnderAgeOfConsent")) {
                        tagForUnderAgeOfConsent = options.getBoolean("tagForUnderAgeOfConsent");
                    }

                    tempShowFormIfRequired = options.optBoolean("showFormIfRequired", true);
                }

                if (resetConsent) {
                    consentInformation.reset();

                }

                final boolean showFormIfRequired = tempShowFormIfRequired;

                ConsentRequestParameters.Builder paramsBuilder = new ConsentRequestParameters.Builder();
                paramsBuilder.setTagForUnderAgeOfConsent(tagForUnderAgeOfConsent);

                if (debugMode) {
                    ConsentDebugSettings.Builder debugSettingsBuilder = new ConsentDebugSettings.Builder(cordova.getActivity())
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA);

                    if (!manualTestDeviceId.isEmpty()) {
                        debugSettingsBuilder.addTestDeviceHashedId(manualTestDeviceId);

                    } else {
                        String deviceId = getDeviceId();
                        if (deviceId != null) {
                            debugSettingsBuilder.addTestDeviceHashedId(deviceId);

                        }
                    }

                    paramsBuilder.setConsentDebugSettings(debugSettingsBuilder.build());
                }

                ConsentRequestParameters params = paramsBuilder.build();
                Activity activity = cordova.getActivity();

                activity.runOnUiThread(() -> {
                    consentInformation.requestConsentInfoUpdate(
                            activity,
                            params,
                            () -> {

                                fireEvent("on.consent.info.update", null);

                                if (showFormIfRequired) {
                                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                                            activity,
                                            (FormError loadAndShowError) -> {
                                                if (loadAndShowError != null) {

                                                    sendErrorEvent(loadAndShowError);
                                                    callbackContext.error(loadAndShowError.getMessage());
                                                } else {
                                                    fireEvent("on.consent.form.dismissed", null);
                                                    sendConsentStatus(callbackContext);
                                                }
                                            }
                                    );
                                } else {

                                    sendConsentStatus(callbackContext);
                                }

                            },
                            (FormError requestConsentError) -> {

                                sendErrorEvent(requestConsentError);
                                callbackContext.error(requestConsentError.getMessage());
                            }
                    );
                });

            } catch (Exception e) {
                callbackContext.error("Exception: " + e.getMessage());
            }
    }

    public void getTCData(CallbackContext callbackContext) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(cordova.getActivity());
            JSONObject tcData = new JSONObject();

            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                if (entry.getKey().startsWith("IABTCF_")) {
                    tcData.put(entry.getKey(), entry.getValue());
                }
            }

            String purposeConsents = prefs.getString("IABTCF_PurposeConsents", "");
            String purposeLegitimateInterests = prefs.getString("IABTCF_PurposeLegitimateInterests", ""); 
            String vendorConsents = prefs.getString("IABTCF_VendorConsents", "");
            int gdprApplies = prefs.getInt("IABTCF_gdprApplies", -1);

            tcData.put("purposeConsents", purposeConsents);
            tcData.put("purposeLegitimateInterests", purposeLegitimateInterests);
            tcData.put("vendorConsents", vendorConsents);
            tcData.put("gdprApplies", gdprApplies);

            boolean isPersonalizedAllowed = false;
            String statusMessage = "Unknown";

            boolean isAdMobPersonalizedAdsAllowed = false;
            boolean isAdMobNonPersonalizedAdsAllowed = false;
            String adMobConsentStatus = "Unknown";

            if (gdprApplies == -1) {

                statusMessage = "TCF data missing. Consent not yet gathered.";
                adMobConsentStatus = "UNKNOWN";

                isPersonalizedAllowed = false;
                isAdMobPersonalizedAdsAllowed = false;
                isAdMobNonPersonalizedAdsAllowed = false;
            } else if (gdprApplies == 0) {

                isPersonalizedAllowed = true;
                statusMessage = "Not GDPR region. Personalized Ads allowed by default.";

                isAdMobPersonalizedAdsAllowed = true;
                isAdMobNonPersonalizedAdsAllowed = true;
                adMobConsentStatus = "Not GDPR region. AdMob ads allowed by default.";
            } else {

                if (purposeConsents != null && purposeConsents.length() > 0) {
                    char p1 = purposeConsents.charAt(0);
                    if (p1 == '1') {
                        isPersonalizedAllowed = true;
                        statusMessage = "Purpose 1 Granted. Legacy check passed.";
                    } else {
                        isPersonalizedAllowed = false;
                        statusMessage = "Purpose 1 Denied. Legacy check failed.";
                    }
                }

                boolean hasPurpose1 = checkConsent(purposeConsents, 1);
                boolean hasPurpose3 = checkConsent(purposeConsents, 3);
                boolean hasPurpose4 = checkConsent(purposeConsents, 4);

                boolean hasRequiredLI_or_Consent = 
                    hasConsentOrLI(purposeConsents, purposeLegitimateInterests, 2) &&
                    hasConsentOrLI(purposeConsents, purposeLegitimateInterests, 7) &&
                    hasConsentOrLI(purposeConsents, purposeLegitimateInterests, 9) &&
                    hasConsentOrLI(purposeConsents, purposeLegitimateInterests, 10);

                boolean hasVendorGoogle = checkConsent(vendorConsents, 755); 

                if (hasPurpose1 && hasVendorGoogle && hasRequiredLI_or_Consent) {
                    isAdMobNonPersonalizedAdsAllowed = true;

                    if (hasPurpose3 && hasPurpose4) {
                        isAdMobPersonalizedAdsAllowed = true;
                        adMobConsentStatus = "Strict requirements met for Personalized Ads (Purposes 1,3,4 + LI 2,7,9,10 + Vendor 755).";
                    } else {
                        isAdMobPersonalizedAdsAllowed = false;
                        adMobConsentStatus = "Requirements met for Non-Personalized Ads only.";
                    }
                } else {
                    isAdMobPersonalizedAdsAllowed = false;
                    isAdMobNonPersonalizedAdsAllowed = false;
                    adMobConsentStatus = "Insufficient strict consent (Missing P1, Vendor 755, or P2,7,9,10). Limited Ads only.";
                }
            }

            tcData.put("isPersonalizedAllowed", isPersonalizedAllowed);
            tcData.put("statusMessage", statusMessage);

            tcData.put("isAdMobPersonalizedAdsAllowed", isAdMobPersonalizedAdsAllowed);
            tcData.put("isAdMobNonPersonalizedAdsAllowed", isAdMobNonPersonalizedAdsAllowed);
            tcData.put("adMobConsentStatus", adMobConsentStatus);

            callbackContext.success(tcData);
        } catch (Exception e) {
            callbackContext.error("Failed to read TC Data: " + e.getMessage());
        }
    }

    private boolean checkConsent(String consentString, int id) {
        if (consentString == null || consentString.length() < id) {
            return false;
        }
        return consentString.charAt(id - 1) == '1';
    }

    private boolean hasConsentOrLI(String consents, String lis, int id) {
        boolean hasConsent = checkConsent(consents, id);
        boolean hasLI = checkConsent(lis, id);
        return hasConsent || hasLI;
    }

    private String getDeviceId() {
        try {
            Context context = cordova.getActivity();
            @SuppressLint("HardwareIds") String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.isEmpty()) return null;
            return md5(androidId).toUpperCase(Locale.getDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private void sendConsentStatus(CallbackContext callbackContext) {
        try {
            JSONObject result = new JSONObject();
            boolean canRequestAds = consentInformation.canRequestAds();
            result.put("canRequestAds", canRequestAds);

            ConsentInformation.PrivacyOptionsRequirementStatus requirementStatus =
                    consentInformation.getPrivacyOptionsRequirementStatus();
            result.put("privacyOptionsRequirementStatus", requirementStatus.name());
            result.put("isPrivacyOptionsRequired",
                    requirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED);

            result.put("consentStatus", consentInformation.getConsentStatus());

            fireEvent("on.consent.status.change", result);

            if (callbackContext != null) {
                callbackContext.success(result);
            }
        } catch (JSONException e) {
            if (callbackContext != null) callbackContext.error("JSON Error");
        }
    }

    public void canRequestAds(CallbackContext callbackContext) {
        callbackContext.success(consentInformation.canRequestAds() ? 1 : 0);
    }

    public void showPrivacyOptionsForm(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();
        cordova.getActivity().runOnUiThread(() -> {
            UserMessagingPlatform.showPrivacyOptionsForm(
                    activity,
                    (FormError formError) -> {
                        if (formError != null) {
                            sendErrorEvent(formError);
                            callbackContext.error(formError.getMessage());
                        } else {

                            fireEvent("on.consent.form.dismissed", null);
                            sendConsentStatus(callbackContext);
                        }
                    }
            );
        });
    }

    private void sendErrorEvent(FormError error) {
        try {
            JSONObject errData = new JSONObject();
            errData.put("code", error.getErrorCode());
            errData.put("message", error.getMessage());
            fireEvent("on.consent.error", errData);
        } catch (JSONException e) {}
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
            if (webView != null) webView.loadUrl(jsCommand);
        });
    }
}
