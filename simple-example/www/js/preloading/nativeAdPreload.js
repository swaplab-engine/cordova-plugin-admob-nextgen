// js/preloading/nativeAdPreload.js

// Starts the preloading process for Native Ads.
// This will only be triggered once, as it will hold multiple ads ready to be displayed.
function startNativePreload() {
    if (typeof isDeviceready !== 'undefined' && isDeviceready && !isPlatformIOS) {
        if (window.logToScreen) window.logToScreen("Starting Native Preloader...");

        admobNextGen.startNativePreload({
            adUnitId: NativeAd_ID,
            view: 'banner_bottom',    // Presets: 'banner_top', 'banner_bottom', 'modal_center'
            isOverlapping: false,     // true = Overlay, false = Push Content
            bufferSize: 1,            // Optional: Number of ads to cache in the background: Default: 1 max 3
            isAutoShow: true,
            retryInterval: 5000       // Anti-spam interval
        });
    }
}


// Example of a combination within a single function
function showNativePreload() {
    if (typeof isDeviceready !== 'undefined' && isDeviceready && !isPlatformIOS) {
        if (window.logToScreen) window.logToScreen("Checking Native Ad availability...");

        // Checking for the availability of ads within the preloader pool first
        admobNextGen.isNativeAdAvailable(
            function(isAvailable) {
                // isAvailable returns a value of 1 (true) or 0 (false)
                if (isAvailable) {
                    if (window.logToScreen) window.logToScreen("Native Ad is ready. Showing now...");
                    
                    // Displaying an ad (optional: you can add { view: 'banner_top' } here)
                    admobNextGen.showPreloadedNativeAd();
                } else {
                    if (window.logToScreen) window.logToScreen("Native Ad is NOT available yet.", "warn");
                    console.warn("Preloaded Native Ad is not ready or pool is empty.");
                    
                    // Optional: You can call startNativePreload() here
                    // if you want to force a reload when the buffer is empty.
                }
            }, 
            function(err) {
                console.error("Error checking Native Ad availability:", err);
            }
        );
    }
}

// Hides the Native Ad UI but keeps the preloader running in the background.
function hideNativePreload() {
    if (typeof isDeviceready !== 'undefined' && isDeviceready && !isPlatformIOS) {
        if (window.logToScreen) window.logToScreen("Hiding Preloaded Native...");
        admobNextGen.hidePreloadedNativeAd();
    }
}

// Stops the preloader engine and clears all cached native ads.
function stopNativePreloader() {
    if (typeof isDeviceready !== 'undefined' && isDeviceready && !isPlatformIOS) {
        admobNextGen.stopNativePreload();
        if (window.logToScreen) window.logToScreen("Native Preloader Stopped");
    }
}

// ==========================================
// EVENT LISTENERS (PRELOADER SPECIFIC)
// ==========================================

document.addEventListener('on.native.preload.exhausted', (e) => {
    var data = e.data || e;
    console.log("Background native ad buffer is empty!", JSON.stringify(data));
    // Optionally restart the preloader here
    // startNativePreload();
});

document.addEventListener('on.native.loaded', (e) => {
    var data = e.data || e;
    if (data && data.source === "preloader") {
        console.log("Native Ad Preloaded Successfully");
        if (window.logToScreen) window.logToScreen("Native Ad Preloaded", "event");
    }
});

document.addEventListener('on.native.failed.load', (e) => {
    var data = e.data || e;
    if (data && data.source === "preloader") {
        console.error("Native Preload Failed:", data);
        if (window.logToScreen) window.logToScreen("Native Preload Failed: " + JSON.stringify(data), "warn");
    }
});

document.addEventListener('on.native.shown', (e) => {
    var data = e.data || e;
    if (data && data.source === "preloader") {
        console.log("Preloaded Native Ad Shown");
    }
});

document.addEventListener('on.native.revenue', (e) => {
    var data = e.data || e;
    if (data && data.source === "preloader") {
        console.log("Preloaded Native Ad Revenue: " + JSON.stringify(data));
    }
});

document.addEventListener('on.native.failed.show', (e) => {
    var data = e.data || e;
    if (data && data.source === "preloader") {
        console.error("Preloaded Native Ad Failed to Show:", data);
    }
});


