var App_Open_ID;
var Banner_ID;
var PiP_ID;
var NativeAd_ID;
var Interstitial_ID;
var Rewarded_ID;
var Rewarded_Interstitial_ID;

let isDeviceready = false;
let isPlatformIOS = false;


// Ad format	Demo ad unit ID
// https://developers.google.com/admob/android/test-ads
// https://developers.google.com/admob/ios/test-ads


/* https://support.google.com/admob/answer/9493252?hl=en
Best practice when using ad original ID unit, 
the app must be uploaded to the play store or app store, 
and you must upload it from there, 
otherwise you may be subject to ad serving restrictions, 
if it happens often, it is possible that your admob account will be permanently disabled.
*/


if (window.cordova && window.cordova.platformId === 'ios') {
    App_Open_ID = 'ca-app-pub-3940256099942544/5575463023';
    Banner_ID = 'ca-app-pub-3940256099942544/2435281174';
    PiP_ID = 'ca-app-pub-3940256099942544/8810945611';
    NativeAd_ID = 'ca-app-pub-3940256099942544/3986624511';
    Interstitial_ID = 'ca-app-pub-3940256099942544/4411468910';
    Rewarded_ID = 'ca-app-pub-3940256099942544/1712485313';
    Rewarded_Interstitial_ID = 'ca-app-pub-3940256099942544/6978759866';
    
    isPlatformIOS = true;
} else {
    // Assume Android
    App_Open_ID = 'ca-app-pub-3940256099942544/9257395921';
    Banner_ID = 'ca-app-pub-3940256099942544/9214589741';
    PiP_ID = 'ca-app-pub-3940256099942544/9657123429';
    NativeAd_ID = 'ca-app-pub-3940256099942544/2247696110';
    Interstitial_ID = 'ca-app-pub-3940256099942544/1033173712';
    Rewarded_ID = 'ca-app-pub-3940256099942544/5224354917';
    Rewarded_Interstitial_ID = 'ca-app-pub-3940256099942544/5354046379';
}

//////////////////////
// cordova deviceready
/////////////////////
document.addEventListener("deviceready", function () {
    isDeviceready = true;
    if (window.logToScreen) window.logToScreen("Device Ready! Starting AdMob sequence...");

    // 1. SET GLOBAL CONFIGURATION
    
    // Optional: Mute ads globally
    // admobNextGen.setAppVolume(0.5);
    // admobNextGen.setAppMuted(true);

    // ===============================================
    // THE PRIVACY WATERFALL (UMP -> ATT -> SDK INIT)
    // ===============================================
    
    // STEP 1: UMP (GDPR)
    function startPrivacyFlow() {
        if (window.logToScreen) window.logToScreen("Requesting UMP Consent...");
        
        admobNextGen.requestConsentInfo({
            debug: true, // true | false | Default/Production: false
            reset: false, // true | false | Default/Production: false
            tagForUnderAgeOfConsent: false, // true | false | Default: false
        }, function () {
            console.log("Consent Info Ready.");
            // UMP Success, continue to ATT or Init
            checkATTFlow();
        }, function (err) {
            console.error("Consent Error", err);
            if (window.logToScreen) window.logToScreen("UMP Error: " + err, "warn");
            // Failed UMP? Keep going, don't let the app crash
            checkATTFlow();
        });
    }

    // STEP 2: ATT (iOS IDFA)
    function checkATTFlow() {
        if (isPlatformIOS && admobNextGen.requestTrackingAuthorization) {
            if (window.logToScreen) window.logToScreen("iOS detected: Requesting ATT...", "event");
            
            admobNextGen.requestTrackingAuthorization(
                function (status) {
                    console.log("ATT Status: " + status);
                    if (window.logToScreen) window.logToScreen("ATT Status: " + status, "event");
                    
                    // Continue SDK Initialization
                    startSdk();
                },
                function (err) {
                    console.warn("ATT Request Failed", err);
                    startSdk();
                }
            );
        } else {
            // If Android, just init
            startSdk();
        }
    }

    // STEP 3: INITIALIZE SDK
    function startSdk() {
        if (window.logToScreen) window.logToScreen("Initializing AdMob SDK...");
        
        admobNextGen.initialize({
            maxAdContentRating: "", // 'G' | 'PG' | 'T' | 'MA' | Default: ""
            tagForChildDirectedTreatment: false, // true | false | Default: null
            tagForUnderAgeOfConsent: false, // true | false | Default: null
           // isNativeValidatorDisabled: false // optional param for: cordova-plugin-admob-nextgen-native
        }, function () {
            console.log(">>> AdMob SDK Initialized & Ready <<<");
            if (window.logToScreen) window.logToScreen("✅ SDK READY TO SERVE ADS", "success");
            
            // Check Smart Data (Optional after Init)
            checkTCData();
            
        }, function (err) {
            console.error("SDK Init Failed", err);
            if (window.logToScreen) window.logToScreen("SDK Init Failed: " + err, "warn");
        });
    }

    // ===============================================
    // https://support.google.com/admanager/answer/9805023?hl=en#consent-policies
    function checkTCData() {
        admobNextGen.getTCData(function(tcData) {
            
            if (window.logToScreen) window.logToScreen(tcData.adMobConsentStatus);
            
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
    }

    // Listeners for UI changes based on consent
    document.addEventListener('on.consent.status.change', (e) => {
        var data = e.data || e;
        console.log("Consent Status Changed", data);
        if (window.logToScreen) window.logToScreen("Consent State Changed", "event");
    });

    // Start Stream
    startPrivacyFlow();

}, false);
