#import "AdMobNextGen.h"
#import "ConsentExecutor.h"
#import "GlobalSettingsExecutor.h"
#import "BannerExecutor.h"
#import "InterstitialExecutor.h"
#import "RewardedExecutor.h"
#import "AppOpenAdExecutor.h"
#import "RewardedInterstitialExecutor.h"

@interface GlobalBoundsObserverView : UIView
@property (nonatomic, copy) void (^onBoundsChanged)(CGRect bounds);
@end

@implementation GlobalBoundsObserverView
- (void)layoutSubviews {
    [super layoutSubviews];
    if (self.onBoundsChanged) {
        self.onBoundsChanged(self.bounds);
    }
}
@end

@interface AdMobNextGen()
@property (nonatomic, assign) BOOL isInitialized;
@property (nonatomic, strong) ConsentExecutor *consentExecutor;
@property (nonatomic, strong) GlobalSettingsExecutor *globalSettingsExecutor;
@property (nonatomic, strong) BannerExecutor *bannerExecutor;
@property (nonatomic, strong) InterstitialExecutor *interstitialExecutor;
@property (nonatomic, strong) RewardedExecutor *rewardedExecutor;
@property (nonatomic, strong) RewardedInterstitialExecutor *rewardedInterstitialExecutor;

@property (nonatomic, strong) GlobalBoundsObserverView *globalBoundsObserver;
@property (nonatomic, assign) CGFloat lastObservedWidth;
@end

@implementation AdMobNextGen

- (void)pluginInitialize {
    [super pluginInitialize];
    self.consentExecutor = [[ConsentExecutor alloc] initWithPlugin:self];
    self.globalSettingsExecutor = [[GlobalSettingsExecutor alloc] initWithPlugin:self];
    self.bannerExecutor = [[BannerExecutor alloc] initWithPlugin:self];
    self.interstitialExecutor = [[InterstitialExecutor alloc] initWithPlugin:self];
    self.rewardedExecutor = [[RewardedExecutor alloc] initWithPlugin:self];
    [[AppOpenAdExecutor sharedInstance] initializeWithPlugin:self];
    self.rewardedInterstitialExecutor = [[RewardedInterstitialExecutor alloc] initWithPlugin:self];

    [self setupGlobalBoundsObserver];
}

- (void)setupGlobalBoundsObserver {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *rootVC = self.viewController;
        if (!rootVC) return;

        self.lastObservedWidth = rootVC.view.bounds.size.width;

        GlobalBoundsObserverView *observerView = [[GlobalBoundsObserverView alloc] init];
        observerView.userInteractionEnabled = NO;
        observerView.backgroundColor = [UIColor clearColor];
        observerView.translatesAutoresizingMaskIntoConstraints = NO;

        [rootVC.view addSubview:observerView];
        [rootVC.view sendSubviewToBack:observerView];

        [NSLayoutConstraint activateConstraints:@[
            [observerView.leadingAnchor constraintEqualToAnchor:rootVC.view.leadingAnchor],
            [observerView.trailingAnchor constraintEqualToAnchor:rootVC.view.trailingAnchor],
            [observerView.topAnchor constraintEqualToAnchor:rootVC.view.topAnchor],
            [observerView.bottomAnchor constraintEqualToAnchor:rootVC.view.bottomAnchor]
        ]];

        __weak typeof(self) weakSelf = self;
        observerView.onBoundsChanged = ^(CGRect bounds) {
            CGFloat currentWidth = bounds.size.width;

            if (fabs(weakSelf.lastObservedWidth - currentWidth) > 1.0) {
                weakSelf.lastObservedWidth = currentWidth;

                BOOL isLandscape = bounds.size.width > bounds.size.height;
                NSString *orientationStr = isLandscape ? @"LANDSCAPE" : @"PORTRAIT";

                NSString *jsonStr = [NSString stringWithFormat:@"{\"orientation\":\"%@\"}", orientationStr];
                [weakSelf fireEvent:@"document" event:@"on.screen.orientation.changed" withData:jsonStr];
            }
        };

        self.globalBoundsObserver = observerView;
    });
}

- (void)initialize:(CDVInvokedUrlCommand*)command {
    NSDictionary *options = nil;
    if (command.arguments.count > 0 && [command.arguments objectAtIndex:0] != [NSNull null]) {
        options = [command.arguments objectAtIndex:0];
    }

    if (options != nil && [options isKindOfClass:[NSDictionary class]]) {
        GADRequestConfiguration *requestConfig = GADMobileAds.sharedInstance.requestConfiguration;

        if (options[@"maxAdContentRating"]) {
            NSString *rating = options[@"maxAdContentRating"];
            if ([rating isEqualToString:@"G"]) {
                requestConfig.maxAdContentRating = GADMaxAdContentRatingGeneral;
            } else if ([rating isEqualToString:@"PG"]) {
                requestConfig.maxAdContentRating = GADMaxAdContentRatingParentalGuidance;
            } else if ([rating isEqualToString:@"T"]) {
                requestConfig.maxAdContentRating = GADMaxAdContentRatingTeen;
            } else if ([rating isEqualToString:@"MA"]) {
                requestConfig.maxAdContentRating = GADMaxAdContentRatingMatureAudience;
            } else {
                requestConfig.maxAdContentRating = @"";
            }
        }

        NSNumber *childDirected = options[@"tagForChildDirectedTreatment"];
        NSNumber *underAge = options[@"tagForUnderAgeOfConsent"];

        if (childDirected != nil && [childDirected boolValue]) {
            requestConfig.ageRestrictedTreatment = GADAgeRestrictedTreatmentChild;
        } else if (underAge != nil && [underAge boolValue]) {
            requestConfig.ageRestrictedTreatment = GADAgeRestrictedTreatmentTeen;
        } else if ((childDirected != nil && ![childDirected boolValue]) ||
                   (underAge != nil && ![underAge boolValue])) {
            requestConfig.ageRestrictedTreatment = GADAgeRestrictedTreatmentUnspecified;
        }
    }

    [self.commandDelegate runInBackground:^{
        [[GADMobileAds sharedInstance] startWithCompletionHandler:^(GADInitializationStatus * _Nonnull status) {
            self.isInitialized = YES;
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Initialization complete."];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }];
    }];
}

#pragma mark - Consent (UMP) Routing

- (void)requestConsentInfo:(CDVInvokedUrlCommand*)command {
    NSDictionary *options = nil;
    if (command.arguments.count > 0) {
        options = [command.arguments objectAtIndex:0];
    }
    [self.consentExecutor requestConsentInfo:options command:command];
}

- (void)showPrivacyOptionsForm:(CDVInvokedUrlCommand*)command {
    [self.consentExecutor showPrivacyOptionsForm:command];
}

- (void)getTCData:(CDVInvokedUrlCommand*)command {
    [self.consentExecutor getTCData:command];
}

- (void)canRequestAds:(CDVInvokedUrlCommand *)command {
    [self.consentExecutor canRequestAds:command];
}

- (void)requestTrackingAuthorization:(CDVInvokedUrlCommand *)command {
    [self.consentExecutor requestTrackingAuthorization:command];
}

- (void)getTrackingAuthorizationStatus:(CDVInvokedUrlCommand *)command {
    [self.consentExecutor getTrackingAuthorizationStatus:command];
}

#pragma mark - Global Settings Routing

- (void)setAppVolume:(CDVInvokedUrlCommand*)command {
    [self.globalSettingsExecutor setAppVolume:command];
}

- (void)setAppMuted:(CDVInvokedUrlCommand*)command {
    [self.globalSettingsExecutor setAppMuted:command];
}

- (void)setRequestConfiguration:(CDVInvokedUrlCommand*)command {
    [self.globalSettingsExecutor setRequestConfiguration:command];
}

#pragma mark - Banner Routing

- (void)createBanner:(CDVInvokedUrlCommand*)command {
    if (!self.isInitialized) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"SDK not initialized. Call initialize() first."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    NSDictionary *options = [command.arguments objectAtIndex:0];
    if (options != nil && [options isKindOfClass:[NSDictionary class]]) {
        [self.bannerExecutor createBanner:options command:command];
    } else {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid options object."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

- (void)showBanner:(CDVInvokedUrlCommand*)command {
    [self.bannerExecutor showBanner:command];
}

- (void)hideBanner:(CDVInvokedUrlCommand*)command {
    [self.bannerExecutor hideBanner:command];
}

- (void)removeBanner:(CDVInvokedUrlCommand*)command {
    [self.bannerExecutor removeBanner:command];
}

#pragma mark - Interstitial Routing

- (void)createInterstitial:(CDVInvokedUrlCommand*)command {
    if (!self.isInitialized) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"SDK not initialized. Call initialize() first."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    NSDictionary *options = [command.arguments objectAtIndex:0];
    if (options != nil && [options isKindOfClass:[NSDictionary class]]) {
        [self.interstitialExecutor createInterstitial:options command:command];
    } else {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid options object."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

- (void)showInterstitial:(CDVInvokedUrlCommand*)command {
    [self.interstitialExecutor showInterstitial:command];
}

- (void)createRewarded:(CDVInvokedUrlCommand*)command {
    if (!self.isInitialized) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"SDK not initialized. Call initialize() first."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    NSDictionary *options = [command.arguments objectAtIndex:0];
    if (options != nil) {
        [self.rewardedExecutor createRewarded:options command:command];
    }
}

- (void)showRewarded:(CDVInvokedUrlCommand*)command {
    [self.rewardedExecutor showRewarded:command];
}

- (void)loadAppOpenAd:(CDVInvokedUrlCommand*)command {
    if (!self.isInitialized) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"SDK not initialized. Call initialize() first."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    NSDictionary *options = [command.arguments objectAtIndex:0];
    if (options != nil) {
        [[AppOpenAdExecutor sharedInstance] loadAppOpenAd:options command:command];
    }
}

- (void)showAppOpenAd:(CDVInvokedUrlCommand*)command {
    [[AppOpenAdExecutor sharedInstance] showAppOpenAd:command];
}

- (void)createRewardedInterstitial:(CDVInvokedUrlCommand*)command {
    if (!self.isInitialized) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"SDK not initialized. Call initialize() first."];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    NSDictionary *options = [command.arguments objectAtIndex:0];
    if (options != nil) {
        [self.rewardedInterstitialExecutor createRewardedInterstitial:options command:command];
    }
}

- (void)showRewardedInterstitial:(CDVInvokedUrlCommand*)command {
    [self.rewardedInterstitialExecutor showRewardedInterstitial:command];
}

#pragma mark - Event Emitter

- (void)fireEvent:(NSString *)obj event:(NSString *)eventName withData:(NSString *)jsonStr {
    NSString* js;
    if(obj && [obj isEqualToString:@"window"]) {
        js = [NSString stringWithFormat:@"var evt=document.createEvent(\"UIEvents\");evt.initUIEvent(\"%@\",true,false,window,0);window.dispatchEvent(evt);", eventName];
    } else if(jsonStr && [jsonStr length] > 0) {
        js = [NSString stringWithFormat:@"javascript:cordova.fireDocumentEvent('%@',%@);", eventName, jsonStr];
    } else {
        js = [NSString stringWithFormat:@"javascript:cordova.fireDocumentEvent('%@');", eventName];
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        [self.commandDelegate evalJs:js];
    });
}

@end
