// js/pipAd.js

// Implementation guide (Policy)
// https://support.google.com/admob/answer/17155813

function loadPipAd() {
    if (isDeviceready) {
        if (window.logToScreen) window.logToScreen("Loading Picture-in-Picture Ad...");
        
        var selectedPosition = document.getElementById('pip-pos').value || 'DEFAULT';
        var selectedScope = document.getElementById('pip-scope').value || 'SCREEN';
        var isAutoShowSelected = document.getElementById('pip-autoshow').checked || false;

        admobNextGen.createPictureInPicture({
            adUnitId: PiP_ID,
            position: selectedPosition,
            presentationScope: selectedScope,
            isAutoShow: isAutoShowSelected,
            retryInterval: 5000
        });
    }
}

function showPipAd() {
    if (isDeviceready) {
        if (window.logToScreen) window.logToScreen("Showing Picture-in-Picture Ad...");
        admobNextGen.showPictureInPicture();
    }
}

function hidePipAd() {
    if (isDeviceready) {
        if (window.logToScreen) window.logToScreen("Hiding Picture-in-Picture Ad...");
        admobNextGen.hidePictureInPicture();
    }
}

function removePipAd() {
    if (isDeviceready) {
        if (window.logToScreen) window.logToScreen("Removing Picture-in-Picture Ad...");
        admobNextGen.removePictureInPicture();
    }
}

// ==========================================
// EVENT LISTENERS
// ==========================================

document.addEventListener('on.pip.loaded', (e) => {
    var data = e.data || e;
    console.log("PiP Ad Loaded", data);
    if (window.logToScreen) window.logToScreen("PiP Ad Loaded & Ready", "event");
});

document.addEventListener('on.pip.failed.load', (e) => {
    var err = e.data || e;
    console.error("PiP Load Failed:", err);
    if (window.logToScreen) window.logToScreen("PiP Load Failed: " + JSON.stringify(err), "warn");
});

document.addEventListener('on.pip.shown', () => {
    console.log("PiP Ad window shown on screen");
    if (window.logToScreen) window.logToScreen("PiP Window Displayed", "event");
});

document.addEventListener('on.pip.hidden', () => {
    console.log("PiP Ad window hidden from screen");
    if (window.logToScreen) window.logToScreen("PiP Window Hidden", "event");
});

document.addEventListener('on.pip.opened', () => {
    console.log("PiP Ad full-screen content opened");
    if (window.logToScreen) window.logToScreen("PiP Content Fullscreen Opened", "event");
});

document.addEventListener('on.pip.closed', () => {
    console.log("PiP Ad full-screen content closed");
    if (window.logToScreen) window.logToScreen("PiP Content Fullscreen Closed", "event");
});

document.addEventListener('on.pip.failed.show', (e) => {
    var err = e.data || e;
    console.error("PiP Show Failed:", err);
    if (window.logToScreen) window.logToScreen("PiP Show Failed: " + JSON.stringify(err), "warn");
});

document.addEventListener('on.pip.impression', () => {
    console.log("PiP Impression recorded");
    if (window.logToScreen) window.logToScreen("PiP Impression Recorded", "event");
});

document.addEventListener('on.pip.clicked', () => {
    console.log("PiP Clicked");
    if (window.logToScreen) window.logToScreen("PiP Ad Clicked!", "event");
});

document.addEventListener('on.pip.revenue', (e) => {
    var data = e.data || e;
    console.log("PiP revenue", data);
    if (window.logToScreen) {
        var revStr = data.value + " " + data.currency;
        window.logToScreen("  PIP REVENUE: " + revStr, "reward");
    }
});