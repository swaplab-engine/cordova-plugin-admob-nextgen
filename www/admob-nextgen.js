var exec = require('cordova/exec');

var AdMobNextGen = {

    /*
     Created by EMI INDO So on Frb 10, 2026
    */

    setAppVolume: function (volume, success, error) {
        exec(success, error, 'AdMobNextGen', 'setAppVolume', [volume]);
    },

    setAppMuted: function (muted, success, error) {
        exec(success, error, 'AdMobNextGen', 'setAppMuted', [muted]);
    },

    setRequestConfiguration: function (config, success, error) {
        config = config || {};
        exec(success, error, 'AdMobNextGen', 'setRequestConfiguration', [config]);
    },

    requestConsentInfo: function (options, success, error) {
        options = options || {};
        exec(success, error, 'AdMobNextGen', 'requestConsentInfo', [options]);
    },

    showPrivacyOptionsForm: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showPrivacyOptionsForm', []);
    },

    getTCData: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'getTCData', []);
    },

    requestTrackingAuthorization: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'requestTrackingAuthorization', []);
    },

    getTrackingAuthorizationStatus: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'getTrackingAuthorizationStatus', []);
    },

    // Inisialisasi SDK
    initialize: function (config, success, error) {
        var args = [];
        var successCb = success;
        var errorCb = error;

        if (typeof config === 'function') {
            successCb = config;
            errorCb = success;
        } else if (config && typeof config === 'object') {
            args = [config];
        }

        exec(successCb, errorCb, 'AdMobNextGen', 'initialize', args);
    },

    createBanner: function (options, success, error) {
        var isCapacitorEnvironment = typeof window.Capacitor !== 'undefined';
        var isCordova15Environment = false;
        if (!isCapacitorEnvironment && typeof cordova !== 'undefined' && cordova.platformVersion) {
            var majorVersion = parseInt(cordova.platformVersion.split('.')[0], 10);
            if (majorVersion >= 15) {
                isCordova15Environment = true;
            }
        }
        options = options || {};
        options.isCapacitor = isCapacitorEnvironment;
        options.isCordova15 = isCordova15Environment;

        exec(success, error, 'AdMobNextGen', 'createBanner', [options]);
    },

    hideBanner: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'hideBanner', []);
    },

    showBanner: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showBanner', []);
    },

    removeBanner: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'removeBanner', []);
    },

    createInterstitial: function (options, successEvent, error) {
        exec(successEvent, error, 'AdMobNextGen', 'createInterstitial', [options]);
    },

    showInterstitial: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showInterstitial', []);
    },

    createRewarded: function (options, successEvent, error) {
        exec(successEvent, error, 'AdMobNextGen', 'createRewarded', [options]);
    },

    showRewarded: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showRewarded', []);
    },

    createRewardedInterstitial: function (options, success, error) {
        exec(success, error, 'AdMobNextGen', 'createRewardedInterstitial', [options]);
    },

    showRewardedInterstitial: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showRewardedInterstitial', []);
    },

    loadAppOpenAd: function (options, successEvent, error) {
        exec(successEvent, error, 'AdMobNextGen', 'loadAppOpenAd', [options]);
    },

    showAppOpenAd: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showAppOpenAd', []);
    },
    
    // Only Android
    createNativeAd: function (options, successEvent, error) {
        var isCapacitorEnvironment = typeof window.Capacitor !== 'undefined';
        var isCordova15Environment = false;
        if (!isCapacitorEnvironment && typeof cordova !== 'undefined' && cordova.platformVersion) {
            var majorVersion = parseInt(cordova.platformVersion.split('.')[0], 10);
            if (majorVersion >= 15) {
                isCordova15Environment = true;
            }
        }
        options = options || {};
        options.isCapacitor = isCapacitorEnvironment;
        options.isCordova15 = isCordova15Environment;
        exec(successEvent, error, 'AdMobNextGen', 'createNativeAd', [options]);
    },

    removeNativeAd: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'removeNativeAd', []);
    },

    startBannerPreload: function (options, success, error) {
       var isCapacitorEnvironment = typeof window.Capacitor !== 'undefined';
        var isCordova15Environment = false;
        if (!isCapacitorEnvironment && typeof cordova !== 'undefined' && cordova.platformVersion) {
            var majorVersion = parseInt(cordova.platformVersion.split('.')[0], 10);
            if (majorVersion >= 15) {
                isCordova15Environment = true;
            }
        }
        options = options || {};
        options.isCapacitor = isCapacitorEnvironment;
        options.isCordova15 = isCordova15Environment;

        exec(success, error, 'AdMobNextGen', 'startBannerPreload', [options]);
    },

    showPreloadedBanner: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showPreloadedBanner', []);
    },

    hideBannerPreload: function (success, error) {
            exec(success, error, 'AdMobNextGen', 'hideBannerPreload', []);
    },

    stopBannerPreload: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'stopBannerPreload', []);
    },

    startAppOpenPreload: function (options, success, error) {
        exec(success, error, 'AdMobNextGen', 'startAppOpenPreload', [options]);
    },

    showPreloadedAppOpenAd: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showPreloadedAppOpenAd', []);
    },

    isAppOpenAdAvailable: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'isAppOpenAdAvailable', []);
    },

    stopAppOpenPreload: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'stopAppOpenPreload', []);
    },

    startInterstitialPreload: function (options, success, error) {
        exec(success, error, 'AdMobNextGen', 'startInterstitialPreload', [options]);
    },

    showPreloadedInterstitial: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showPreloadedInterstitial', []);
    },

    isInterstitialAdAvailable: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'isInterstitialAdAvailable', []);
    },

    stopInterstitialPreload: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'stopInterstitialPreload', []);
    },

    startRewardedPreload: function (options, success, error) {
        exec(success, error, 'AdMobNextGen', 'startRewardedPreload', [options]);
    },

    showPreloadedRewarded: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showPreloadedRewarded', []);
    },

    isRewardedAdAvailable: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'isRewardedAdAvailable', []);
    },

    stopRewardedPreload: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'stopRewardedPreload', []);
    },

    startRewardedInterstitialPreload: function (options, success, error) {
        exec(success, error, 'AdMobNextGen', 'startRewardedInterstitialPreload', [options]);
    },

    showPreloadedRewardedInterstitial: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'showPreloadedRewardedInterstitial', []);
    },

    isRewardedInterstitialAdAvailable: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'isRewardedInterstitialAdAvailable', []);
    },

    stopRewardedInterstitialPreload: function (success, error) {
        exec(success, error, 'AdMobNextGen', 'stopRewardedInterstitialPreload', []);
    }

};

module.exports = AdMobNextGen;