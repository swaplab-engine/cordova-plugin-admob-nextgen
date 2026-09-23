#import "AppOpenAdExecutor.h"
#import "AdMobNextGen.h"

@interface AppOpenAdExecutor()

@property (nonatomic, weak) AdMobNextGen *plugin;
@property (nonatomic, strong) GADAppOpenAd *appOpenAd;

@property (nonatomic, assign) BOOL isLoadingAd;
@property (nonatomic, assign) BOOL isShowingAd;
@property (nonatomic, strong) NSDate *loadTime;

@property (nonatomic, assign) BOOL isAutoShow;
@property (nonatomic, strong) NSString *currentAdUnitId;

@property (nonatomic, assign) NSTimeInterval lastLoadTime;
@property (nonatomic, assign) NSTimeInterval minLoadInterval;

@end

@implementation AppOpenAdExecutor

+ (instancetype)sharedInstance {
    static AppOpenAdExecutor *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[AppOpenAdExecutor alloc] init];
    });
    return instance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        self.isLoadingAd = NO;
        self.isShowingAd = NO;
        self.isAutoShow = NO;
        self.lastLoadTime = 0;
        self.minLoadInterval = 5.0; 
    }
    return self;
}

- (void)initializeWithPlugin:(AdMobNextGen *)plugin {
    self.plugin = plugin;
}

- (void)loadAppOpenAd:(NSDictionary *)options command:(CDVInvokedUrlCommand *)command {
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

    self.currentAdUnitId = adUnitId;
    [self loadAdInternal:adUnitId command:command];
}

- (void)loadAdInternal:(NSString *)adUnitId command:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];

        if (self.isLoadingAd || [self isAdAvailable]) {
            if ([self isAdAvailable]) {
                [self.plugin fireEvent:@"document" event:@"on.appopen.loaded" withData:nil];
                if (command) {
                    CDVPluginResult* res = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ad Ready"];
                    [self.plugin.commandDelegate sendPluginResult:res callbackId:command.callbackId];
                }

                if (self.isAutoShow) {
                    [self showAdIfAvailable];
                }

            }
            return;
        }

        if ((currentTime - self.lastLoadTime) < self.minLoadInterval) {

            NSString *errorMsg = [NSString stringWithFormat:@"Request too fast. Please wait %.0fms to prevent invalid traffic.", (self.minLoadInterval * 1000.0)];

            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMsg];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }

        self.isLoadingAd = YES;
        self.lastLoadTime = currentTime;

        if (command) {
            CDVPluginResult* res = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Loading started..."];
            [res setKeepCallbackAsBool:YES];
            [self.plugin.commandDelegate sendPluginResult:res callbackId:command.callbackId];
        }

        GADRequest *request = [GADRequest request];

        [GADAppOpenAd loadWithAdUnitID:adUnitId
                               request:request
                     completionHandler:^(GADAppOpenAd *ad, NSError *error) {
            self.isLoadingAd = NO;

            if (error) {

                NSString *jsonStr = [NSString stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}", (long)error.code, error.localizedDescription];
                [self.plugin fireEvent:@"document" event:@"on.appopen.failed.load" withData:jsonStr];
                return;
            }

            self.appOpenAd = ad;
            self.loadTime = [NSDate date];
            self.appOpenAd.fullScreenContentDelegate = self;

            __weak typeof(self) weakSelf = self;
            self.appOpenAd.paidEventHandler = ^(GADAdValue * _Nonnull value) {
                NSString *jsonStr = [NSString stringWithFormat:@"{\"value\":%lld, \"currency\":\"%@\", \"precision\":%ld}",
                                     value.value.longLongValue, value.currencyCode, (long)value.precision];
                [weakSelf.plugin fireEvent:@"document" event:@"on.appopen.revenue" withData:jsonStr];
            };

            [self.plugin fireEvent:@"document" event:@"on.appopen.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showAdIfAvailable];
            }
        }];
    });
}

- (void)showAppOpenAd:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self showAdIfAvailable];
        if (command) {
            CDVPluginResult* res = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:res callbackId:command.callbackId];
        }
    });
}

- (void)showAdIfAvailable {
    if (self.isShowingAd) return;

    if (![self isAdAvailable]) {
        NSString *jsonStr = @"{\"message\":\"Ad not ready or expired\"}";
        [self.plugin fireEvent:@"document" event:@"on.appopen.failed.show" withData:jsonStr];
        return;
    }

    if ([UIApplication sharedApplication].applicationState != UIApplicationStateActive) {
        NSString *jsonStr = @"{\"code\":\"APP_IN_BACKGROUND\", \"message\":\"Ad presentation blocked: Application is currently in the background or inactive.\"}";
        [self.plugin fireEvent:@"document" event:@"on.appopen.failed.show" withData:jsonStr];
        return;
    }

    self.isShowingAd = YES;
    [self.appOpenAd presentFromRootViewController:self.plugin.viewController];
}

#pragma mark - Expiration Logic (4 Hours Rule)

- (BOOL)wasLoadTimeLessThanNHoursAgo:(int)n {
    if (!self.loadTime) return NO;
    NSDate *now = [NSDate date];
    NSTimeInterval timeIntervalBetweenNowAndLoadTime = [now timeIntervalSinceDate:self.loadTime];
    double secondsPerHour = 3600.0;
    double intervalInHours = timeIntervalBetweenNowAndLoadTime / secondsPerHour;
    return intervalInHours < n;
}

- (BOOL)isAdAvailable {
    return self.appOpenAd != nil && [self wasLoadTimeLessThanNHoursAgo:4];
}

#pragma mark - GADFullScreenContentDelegate

- (void)adDidRecordImpression:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.appopen.impression" withData:nil];
}

- (void)adDidRecordClick:(id<GADFullScreenPresentingAd>)ad {
    [self.plugin fireEvent:@"document" event:@"on.appopen.clicked" withData:nil];
}

- (void)adWillPresentFullScreenContent:(id<GADFullScreenPresentingAd>)ad {
    self.isShowingAd = YES;
    [self.plugin fireEvent:@"document" event:@"on.appopen.shown" withData:nil];
}

- (void)adDidDismissFullScreenContent:(id<GADFullScreenPresentingAd>)ad {
    self.appOpenAd = nil;
    self.isShowingAd = NO;
    [self.plugin fireEvent:@"document" event:@"on.appopen.dismissed" withData:nil];
}

- (void)ad:(id<GADFullScreenPresentingAd>)ad didFailToPresentFullScreenContentWithError:(NSError *)error {
    self.appOpenAd = nil;
    self.isShowingAd = NO;

    NSString *jsonStr = [NSString stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}", (long)error.code, error.localizedDescription];
    [self.plugin fireEvent:@"document" event:@"on.appopen.failed.show" withData:jsonStr];
}

@end
