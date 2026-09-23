#import "InterstitialExecutor.h"
#import "AdMobNextGen.h"

@interface InterstitialExecutor()

@property (nonatomic, weak) AdMobNextGen *plugin;
@property (nonatomic, strong) GADInterstitialAd *interstitialAd;

@property (nonatomic, assign) BOOL isLoading;
@property (nonatomic, assign) BOOL isAutoShow;

@property (nonatomic, assign) NSTimeInterval lastLoadTime;
@property (nonatomic, assign) NSTimeInterval minLoadInterval;

@end

@implementation InterstitialExecutor

- (instancetype)initWithPlugin:(AdMobNextGen *)plugin {
    self = [super init];
    if (self) {
        self.plugin = plugin;
        self.isLoading = NO;
        self.isAutoShow = YES;
        self.lastLoadTime = 0;
        self.minLoadInterval = 5.0; 
    }
    return self;
}

- (void)createInterstitial:(NSDictionary *)options command:(CDVInvokedUrlCommand *)command {
    NSString *adUnitId = options[@"adUnitId"];

    if (!adUnitId) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"adUnitId is required."];
        [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    if (options[@"isAutoShow"] != nil) {
        self.isAutoShow = [options[@"isAutoShow"] boolValue];
    } else {
        self.isAutoShow = YES;
    }

    if (options[@"retryInterval"] != nil) {
        self.minLoadInterval = [options[@"retryInterval"] doubleValue] / 1000.0;
    }

    [self loadInterstitial:adUnitId command:command];
}

- (void)loadInterstitial:(NSString *)adUnitId command:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];

        if (self.isLoading) {

            return;
        }

        if (self.interstitialAd != nil) {

            [self.plugin fireEvent:@"document" event:@"on.interstitial.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showInterstitialAd];
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

        [GADInterstitialAd loadWithAdUnitID:adUnitId
                                    request:request
                          completionHandler:^(GADInterstitialAd *ad, NSError *error) {
            self.isLoading = NO;

            if (error) {

                NSString *jsonStr = [NSString stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}", (long)error.code, [error localizedDescription]];
                [self.plugin fireEvent:@"document" event:@"on.interstitial.failed.load" withData:jsonStr];
                return;
            }

            self.interstitialAd = ad;
            self.interstitialAd.fullScreenContentDelegate = self;

            __weak typeof(self) weakSelf = self;
            self.interstitialAd.paidEventHandler = ^(GADAdValue * _Nonnull value) {
                NSString *jsonStr = [NSString stringWithFormat:@"{\"value\":%lld, \"currency\":\"%@\", \"precision\":%ld}",
                                     value.value.longLongValue, value.currencyCode, (long)value.precision];

                [weakSelf.plugin fireEvent:@"document" event:@"on.interstitial.revenue" withData:jsonStr];
            };

            [self.plugin fireEvent:@"document" event:@"on.interstitial.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showInterstitialAd];
            }
        }];
    });
}

- (void)showInterstitial:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.interstitialAd != nil) {
            [self showInterstitialAd];
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Ad not ready"];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    });
}

- (void)showInterstitialAd {
    if (self.interstitialAd == nil) return;

    if ([UIApplication sharedApplication].applicationState == UIApplicationStateBackground) {
        NSString *jsonStr = @"{\"code\":\"APP_IN_BACKGROUND\", \"message\":\"Ad presentation blocked: Application is currently in the background.\"}";
        [self.plugin fireEvent:@"document" event:@"on.interstitial.failed.show" withData:jsonStr];
        return;
    }

    [self.interstitialAd presentFromRootViewController:self.plugin.viewController];
}

#pragma mark - GADFullScreenContentDelegate

- (void)adDidRecordImpression:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.interstitial.impression" withData:nil];
}

- (void)adDidRecordClick:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.interstitial.clicked" withData:nil];
}

- (void)adWillPresentFullScreenContent:(id<GADFullScreenPresentingAd>)ad {

    [self.plugin fireEvent:@"document" event:@"on.interstitial.shown" withData:nil];
}

- (void)adDidDismissFullScreenContent:(id<GADFullScreenPresentingAd>)ad {

    self.interstitialAd = nil;
    self.isLoading = NO;
    [self.plugin fireEvent:@"document" event:@"on.interstitial.dismissed" withData:nil];
}

- (void)ad:(id<GADFullScreenPresentingAd>)ad didFailToPresentFullScreenContentWithError:(NSError *)error {

    self.interstitialAd = nil;
    self.isLoading = NO;

    NSString *jsonStr = [NSString stringWithFormat:@"{\"message\":\"%@\"}", [error localizedDescription]];
    [self.plugin fireEvent:@"document" event:@"on.interstitial.failed.show" withData:jsonStr];
}

@end
