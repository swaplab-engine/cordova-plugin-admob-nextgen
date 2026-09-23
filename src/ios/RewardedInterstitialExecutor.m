#import "RewardedInterstitialExecutor.h"
#import "AdMobNextGen.h"

@interface RewardedInterstitialExecutor()

@property (nonatomic, weak) AdMobNextGen *plugin;
@property (nonatomic, strong) GADRewardedInterstitialAd *rewardedInterstitialAd;

@property (nonatomic, assign) BOOL isLoading;
@property (nonatomic, assign) BOOL isAutoShow;
@property (nonatomic, assign) BOOL isRewardEarned;

@property (nonatomic, assign) NSTimeInterval lastLoadTime;
@property (nonatomic, assign) NSTimeInterval minLoadInterval;

@end

@implementation RewardedInterstitialExecutor

- (instancetype)initWithPlugin:(AdMobNextGen *)plugin {
    self = [super init];
    if (self) {
        self.plugin = plugin;
        self.isLoading = NO;
        self.isAutoShow = NO; 
        self.isRewardEarned = NO;
        self.lastLoadTime = 0;
        self.minLoadInterval = 5.0; 
    }
    return self;
}

- (void)createRewardedInterstitial:(NSDictionary *)options command:(CDVInvokedUrlCommand *)command {
    NSString *adUnitId = options[@"adUnitId"];

    if (!adUnitId) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"adUnitId is required."];
        [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    if (options[@"isAutoShow"] != nil) {
        self.isAutoShow = [options[@"isAutoShow"] boolValue];
    } else {
        self.isAutoShow = NO;
    }

    if (options[@"retryInterval"] != nil) {
        self.minLoadInterval = [options[@"retryInterval"] doubleValue] / 1000.0;
    }

    [self loadRewardedInterstitial:adUnitId command:command];
}

- (void)loadRewardedInterstitial:(NSString *)adUnitId command:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];

        if (self.isLoading) {

            if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Ad is loading"];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            return;
        }

        if (self.rewardedInterstitialAd != nil) {

            [self.plugin fireEvent:@"document" event:@"on.rewardedInter.loaded" withData:nil];
            if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ad Loaded (Cached)"];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }

            if (self.isAutoShow) {

                [self showRewardedInterstitialAd:nil];
            }

            return;
        }

        if ((currentTime - self.lastLoadTime) < self.minLoadInterval) {

            NSString *errorMsg = [NSString stringWithFormat:@"Request too fast. Please wait %.0fms to prevent invalid traffic.", (self.minLoadInterval * 1000.0)];

            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMsg];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }

        self.isLoading = YES;
        self.lastLoadTime = currentTime;

        if (command) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Loading started..."];
            [pluginResult setKeepCallbackAsBool:YES];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }

        GADRequest *request = [GADRequest request];

        [GADRewardedInterstitialAd loadWithAdUnitID:adUnitId
                                            request:request
                                  completionHandler:^(GADRewardedInterstitialAd *ad, NSError *error) {
            self.isLoading = NO;

            if (error) {

                NSString *jsonStr = [NSString stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}", (long)error.code, [error localizedDescription]];
                [self.plugin fireEvent:@"document" event:@"on.rewardedInter.failed.load" withData:jsonStr];

                if (command) {
                    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]];
                    [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
                return;
            }

            self.rewardedInterstitialAd = ad;
            self.rewardedInterstitialAd.fullScreenContentDelegate = self;

            __weak typeof(self) weakSelf = self;
            self.rewardedInterstitialAd.paidEventHandler = ^(GADAdValue * _Nonnull value) {
                NSString *jsonStr = [NSString stringWithFormat:@"{\"value\":%lld, \"currency\":\"%@\", \"precision\":%ld}",
                                     value.value.longLongValue, value.currencyCode, (long)value.precision];

                [weakSelf.plugin fireEvent:@"document" event:@"on.rewardedInter.revenue" withData:jsonStr];
            };

            [self.plugin fireEvent:@"document" event:@"on.rewardedInter.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showRewardedInterstitialAd:command];
            } else if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ad Loaded"];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        }];
    });
}

- (void)showRewardedInterstitial:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self showRewardedInterstitialAd:command];
    });
}

- (void)showRewardedInterstitialAd:(CDVInvokedUrlCommand *)command {
    if (self.rewardedInterstitialAd != nil) {

        if ([UIApplication sharedApplication].applicationState != UIApplicationStateActive) {
            NSString *jsonStr = @"{\"code\":\"APP_IN_BACKGROUND\", \"message\":\"Ad presentation blocked: Application is currently in the background or inactive.\"}";
            [self.plugin fireEvent:@"document" event:@"on.rewardedInter.failed.show" withData:jsonStr];

            if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Ad presentation blocked: Application is in background."];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            return;
        }

        self.isRewardEarned = NO;

        [self.rewardedInterstitialAd presentFromRootViewController:self.plugin.viewController
                                          userDidEarnRewardHandler:^{

            self.isRewardEarned = YES;

            GADAdReward *reward = self.rewardedInterstitialAd.adReward;

            NSString *jsonStr = [NSString stringWithFormat:@"{\"amount\":%f, \"type\":\"%@\"}", [reward.amount doubleValue], reward.type];
            [self.plugin fireEvent:@"document" event:@"on.rewardedInter.earned" withData:jsonStr];
        }];

        if (command) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ad Shown"];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    } else {

        if (command) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Ad Not Ready"];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }
}

#pragma mark - GADFullScreenContentDelegate

- (void)adDidRecordImpression:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.rewardedInter.impression" withData:nil];
}

- (void)adDidRecordClick:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.rewardedInter.clicked" withData:nil];
}

- (void)adWillPresentFullScreenContent:(id<GADFullScreenPresentingAd>)ad {

    [self.plugin fireEvent:@"document" event:@"on.rewardedInter.shown" withData:nil];
}

- (void)adDidDismissFullScreenContent:(id<GADFullScreenPresentingAd>)ad {

    if (!self.isRewardEarned) {
        [self.plugin fireEvent:@"document" event:@"on.rewardedInter.canceled" withData:nil];
    }
    self.rewardedInterstitialAd = nil;
    self.isLoading = NO;
    [self.plugin fireEvent:@"document" event:@"on.rewardedInter.dismissed" withData:nil];
}

- (void)ad:(id<GADFullScreenPresentingAd>)ad didFailToPresentFullScreenContentWithError:(NSError *)error {

    self.rewardedInterstitialAd = nil;
    self.isLoading = NO;

    NSString *jsonStr = [NSString stringWithFormat:@"{\"message\":\"%@\"}", [error localizedDescription]];
    [self.plugin fireEvent:@"document" event:@"on.rewardedInter.failed.show" withData:jsonStr];
}

@end
