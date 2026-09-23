#import "RewardedExecutor.h"
#import "AdMobNextGen.h"

@interface RewardedExecutor()

@property (nonatomic, weak) AdMobNextGen *plugin;
@property (nonatomic, strong) GADRewardedAd *rewardedAd;

@property (nonatomic, assign) BOOL isLoading;
@property (nonatomic, assign) BOOL isAutoShow;
@property (nonatomic, assign) BOOL isRewardEarned;

@property (nonatomic, assign) NSTimeInterval lastLoadTime;
@property (nonatomic, assign) NSTimeInterval minLoadInterval;

@end

@implementation RewardedExecutor

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

- (void)createRewarded:(NSDictionary *)options command:(CDVInvokedUrlCommand *)command {
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

    [self loadRewarded:adUnitId command:command];
}

- (void)loadRewarded:(NSString *)adUnitId command:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];

        if (self.isLoading) {

            return;
        }

        if (self.rewardedAd != nil) {

            [self.plugin fireEvent:@"document" event:@"on.rewarded.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showRewardedAd];
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

        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Loading started..."];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

        GADRequest *request = [GADRequest request];

        [GADRewardedAd loadWithAdUnitID:adUnitId
                                request:request
                      completionHandler:^(GADRewardedAd *ad, NSError *error) {
            self.isLoading = NO;

            if (error) {

                NSString *jsonStr = [NSString stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}", (long)error.code, [error localizedDescription]];
                [self.plugin fireEvent:@"document" event:@"on.rewarded.failed.load" withData:jsonStr];
                return;
            }

            self.rewardedAd = ad;
            self.rewardedAd.fullScreenContentDelegate = self;

            __weak typeof(self) weakSelf = self;
            self.rewardedAd.paidEventHandler = ^(GADAdValue * _Nonnull value) {
                NSString *jsonStr = [NSString stringWithFormat:@"{\"value\":%lld, \"currency\":\"%@\", \"precision\":%ld}",
                                     value.value.longLongValue, value.currencyCode, (long)value.precision];

                [weakSelf.plugin fireEvent:@"document" event:@"on.rewarded.revenue" withData:jsonStr];
            };

            [self.plugin fireEvent:@"document" event:@"on.rewarded.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showRewardedAd];
            }
        }];
    });
}

- (void)showRewarded:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.rewardedAd != nil) {
            [self showRewardedAd];
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Rewarded ad not ready yet"];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    });
}

- (void)showRewardedAd {
    if (self.rewardedAd == nil) return;

    if ([UIApplication sharedApplication].applicationState != UIApplicationStateActive) {
        NSString *jsonStr = @"{\"code\":\"APP_IN_BACKGROUND\", \"message\":\"Ad presentation blocked: Application is currently in the background or inactive.\"}";
        [self.plugin fireEvent:@"document" event:@"on.rewarded.failed.show" withData:jsonStr];
        return;
    }

    self.isRewardEarned = NO;

    [self.rewardedAd presentFromRootViewController:self.plugin.viewController
                          userDidEarnRewardHandler:^{

        self.isRewardEarned = YES;

        GADAdReward *reward = self.rewardedAd.adReward;

        NSString *jsonStr = [NSString stringWithFormat:@"{\"amount\":%f, \"type\":\"%@\"}", [reward.amount doubleValue], reward.type];
        [self.plugin fireEvent:@"document" event:@"on.rewarded.earned" withData:jsonStr];
    }];
}

#pragma mark - GADFullScreenContentDelegate

- (void)adDidRecordImpression:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.rewarded.impression" withData:nil];
}

- (void)adDidRecordClick:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.rewarded.clicked" withData:nil];
}

- (void)adWillPresentFullScreenContent:(id<GADFullScreenPresentingAd>)ad {

    [self.plugin fireEvent:@"document" event:@"on.rewarded.shown" withData:nil];
}

- (void)adDidDismissFullScreenContent:(id<GADFullScreenPresentingAd>)ad {

    if (!self.isRewardEarned) {
        [self.plugin fireEvent:@"document" event:@"on.rewarded.canceled" withData:nil];
    }
    self.rewardedAd = nil; 
    self.isLoading = NO;
    [self.plugin fireEvent:@"document" event:@"on.rewarded.dismissed" withData:nil];
}

- (void)ad:(id<GADFullScreenPresentingAd>)ad didFailToPresentFullScreenContentWithError:(NSError *)error {

    self.rewardedAd = nil;
    self.isLoading = NO;

    NSString *jsonStr = [NSString stringWithFormat:@"{\"message\":\"%@\"}", [error localizedDescription]];
    [self.plugin fireEvent:@"document" event:@"on.rewarded.failed.show" withData:jsonStr];
}

@end
