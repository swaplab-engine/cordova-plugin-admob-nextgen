const fs = require('fs');
const path = require('path');

const rootPath = process.cwd();
const configPath = fs.existsSync(path.join(rootPath, 'capacitor.config.ts'))
    ? path.join(rootPath, 'capacitor.config.ts')
    : path.join(rootPath, 'capacitor.config.json');

const manifestPath = path.join(rootPath, 'android/app/src/main/AndroidManifest.xml');
const gradlePath = path.join(rootPath, 'android/app/build.gradle');
const iosPlistPath = path.join(rootPath, 'ios/App/App/Info.plist');

/**
 * ---------------------------------------------------------
 * ANDROID HOOKS
 * ---------------------------------------------------------
 */
function updateAndroidManifest(appId) {
    if (!fs.existsSync(manifestPath)) return;
    let content = fs.readFileSync(manifestPath, 'utf8');
    const appIdRegex = /<meta-data\s+android:name="com.google.android.gms.ads.APPLICATION_ID"\s+android:value=".*?"\s*\/>/;
    const newTag = `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${appId}"/>`;

    if (appIdRegex.test(content)) {
        content = content.replace(appIdRegex, newTag);
    } else {
        content = content.replace('</application>', `    ${newTag}\n    </application>`);
    }
    fs.writeFileSync(manifestPath, content, 'utf8');
    console.log(`[AdMob Hook] Success: Updated Android App ID to ${appId}`);
}

function updateGradleDependencies(nextGenVersion, umpVersion) {
    if (!fs.existsSync(gradlePath)) return;
    let content = fs.readFileSync(gradlePath, 'utf8');

    const adsRegex = /implementation\s+['"]com\.google\.android\.libraries\.ads\.mobile\.sdk:ads-mobile-sdk:.*?['"]/;
    const umpRegex = /implementation\s+['"]com\.google\.android\.ump:user-messaging-platform:.*?['"]/;

    const newAds = `implementation "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:${nextGenVersion}"`;
    const newUmp = `implementation "com.google.android.ump:user-messaging-platform:${umpVersion}"`;

    if (adsRegex.test(content)) content = content.replace(adsRegex, newAds);
    if (umpRegex.test(content)) content = content.replace(umpRegex, newUmp);

    fs.writeFileSync(gradlePath, content, 'utf8');
    //console.log(`[AdMob Hook] Success: Updated Android Next Gen SDK to ${nextGenVersion} and UMP to ${umpVersion}`);
}

function injectExclusionRules() {
    if (!fs.existsSync(gradlePath)) return;
    let content = fs.readFileSync(gradlePath, 'utf8');

    if (content.includes('exclude group: "com.google.android.gms", module: "play-services-ads"')) return;

    const exclusionBlock = `
    // [AdMob Next Gen] Exclude Legacy SDK to prevent duplicates
    configurations.configureEach {
        exclude group: "com.google.android.gms", module: "play-services-ads"
        exclude group: "com.google.android.gms", module: "play-services-ads-lite"
    }
`;
    content += exclusionBlock;
    fs.writeFileSync(gradlePath, content, 'utf8');
   // console.log('[AdMob Hook] Success: Injected legacy SDK exclusion rules into build.gradle');
}

/**
 * ---------------------------------------------------------
 * IOS HOOKS (THE NEW SMART ENGINE)
 * ---------------------------------------------------------
 */
function updateIosInfoPlist(appIdIos) {
    if (!fs.existsSync(iosPlistPath)) {
        //console.warn('[AdMob Hook] Warning: ios/App/App/Info.plist not found. Run "npx cap add ios" first.');
        return;
    }
    
    let content = fs.readFileSync(iosPlistPath, 'utf8');

    function setStringKey(key, value) {
        const regex = new RegExp(`<key>${key}</key>\\s*<string>.*?</string>`, 's');
        const replacement = `<key>${key}</key>\n\t<string>${value}</string>`;
        if (regex.test(content)) {
            content = content.replace(regex, replacement);
        } else {
            content = content.replace('</dict>\n</plist>', `\t${replacement}\n</dict>\n</plist>`);
        }
    }

    function setBoolKey(key, value) {
        const boolStr = value ? '<true/>' : '<false/>';
        const regex = new RegExp(`<key>${key}</key>\\s*<(true|false)\\/>`, 's');
        const replacement = `<key>${key}</key>\n\t${boolStr}`;
        if (regex.test(content)) {
            content = content.replace(regex, replacement);
        } else {
            content = content.replace('</dict>\n</plist>', `\t${replacement}\n</dict>\n</plist>`);
        }
    }

    setStringKey('GADApplicationIdentifier', appIdIos);
    
    setStringKey('NSUserTrackingUsageDescription', 'This identifier will be used to deliver personalized ads to you.');
    
    setBoolKey('GADDelayAppMeasurementInit', true);
    // https://developers.google.com/admob/ios/quick-start
    if (!content.includes('<key>SKAdNetworkItems</key>')) {
        const skAdNetworks = [
            'cstr6suwn9.skadnetwork', '4fzdc2evr5.skadnetwork', '2fnua5tdw4.skadnetwork', 'ydx93a7ass.skadnetwork',
            'p78axxw29g.skadnetwork', 'v72qych5uu.skadnetwork', 'ludvb6z3bs.skadnetwork', 'cp8zw746q7.skadnetwork',
            '3sh42y64q3.skadnetwork', 'c6k4g5qg8m.skadnetwork', 's39g8k73mm.skadnetwork', 'wg4vff78zm.skadnetwork',
            '3qy4746246.skadnetwork', 'f38h382jlk.skadnetwork', 'hs6bdukanm.skadnetwork', 'mlmmfzh3r3.skadnetwork',
            'v4nxqhlyqp.skadnetwork', 'wzmmz9fp6w.skadnetwork', 'su67r6k2v3.skadnetwork', 'yclnxrl5pm.skadnetwork',
            't38b2kh725.skadnetwork', '7ug5zh24hu.skadnetwork', 'gta9lk7p23.skadnetwork', 'vutu7akeur.skadnetwork',
            'y5ghdn5j9k.skadnetwork', 'v9wttpbfk9.skadnetwork', 'n38lu8286q.skadnetwork', '47vhws6wlr.skadnetwork',
            'kbd757ywx3.skadnetwork', '9t245vhmpl.skadnetwork', 'a2p9lx4jpn.skadnetwork', '22mmun2rn5.skadnetwork',
            '44jx6755aq.skadnetwork', 'k674qkevps.skadnetwork', '4468km3ulz.skadnetwork', '2u9pt9hc89.skadnetwork',
            '8s468mfl3y.skadnetwork', 'klf5c3l5u5.skadnetwork', 'ppxm28t8ap.skadnetwork', 'uw77j35x4d.skadnetwork',
            '578prtvx9j.skadnetwork', '4dzt52r2t5.skadnetwork', 'tl55sbb4fm.skadnetwork', 'e5fvkxwrpn.skadnetwork',
            '8c4e2ghe7u.skadnetwork', '3rd42ekr43.skadnetwork', '3qcr597p9d.skadnetwork'
        ];

        let dicts = skAdNetworks.map(id => `\t\t<dict>\n\t\t\t<key>SKAdNetworkIdentifier</key>\n\t\t\t<string>${id}</string>\n\t\t</dict>`).join('\n');
        let arrayBlock = `\t<key>SKAdNetworkItems</key>\n\t<array>\n${dicts}\n\t</array>`;
        
        content = content.replace('</dict>\n</plist>', `${arrayBlock}\n</dict>\n</plist>`);
    }

    fs.writeFileSync(iosPlistPath, content, 'utf8');
    // console.log(`[AdMob Hook] Success: Updated iOS Info.plist with App ID (${appIdIos}), ATT prompt, and SKAdNetworks.`);
}

/**
 * ---------------------------------------------------------
 * MAIN RUNNER
 * ---------------------------------------------------------
 */
function run() {
    try {
        if (!fs.existsSync(configPath)) {
            // console.warn('[AdMob Hook] capacitor.config not found. Skipping auto-injection.');
            return;
        }

        const configContent = fs.readFileSync(configPath, 'utf8');
        let admob;

        if (configPath.endsWith('.json')) {
            admob = JSON.parse(configContent).plugins?.AdMob;
        } else {
            // Extract from .ts
            const appIdAndroid = configContent.match(/APP_ID_ANDROID:\s*['"](.*?)['"]/);
            const appIdIos = configContent.match(/APP_ID_IOS:\s*['"](.*?)['"]/);
            const sdk = configContent.match(/NEXT_GEN_SDK_VERSION:\s*['"](.*?)['"]/);
            const ump = configContent.match(/UMP_VERSION:\s*['"](.*?)['"]/);
            
            admob = {
                APP_ID_ANDROID: appIdAndroid ? appIdAndroid[1] : "ca-app-pub-3940256099942544~3347511713",
                APP_ID_IOS: appIdIos ? appIdIos[1] : "ca-app-pub-3940256099942544~1458002511",
                NEXT_GEN_SDK_VERSION: sdk ? sdk[1] : "1.6.0",
                UMP_VERSION: ump ? ump[1] : "4.0.0"
            };
        }

        if (admob?.APP_ID_ANDROID) updateAndroidManifest(admob.APP_ID_ANDROID);
        injectExclusionRules();
        if (admob?.NEXT_GEN_SDK_VERSION || admob?.UMP_VERSION) {
            updateGradleDependencies(
                admob.NEXT_GEN_SDK_VERSION || "1.6.0",
                admob.UMP_VERSION || "4.0.0"
            );
        }

        if (admob?.APP_ID_IOS) {
            updateIosInfoPlist(admob.APP_ID_IOS);
        }

    } catch (err) {
        console.error('[AdMob Hook] Error:', err.message);
    }
}

run();