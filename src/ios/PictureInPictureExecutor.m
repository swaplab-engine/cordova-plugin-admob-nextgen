#import "PictureInPictureExecutor.h"
#import "AdMobNextGen.h"

@interface PictureInPictureExecutor()
@property (nonatomic, weak) AdMobNextGen *plugin;
@property (nonatomic, strong) GADPictureInPictureAd *pipAd;
@property (nonatomic, strong) GADPictureInPictureAdOptions *pipOptions;
@property (nonatomic, assign) BOOL isLoading;
@property (nonatomic, assign) BOOL isAutoShow;
@property (nonatomic, strong) NSString *currentAdUnitId;

@property (nonatomic, assign) NSTimeInterval lastLoadTime;
@property (nonatomic, assign) NSTimeInterval minLoadInterval;
@end

@implementation PictureInPictureExecutor

- (instancetype)initWithPlugin:(AdMobNextGen *)plugin {
    self = [super init];
    if (self) {
        self.plugin = plugin;
        self.isLoading = NO;
        self.isAutoShow = NO;
        self.lastLoadTime = 0;
        self.minLoadInterval = 5.0;
    }
    return self;
}

- (void)createPictureInPicture:(NSDictionary *)options command:(CDVInvokedUrlCommand *)command {
    NSString *adUnitId = options[@"adUnitId"];

    if (!adUnitId || adUnitId.length == 0) {
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

    GADPictureInPictureAdPosition position = GADPictureInPictureAdPositionDefault;
    NSString *posString = options[@"position"];
    if (posString) {
        if ([[posString uppercaseString] isEqualToString:@"TOP_LEFT"]) {
            position = GADPictureInPictureAdPositionTopLeft;
        } else if ([[posString uppercaseString] isEqualToString:@"TOP_RIGHT"]) {
            position = GADPictureInPictureAdPositionTopRight;
        } else if ([[posString uppercaseString] isEqualToString:@"BOTTOM_LEFT"]) {
            position = GADPictureInPictureAdPositionBottomLeft;
        } else if ([[posString uppercaseString] isEqualToString:@"BOTTOM_RIGHT"]) {
            position = GADPictureInPictureAdPositionBottomRight;
        }
    }

    GADPictureInPictureAdPresentationScope scope = GADPictureInPictureAdPresentationScopeScreen;
    NSString *scopeString = options[@"presentationScope"];
    if (scopeString && [[scopeString uppercaseString] isEqualToString:@"APPLICATION"]) {
        scope = GADPictureInPictureAdPresentationScopeApplication;
    }

    self.pipOptions = [[GADPictureInPictureAdOptions alloc] init];
    self.pipOptions.position = position;
    self.pipOptions.presentationScope = scope;

    [self loadPictureInPicture:adUnitId command:command];
}

- (void)loadPictureInPicture:(NSString *)adUnitId command:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];

        if (self.isLoading) {

            return;
        }

        if (self.pipAd != nil && [self.currentAdUnitId isEqualToString:adUnitId]) {

            [self.plugin fireEvent:@"document" event:@"on.pip.loaded" withData:nil];

            if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ad Loaded (Cached)"];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            if (self.isAutoShow) {
                [self showPictureInPictureAd:nil];
            }
            return;
        }

        if ((currentTime - self.lastLoadTime) < self.minLoadInterval) {
            NSString *errorMsg = [NSString stringWithFormat:@"Request too fast. Please wait %.0fms to prevent invalid traffic.", (self.minLoadInterval * 1000.0)];
            if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMsg];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            return;
        }

        self.isLoading = YES;
        self.lastLoadTime = currentTime;
        self.currentAdUnitId = adUnitId;

        if (command) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Loading started..."];
            [pluginResult setKeepCallbackAsBool:YES];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }

        GADRequest *request = [GADRequest request];

        [GADPictureInPictureAd loadWithAdUnitID:adUnitId
                                        request:request
                              completionHandler:^(GADPictureInPictureAd * _Nullable ad, NSError * _Nullable error) {
            self.isLoading = NO;

            if (error) {

                NSString *jsonStr = [NSString stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}", (long)error.code, [error localizedDescription]];
                [self.plugin fireEvent:@"document" event:@"on.pip.failed.load" withData:jsonStr];

                if (command) {
                    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]];
                    [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
                return;
            }

            if (self.pipAd) {
                [self.pipAd hide];
            }

            self.pipAd = ad;
            self.pipAd.delegate = self;

            __weak typeof(self) weakSelf = self;
            self.pipAd.paidEventHandler = ^(GADAdValue * _Nonnull value) {
                NSString *jsonStr = [NSString stringWithFormat:@"{\"value\":%lld, \"currency\":\"%@\", \"precision\":%ld}",
                                     value.value.longLongValue, value.currencyCode, (long)value.precision];
                [weakSelf.plugin fireEvent:@"document" event:@"on.pip.revenue" withData:jsonStr];
            };

            [self.plugin fireEvent:@"document" event:@"on.pip.loaded" withData:nil];

            if (self.isAutoShow) {
                [self showPictureInPictureAd:command];
            } else if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Ad Loaded"];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        }];
    });
}

- (void)showPictureInPicture:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self showPictureInPictureAd:command];
    });
}

- (void)showPictureInPictureAd:(CDVInvokedUrlCommand *)command {
    if (self.pipAd != nil) {
        if ([UIApplication sharedApplication].applicationState != UIApplicationStateActive) {
            NSString *jsonStr = @"{\"code\":\"APP_IN_BACKGROUND\", \"message\":\"Ad presentation blocked: Application is currently in the background or inactive.\"}";
            [self.plugin fireEvent:@"document" event:@"on.pip.failed.show" withData:jsonStr];

            if (command) {
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Ad presentation blocked: Application is in background."];
                [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            return;
        }

        [self.pipAd showWithOptions:self.pipOptions];

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

- (void)hidePictureInPicture:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.pipAd) {
            [self.pipAd hide];
        }
        if (command) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    });
}

- (void)removePictureInPicture:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.pipAd) {
            [self.pipAd hide];
            self.pipAd = nil;
        }
        self.isLoading = NO;
        if (command) {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    });
}

#pragma mark - GADPictureInPictureAdDelegate

- (void)pictureInPictureAdDidShow:(GADPictureInPictureAd *)pictureInPictureAd {
    [self.plugin fireEvent:@"document" event:@"on.pip.shown" withData:nil];
}

- (void)pictureInPictureAdDidHide:(GADPictureInPictureAd *)pictureInPictureAd {
    [self.plugin fireEvent:@"document" event:@"on.pip.hidden" withData:nil];
}

- (void)pictureInPictureAdDidFailToShow:(GADPictureInPictureAd *)pictureInPictureAd withError:(NSError *)error {
    NSString *jsonStr = [NSString stringWithFormat:@"{\"message\":\"%@\"}", [error localizedDescription]];
    [self.plugin fireEvent:@"document" event:@"on.pip.failed.show" withData:jsonStr];
}

- (void)pictureInPictureAdDidRecordImpression:(GADPictureInPictureAd *)pictureInPictureAd {
    [self.plugin fireEvent:@"document" event:@"on.pip.impression" withData:nil];
}

- (void)pictureInPictureAdDidRecordClick:(GADPictureInPictureAd *)pictureInPictureAd {
    [self.plugin fireEvent:@"document" event:@"on.pip.clicked" withData:nil];
}

- (void)pictureInPictureAdWillPresentScreen:(GADPictureInPictureAd *)pictureInPictureAd {
    [self.plugin fireEvent:@"document" event:@"on.pip.opened" withData:nil];
}

- (void)pictureInPictureAdWillDismissScreen:(GADPictureInPictureAd *)pictureInPictureAd {

}

- (void)pictureInPictureAdDidDismissScreen:(GADPictureInPictureAd *)pictureInPictureAd {
    [self.plugin fireEvent:@"document" event:@"on.pip.closed" withData:nil];
}

@end
