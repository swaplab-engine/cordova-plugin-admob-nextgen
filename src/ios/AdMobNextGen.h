#import <Cordova/CDV.h>
#import <GoogleMobileAds/GoogleMobileAds.h>

@interface AdMobNextGen : CDVPlugin

- (void)initialize:(CDVInvokedUrlCommand*)command;

- (void)requestConsentInfo:(CDVInvokedUrlCommand*)command;
- (void)showPrivacyOptionsForm:(CDVInvokedUrlCommand*)command;
- (void)getTCData:(CDVInvokedUrlCommand*)command;
- (void)canRequestAds:(CDVInvokedUrlCommand*)command;
- (void)requestTrackingAuthorization:(CDVInvokedUrlCommand*)command;
- (void)getTrackingAuthorizationStatus:(CDVInvokedUrlCommand*)command;

- (void)setAppVolume:(CDVInvokedUrlCommand*)command;
- (void)setAppMuted:(CDVInvokedUrlCommand*)command;
- (void)setRequestConfiguration:(CDVInvokedUrlCommand*)command;

- (void)createBanner:(CDVInvokedUrlCommand*)command;
- (void)showBanner:(CDVInvokedUrlCommand*)command;
- (void)hideBanner:(CDVInvokedUrlCommand*)command;
- (void)removeBanner:(CDVInvokedUrlCommand*)command;

- (void)createPictureInPicture:(CDVInvokedUrlCommand*)command;
- (void)showPictureInPicture:(CDVInvokedUrlCommand*)command;
- (void)hidePictureInPicture:(CDVInvokedUrlCommand*)command;
- (void)removePictureInPicture:(CDVInvokedUrlCommand*)command;

- (void)createInterstitial:(CDVInvokedUrlCommand*)command;
- (void)showInterstitial:(CDVInvokedUrlCommand*)command;

- (void)createRewarded:(CDVInvokedUrlCommand*)command;
- (void)showRewarded:(CDVInvokedUrlCommand*)command;

- (void)loadAppOpenAd:(CDVInvokedUrlCommand*)command;
- (void)showAppOpenAd:(CDVInvokedUrlCommand*)command;

- (void)createRewardedInterstitial:(CDVInvokedUrlCommand*)command;
- (void)showRewardedInterstitial:(CDVInvokedUrlCommand*)command;

- (void)fireEvent:(NSString *)obj event:(NSString *)eventName withData:(NSString *)jsonStr;

@end
