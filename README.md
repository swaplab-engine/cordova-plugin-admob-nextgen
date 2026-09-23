# Cordova AdMob Next-Gen Plugin

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://www.android.com)
[![iOS](https://img.shields.io/badge/Platform-iOS-lightgrey?logo=apple)](https://www.apple.com/ios)
[![AdMob Next Gen](https://img.shields.io/badge/SDK-Google%20Mobile%20Ads%20Next--Gen-blue)](https://ads-developers.googleblog.com/2026/01/announcing-google-mobile-ads-next-gen.html)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

[![NPM version](https://img.shields.io/npm/v/cordova-plugin-admob-nextgen.svg)](https://www.npmjs.com/package/cordova-plugin-admob-nextgen)
[![Downloads](https://img.shields.io/npm/dm/cordova-plugin-admob-nextgen.svg)](https://www.npmjs.com/package/cordova-plugin-admob-nextgen)
[![License](https://img.shields.io/npm/l/cordova-plugin-admob-nextgen.svg)](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/blob/main/LICENSE)

**The Ultimate AdMob Monetization Solution for Cordova/Capacitor/Framework7.**

This plugin is a complete **rewrite and re-architecture** of the classic AdMob integration, built specifically for the **[Google Mobile Ads Next Generation SDK](https://ads-developers.googleblog.com/2026/01/announcing-google-mobile-ads-next-gen.html)**. 

It moves away from legacy implementations to modern `SurfaceControl`, optimized layouts, and background threading, ensuring maximum performance, stability, and revenue.

> **Maintained by the original creator of [EMI-INDO/emi-indo-cordova-plugin-admob](https://github.com/EMI-INDO/emi-indo-cordova-plugin-admob).** This is not just an update; it is a brand new engine designed for 2026 and beyond.

---
> (NEW) ⚡️ Next-Gen special capacitor: [capacitor-admob-nextgen](https://github.com/swaplab-engine/capacitor-admob-nextgen)
---

## 🚀 Why Next-Gen?

Google has introduced a fundamental shift in how ads are rendered on Android. 

This plugin aligns perfectly with those changes to solve the most critical issues developers face today:

1. **ANR Error Elimination:** 
   The legacy SDK is notorious for causing ANR (Application Not Responding) errors in the Google Play Console. This Next-Gen architecture fully resolves these threading bottlenecks. 
   *(See [Google's Next-Gen Announcement](https://ads-developers.googleblog.com/2026/01/announcing-google-mobile-ads-next-gen.html))*

2. **Lifecycle Stability:** 
   Solves the notorious issue where Full-Screen Ads (Interstitial, Rewarded, App Open) would crash or disappear when the app goes to the background and returns to the foreground.

3. **Smart Runtime Engine (Android 15 Edge-to-Edge):** 
   Intelligently detects and handles Android 14 (API 35) vs Android 15 (API 36) edge-to-edge conditions at runtime.
   * Automatically secures the "Close/X" buttons on full-screen ads.
   * When using the banner parameter `isOverlapping: false`, it intelligently calculates the system safe bottom insets to push the WebView content up perfectly.
   * Ensures neither the ad nor your app UI is hidden behind the system navigation bar.
   * Requires **zero** manual configuration from the user. 
   *(See [Release v1.3.9-beta.1](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/releases/tag/v1.3.9-beta.1))*

4. **Cordova & Capacitor Agnostic:** 
   Intelligently identifies whether the runtime environment is Cordova or Capacitor (including deep native Java logic adjustments) to provide seamless compatibility.

5. **Clean Architecture:** 
   Written from scratch using the official [Next Gen SDK Examples](https://github.com/googleads/gma-next-gen-sdk-android-examples), ensuring long-term compliance with Google Policies.


## 💰 Mission: Revenue Maximization

This plugin is designed with one goal: 

**increasing your eCPM and fill rates**. 

 It incorporates advanced formats and technologies that are proven to outperform standard implementations.

### 1. True Next-Gen Speed: Ad Preloading API ⚡
This is the **real** Next-Gen feature, this plugin implements the specific Preload API methods: `startBannerPreload` and `showPreloadedBanner`.
* **Zero Latency:** Instead of loading an ad when you need it, the plugin maintains a "pool" of ready-to-show ads in the background.
* **Instant Show:** When you call `showPreloadedBanner`, the ad appears instantly (0ms delay) from the pool.

### 2. Collapsible Banners (The Revenue Booster)
Standard banners are often ignored by users. 

This plugin supports **Collapsible Banners** natively.
* **Impact:** Typically delivers **3-5% higher revenue** than standard or large adaptive banners.
* **Mechanism:** Shows a larger ad initially (anchored top/bottom) that can be collapsed by the user, drastically increasing visibility and click-through rates (CTR).

### 3. Native Advanced Overlay (The Banner Killer)
Move beyond simple 320x50 banners. 

The Native Overlay feature allows you to render high-performance Native Ads that look like system notifications.
* **Impact:** Can yield **5-10% higher revenue** compared to standard banners due to higher advertiser demand for Native assets.
* **Smart Templates:**
    * `banner_bottom`: A sleek, notification-style ad docked at the bottom.
    * `banner_top`: Docked at the top.
    * `modal_center`: A popup-style native ad.
* **Policy Safe:** Includes an `isOverlapping` parameter.
* You can choose to overlay the ad (float) or push the webview content (safe layout), preventing accidental clicks.


## 🛡️ Safety & Reliability

This plugin prioritizes the safety of your AdMob account and the stability of your app.

* **Smart Throttling (`retryInterval`)**: Solves the dreaded "high requests, low impressions" (Invalid Traffic / IVT) issue.

* This parameter acts as a strict anti-spam safeguard.
* If an ad load is accidentally triggered repeatedly by an app logic error (e.g., game loop, tick, or scroll event) before the ad surfaces and is viewed by the user, the `retryInterval` automatically blocks the redundant requests.
  
  **Without this safeguard, you face two fatal problems:**
  1. **Banner Ad Flickering:** Ads are constantly being destroyed and redrawn before becoming visible.
  2. **Account Ban:** Aggressively pulling ads (spamming requests without impressions) is a serious violation of AdMob's Invalid Traffic policy.

* **Background Thread Loading**: All ad requests are dispatched on background threads, ensuring your app's UI never freezes.


---


## Cordova/Capacitor/Framework7 AdMob Next-Gen: 

### Installation & Usage Guide

---
> Fastest test (APK Debug): **[⚡ With github action ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/discussions/4)** (Optional)
---

### 📦 Current SDK Versions (Maintained & Up-to-Date)

This plugin is regularly updated to support the latest standards.

| Component | Platform | Version | Release Notes |
| :--- | :--- | :--- | :--- |
| **GMA Next-Gen SDK** | Android | **1.4.0** | [View Notes](https://developers.google.com/admob/android/next-gen/rel-notes) |
| **UMP SDK** | Android | **4.0.0** | [View Notes](https://developers.google.com/admob/android/privacy/release-notes) |
| **Mobile Ads SDK** | iOS | **13.10.0** | [View Notes](https://developers.google.com/admob/ios/rel-notes) |
| **UMP SDK** | iOS | **3.1.0** | [View Notes](https://developers.google.com/ad-manager/mobile-ads-sdk/ios/privacy/download) |

### 🎉 View all plugin release notes
- 👉 [**Releases notes**](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/releases)

---


## 1. Cordova or Framework7

### Option A: Via CLI
Install the plugin directly using the Cordova CLI, You must provide your AdMob App ID.

    cordova plugin add cordova-plugin-admob-nextgen --save --variable APP_ID_ANDROID="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" --variable APP_ID_IOS="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"

### Option B: Via config.xml
Add this to your `config.xml` to restore the plugin automatically.

    <plugin name="cordova-plugin-admob-nextgen">
        <variable name="APP_ID_ANDROID" value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
        <variable name="APP_ID_IOS" value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
    </plugin>

---

## (Optional)

### Supporting Mediation: **[⚡ admob-mediation-suite ](https://github.com/swaplab-engine/admob-mediation-suite)** (Optional)
### Supporting Native ads: **[⚡ cordova-plugin-admob-nextgen-native ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen-native)** (Optional)
### New COPPA : **[⚡ cordova-plugin-play-age-signals ](https://github.com/swaplab-engine/cordova-plugin-play-age-signals)** (Optional)

---

## For Capacitor users
**[⚡ Capacitor Integration: Automated Setup for AdMob Next Gen ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/discussions/3)**.

**[⚡ AdMob Next Gen - Starter Capacitor Templates 🚀 ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen-template)**.

---

**[⚡ FULL Cordova - simple example 🚀 ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/tree/main/simple-example/www/js)**.

## 2. Configuration & Initialization (CRITICAL)

**IMPORTANT:** Configure Global Settings and handle Privacy Consent (UMP) **BEFORE** initializing the SDK.

    // Optional: Mute ads globally
    admobNextGen.setAppVolume(0.5);
    admobNextGen.setAppMuted(true);

### Step 1: Request Consent (UMP) & Initialize

    // 1. Request Consent Information
    admobNextGen.requestConsentInfo({
        debug: false, // Set true for testing
        reset: false,
        tagForUnderAgeOfConsent: false
    }, function() {
        console.log("Consent Info Ready.");
        startSdk();

    }, function(err) {
        console.error("Consent Error", err);
        startSdk();
    });


    
    // --- Consent Events ---
    document.addEventListener('on.consent.status.change', (data) => {
        console.log(data);
    });
    on.consent.info.update
    on.consent.form.dismissed
    on.consent.status.change  (obj)
    on.consent.error  (obj)
    
   


    // 3. Initialize the SDK
    function startSdk() {
        admobNextGen.initialize({
            maxAdContentRating: 'G',  // 'G' | 'PG' | 'T' | 'MA' | ""
            tagForChildDirectedTreatment: false, // true | false | null
            tagForUnderAgeOfConsent: false, // true | false | null
            // isNativeValidatorDisabled: false // optional param for: cordova-plugin-admob-nextgen-native
        }, function() {
            console.log(">>> AdMob SDK Initialized & Ready <<<");
        }, function(err) {
            console.error("SDK Init Failed", err);
        });
    }


       // 4. Privacy options
        admobNextGen.showPrivacyOptionsForm(function() {
            startSdk();
        }, function(err) {
            startSdk();
        });


### App Tracking Transparency (ATT / IDFA) - iOS Only
For iOS 14+, Apple requires you to prompt the user before tracking their IDFA. 
The plugin provides manual control over this prompt so you can display it at the right time in your app's lifecycle.

```
function checkAppTracking() {
    admobNextGen.requestTrackingAuthorization(
        function(status) {
            console.log("ATT Status: " + status); 
            // Returns: 'AUTHORIZED', 'DENIED', 'NOT_DETERMINED', 'RESTRICTED'
            
            // Regardless of the ATT choice, initialize the SDK
            startSdk();
        },
        function(err) {
            console.warn("ATT Request Failed", err);
            startSdk();
        }
    );
}

// You can also silently check the status anytime:
// admobNextGen.getTrackingAuthorizationStatus(success, error);
```   

### Step 2: Check Consent Status (Smart Analysis)
- Understanding Consent Flags (`getTCData`)

*(See [Publisher integration with the IAB Europe TCF](https://support.google.com/admanager/answer/9805023?hl=en#consent-policies))*
> 

When you call `admobNextGen.getTCData()`, the plugin returns a JSON object containing raw IAB TCF strings alongside pre-calculated boolean flags. To comply with AdMob's strict policies in GDPR regions, This plugin provide specific AdMob flags alongside the legacy flag.

| Flag | Description | GDPR Requirements Checked |
| :--- | :--- | :--- |
| `isAdMobPersonalizedAdsAllowed` | **(Recommended)** Returns `true` if the user has granted all necessary consents for AdMob to serve **Personalized Ads**. | Consent for Purposes 1, 3, 4 **AND** (Consent or Legitimate Interest) for Purposes 2, 7, 9, 10 **AND** Vendor 755 (Google). |
| `isAdMobNonPersonalizedAdsAllowed` | **(Recommended)** Returns `true` if the user has granted the minimum consent required for AdMob to serve **Non-Personalized Ads**. | Consent for Purpose 1 **AND** (Consent or Legitimate Interest) for Purposes 2, 7, 9, 10 **AND** Vendor 755 (Google). |
| `isPersonalizedAllowed` | **(Legacy)** Preserved for backward compatibility with older app versions. **Not recommended for strict AdMob policy compliance.** | Consent for Purpose 1 only. |

> **Note:** If the user is outside the GDPR region (e.g., USA, Indonesia), all of the ready-to-use flags above will return `true` by default.

### A: Standard Usage (Ready-to-use Flags)
For most users, relying on the built-in boolean flags is the easiest way to ensure AdMob compliance without parsing complex strings.

```javascript
// Request TC Data
admobNextGen.getTCData(function(tcData) {

    
    console.log(tcData.adMobConsentStatus);
    
    if (tcData.isAdMobPersonalizedAdsAllowed) {
        console.log("Consent met: AdMob can serve Personalized Ads.");
        // Proceed to load ads...
    } 
    else if (tcData.isAdMobNonPersonalizedAdsAllowed) {
        console.log("Consent met: AdMob will serve Non-Personalized Ads (Limited Ads).");
        // Proceed to load ads...
    } 
    else {
        console.log("Consent insufficient: AdMob cannot serve any ads.");
        // You might want to show the privacy options form again here
    }

});
```

#### B: Advanced Usage (Raw Data Control)

Advanced users who want full control over the consent logic can completely ignore the built-in boolean flags. The plugin now dynamically extracts and exposes **ALL standard IAB TCF v2 keys** directly from the device's local storage. 

This means you have full access to every `IABTCF_` prefixed key (such as `IABTCF_PurposeConsents`, `IABTCF_VendorLegitimateInterests`, `IABTCF_SpecialFeaturesOptIns`, etc.), allowing you to manipulate and build custom enum conditions based on your own specific architecture requirements.

```javascript
admobNextGen.getTCData(function(tcData) {
    
    // Ignore the built-in flags and parse the raw data yourself.
    // Note: IAB TCF strings are 1-indexed, but JavaScript strings are 0-indexed.
    // Index 0 represents Purpose 1, Index 2 represents Purpose 3, etc.
    
    // You can use the standard IABTCF_ keys extracted dynamically by the plugin
    const purposes = tcData.IABTCF_PurposeConsents || "";
    const lis = tcData.IABTCF_PurposeLegitimateInterests || "";
    const vendors = tcData.IABTCF_VendorConsents || "";
    const vendorLIs = tcData.IABTCF_VendorLegitimateInterests || ""; 

    // const tcs = tcData.IABTCF_TCString;
    // const region = tcData.IABTCF_gdprApplies;
    
    // Custom logic example: Check if Purpose 1 and Purpose 3 are explicitly granted
    const hasPurpose1 = purposes.charAt(0) === '1';
    const hasPurpose3 = purposes.charAt(2) === '1';
    
    // Check if Vendor 755 (Google) Consent OR Legitimate Interest is granted (Index 754)
    const hasGoogleVendorConsent = vendors.charAt(754) === '1';
    const hasGoogleVendorLI = vendorLIs.charAt(754) === '1';
    
    if (hasPurpose1 && hasPurpose3 && (hasGoogleVendorConsent || hasGoogleVendorLI)) {
        console.log("Custom advanced condition met!");
        // Your custom ad loading logic here
    } else {
        console.log("Custom advanced condition failed.");
    }

});
```

---

## 3. Banner Ads

Supports **Adaptive**, **Standard**, and **Collapsible** banners.

### Create Banner

    admobNextGen.createBanner({
        adUnitId: 'ca-app-pub-xxx/xxx',
        position: 'bottom',       // 'top' or 'bottom'
        size: 'ADAPTIVE',         // 'BANNER', 'LARGE_BANNER', 'MEDIUM_RECTANGLE', 'ADAPTIVE', 'FULL_BANNER', 'LEADERBOARD'
        isOverlapping: false,     // true = Overlay, false = Push Webview
        collapsible: true,        // true = Enable Collapsible Format (High Revenue)
        retryInterval: 5000,      // Anti-spam delay (ms)
        isAutoShow: true
    });

### New large banner adaptive size

>  new banner size min plugin version: 1.2+

- LARGE_LANDSCAPE_ANCHORED_ADAPTIVE
- LARGE_PORTRAIT_ANCHORED_ADAPTIVE
- CURRENT_ORIENTATION_INLINE_ADAPTIVE
- LARGE_ANCHORED_ADAPTIVE
- PORTRAIT_INLINE_ADAPTIVE


### Banner Methods

    admobNextGen.showBanner();
    admobNextGen.hideBanner();
    admobNextGen.removeBanner();

### Banner Events

    document.addEventListener('on.banner.load', (data) => {
        console.log("Banner Loaded: " + data.width + "x" + data.height);
        console.log("Collapsible: " + data.isCollapsible);
    });
    document.addEventListener('on.banner.failed', (err) => console.error(err));
    document.addEventListener('on.banner.revenue', (data) => {
        console.log("Revenue: " + data.value + " " + data.currency);
    });
    document.addEventListener('on.banner.clicked', () => console.log("Clicked"));
    document.addEventListener('on.banner.impression', () => console.log("Impression"));

    // else
    on.banner.opened
    on.banner.closed
    on.banner.failed.show
    on.banner.refreshed
    on.banner.refresh.failed

---

## 4. Native Ads (Advanced Overlay) - Android Only

- Recommended: **[⚡ cordova-plugin-admob-nextgen-native ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen-native)** (Android | IOS)

High-performance native templates.

### Example A: Using Templates (Recommended)
    // Android Only
    admobNextGen.createNativeAd({
        adUnitId: 'ca-app-pub-xxx/xxx',
        view: 'banner_bottom',    // Presets: 'banner_top', 'banner_bottom', 'modal_center'
        isOverlapping: false,     // true = Overlay, false = Push Content
        retryInterval: 5000
    });

### Example B: Custom Position (Manual Coordinates)
    // Android Only
    admobNextGen.createNativeAd({
        adUnitId: 'ca-app-pub-xxx/xxx',
        view: 'custom',
        x: 20,              // X Position in dp
        y: 100,             // Y Position in dp
        width: 300,         // Width in dp
        height: 300,        // Height in dp
        isOverlapping: true // Typically true for custom floating ads
    });

### Native Methods & Events
    // Android Only
    admobNextGen.removeNativeAd();

    document.addEventListener('on.native.loaded', () => console.log("Native Loaded"));
    document.addEventListener('on.native.revenue', (data) => console.log("Revenue:", data.value));

    // else
    on.native.shown
    on.native.failed
    on.native.dismissed
    on.native.show.failed  (obj)
    on.native.impression
    on.native.clicked


---

## 6. App Open Ads

Supports Auto-Resume logic.

### Load App Open

    admobNextGen.loadAppOpenAd({
        adUnitId: 'ca-app-pub-xxx/xxx',
        isAutoShow: true, // Automatically show when app resumes from background
        retryInterval: 5000
    });

### Manual Show

    admobNextGen.showAppOpenAd();

### App Open Events

    document.addEventListener('on.appopen.revenue', (data) => console.log(data));
    document.addEventListener('on.appopen.dismissed', () => console.log("Closed"));

    // else
    on.appopen.loaded
    on.appopen.failed.load (obj)
    on.appopen.failed.show (obj)
    on.appopen.revenue (obj)
    on.appopen.shown
    on.appopen.dismissed
    on.appopen.failed.show (obj)
    on.appopen.impression
    on.appopen.clicked


---

## 7. Interstitial Ads

### Load & Show

    admobNextGen.createInterstitial({
        adUnitId: 'ca-app-pub-xxx/xxx',
        isAutoShow: true, // Show immediately when loaded
        retryInterval: 5000
    });

    // Manual Show
    // admobNextGen.showInterstitial();

### Events

    document.addEventListener('on.interstitial.revenue', (data) => console.log(data));
    document.addEventListener('on.interstitial.dismissed', () => console.log("Closed"));

    // else
    on.interstitial.loaded
    on.interstitial.failed.load (obj)
    on.interstitial.failed.show (obj)
    on.interstitial.revenue (obj)
    on.interstitial.shown
    on.interstitial.dismissed
    on.interstitial.failed.show (obj)
    on.interstitial.impression
    on.interstitial.clicked

---

## 8. Rewarded Video

### Load & Show

    admobNextGen.createRewarded({
        adUnitId: 'ca-app-pub-xxx/xxx',
        retryInterval: 5000,  // Anti-spam interval
        isAutoShow: false     // Default false (User must opt-in)
    });

    // Show manually when ready
    admobNextGen.showRewarded();

### Events

    document.addEventListener('on.rewarded.earned', (data) => {
        console.log("User Earned: " + data.amount + " " + data.type);
    });
    document.addEventListener('on.rewarded.revenue', (data) => console.log(data));

    // else
    on.rewarded.loaded
    on.rewarded.failed.load (obj)
    on.rewarded.failed.show (obj)
    on.rewarded.revenue (obj)
    on.rewarded.shown
    on.rewarded.canceled
    on.rewarded.dismissed
    on.rewarded.failed.show (obj)
    on.rewarded.impression
    on.rewarded.clicked

---

## 9. Rewarded Interstitial

Auto-showing rewarded format (no opt-in).

### Load & Show

    admobNextGen.createRewardedInterstitial({
        adUnitId: 'ca-app-pub-xxx/xxx',
        isAutoShow: true,
        retryInterval: 5000 // Anti-spam interval
    });

### Events

    document.addEventListener('on.rewardedInter.earned', (data) => console.log(data));
    document.addEventListener('on.rewardedInter.revenue', (data) => console.log(data));

    // else
    on.rewardedInter.loaded
    on.rewardedInter.failed.load (obj)
    on.rewardedInter.failed.show (obj)
    on.rewardedInter.revenue (obj)
    on.rewardedInter.shown
    on.rewardedInter.canceled
    on.rewardedInter.dismissed
    on.rewardedInter.failed.show (obj)
    on.rewardedInter.impression
    on.rewardedInter.clicked


---


## 10. Preloader Engine (Android Only - Next-Gen SDK)

The AdMob Next-Gen SDK introduces a powerful background Preloader Engine that caches ads in a buffer pool, enabling zero-latency ad rendering.

> **⚠️ IMPORTANT WARNING & BEST PRACTICES:**
> * **Android Only:** The Preloader engine is currently an exclusive feature of the Android Next-Gen SDK.
> * **Strict Method Separation:** NEVER mix Classic methods with Preloader methods to avoid logic conflicts. 
>   * If you use `createBanner`, you MUST use `showBanner`. 
>   * If you use `startBannerPreload`, you MUST use `showPreloadedBanner`.
> * **Cross-Platform (iOS) Consideration:** Events are unified. A `source` flag (`data.source === "preloader"`) is injected into the event data on Android. However, it is highly recommended to maintain a single unified logic flow. Relying strictly on the `source === "preloader"` condition will cause issues on iOS, as iOS relies entirely on the Classic implementation.

### Method Mapping (Classic vs Preloader)

| Ad Format | Start Preload | Show Polled Ad | Other Preload Methods |
| :--- | :--- | :--- | :--- |
| **Banner** | `startBannerPreload(obj)` | `showPreloadedBanner()` | `stopBannerPreload()`, `hideBannerPreload()` |
| **App Open** | `startAppOpenPreload(obj)` | `showPreloadedAppOpenAd()` | `isAppOpenAdAvailable()` |
| **Interstitial** | `startInterstitialPreload(obj)` | `showPreloadedInterstitial()` | `stopInterstitialPreload()`, `isInterstitialAdAvailable()` |
| **Rewarded** | `startRewardedPreload(obj)` | `showPreloadedRewarded()` | `stopRewardedPreload()`, `isRewardedAdAvailable()` |
| **Rewarded Interstitial** | `startRewardedInterstitialPreload(obj)` | `showPreloadedRewardedInterstitial()` | `stopRewardedInterstitialPreload()`, `isRewardedInterstitialAdAvailable()` |

### Implementation Example

- [⚡ Example preloading ](https://github.com/swaplab-engine/cordova-plugin-admob-nextgen/tree/main/simple-example/www/js/preloading)

**1. Start the Preloader**
Run this once (e.g., after SDK initialization). The engine will automatically maintain the buffer in the background.

```javascript
admobNextGen.startRewardedPreload({
    adUnitId: 'ca-app-pub-xxx/xxx',
    bufferSize: 2,         // (Optional) Number of ads to cache. Default: 1 max 3
    isAutoShow: false,     // (Optional) Show immediately when the first ad is ready
    retryInterval: 5000    // (Optional) Anti-spam protection in ms
});
```

**2. Show the Preloaded Ad**
Call this when the user interacts with your app. It will render instantly from the local buffer.

```javascript
admobNextGen.showPreloadedRewarded();
```

### Unified Events & Preloader Exclusives

To streamline development, the Preloader triggers the **exact same lifecycle events** as the Classic mode (e.g., `on.rewarded.loaded`, `on.rewarded.dismissed`). You only need to write one event listener for both modes.

**Preloader Exclusive Event:**
The only event strictly unique to the Preloader is the `exhausted` event. This fires when the buffer pool is completely empty and the SDK stops trying to fetch new ads.

```javascript
// Example: Unique Preloader Event
document.addEventListener('on.rewarded.exhausted', () => {
    console.warn("Background ad buffer is empty!");
});
```

**Optional: Checking the Event Source**
If you need to execute specific conditional logic for the preloader, you can check `data.source`. *(Remember: This flag is Android-only).*

```javascript
document.addEventListener('on.rewarded.loaded', (e) => {
    let data = e.data || e;
    
    if (data && data.source === "preloader") {
        console.log("Ad loaded via Android Preloader Engine");
    } else {
        console.log("Ad loaded via Classic Engine (or iOS)");
    }
});
```

---


## ⚖️ Revenue Policy

**No Ad-Sharing. No Hidden Fees.**

Unlike many other free plugins, this project is clean:
* **0% Revenue Share:** This plugin do not inject our own Ad Unit IDs into your traffic.
* **100% Control:** Every impression and click goes directly to your AdMob account.
* **Transparent Source:** The code is open source. You can verify that no third-party SDKs or hidden backdoors exist.

---

## ❤️ Support the Project

This plugin is developed and maintained in my free time. 
If it saved you hours of work, consider supporting the development!

<a href="https://www.buymeacoffee.com/emi.indo" target="_blank">
  <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" >
</a>

Your support helps me keep the dependencies updated and the cleaner script running smoothly.


<p align="center">
  Built with ❤️ by <b>Swaplab Engine</b> (formerly EMI-INDO).
</p>