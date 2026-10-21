#import "BannerExecutor.h"
#import "AdMobNextGen.h"

@interface BoundsObserverView : UIView
@property (nonatomic, copy) void (^onBoundsChanged)(CGRect bounds);
@end

@implementation BoundsObserverView
- (void)layoutSubviews {
    [super layoutSubviews];
    if (self.onBoundsChanged) {
        self.onBoundsChanged(self.bounds);
    }
}
@end

@interface BannerExecutor ()

@property(nonatomic, weak) AdMobNextGen *plugin;
@property(nonatomic, strong) GADBannerView *bannerView;
@property(nonatomic, strong) GADBannerView *pendingBannerView;

@property(nonatomic, strong) BoundsObserverView *boundsObserverView;
@property(nonatomic, assign) CGFloat lastObservedWidth;

@property(nonatomic, strong) NSString *lastAdUnitId;
@property(nonatomic, strong) NSString *lastSizeStr;
@property(nonatomic, strong) NSString *currentPosition;
@property(nonatomic, strong) NSString *lastPosition;
@property(nonatomic, assign) GADAdSize lastAdSize;

@property(nonatomic, assign) BOOL isBannerVisible;
@property(nonatomic, assign) BOOL isLoading;
@property(nonatomic, assign) BOOL isOverlapping;
@property(nonatomic, assign) BOOL isAutoShow;
@property(nonatomic, assign) BOOL isCollapsible;

@property(nonatomic, assign) CGFloat activeBannerHeight;

@property(nonatomic, assign) NSTimeInterval lastLoadTime;
@property(nonatomic, assign) NSTimeInterval minLoadInterval;

@end

@implementation BannerExecutor

- (instancetype)initWithPlugin:(AdMobNextGen *)plugin {
    self = [super init];
    if (self) {
        self.plugin = plugin;
        self.lastAdUnitId = @"";
        self.lastSizeStr = @"";

        self.currentPosition = @"bottom";
        self.lastPosition = @"bottom";

        self.isBannerVisible = NO;
        self.isLoading = NO;
        self.isOverlapping = YES;
        self.isAutoShow = YES;
        self.isCollapsible = NO;

        self.activeBannerHeight = 0;
        self.lastLoadTime = 0;
        self.minLoadInterval = 5.0;

    }
    return self;
}

- (void)dealloc {
    dispatch_async(dispatch_get_main_queue(), ^{
            [self.boundsObserverView removeFromSuperview];
            self.boundsObserverView = nil;
        });
}

#pragma mark - Smart Window Helper

- (UIWindow *)getKeyWindow {
    UIWindow *window = nil;
    if (@available(iOS 13.0, *)) {
        for (UIWindowScene *windowScene in [UIApplication sharedApplication]
                 .connectedScenes) {
            if (windowScene.activationState ==
                UISceneActivationStateForegroundActive) {
                for (UIWindow *w in windowScene.windows) {
                    if (w.isKeyWindow) {
                        window = w;
                        break;
                    }
                }
            }
            if (window)
                break;
        }
    }
    if (!window) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
        window = [UIApplication sharedApplication].keyWindow;
#pragma clang diagnostic pop
    }
    return window;
}

- (void)startObservingBounds {
    if (self.boundsObserverView != nil) return;

    UIViewController *rootVC = self.plugin.viewController;
    if (!rootVC) return;

    self.lastObservedWidth = rootVC.view.bounds.size.width;

    BoundsObserverView *observerView = [[BoundsObserverView alloc] init];
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
        dispatch_async(dispatch_get_main_queue(), ^{
            [weakSelf layoutViews];

            CGFloat currentWidth = bounds.size.width;
            if (fabs(weakSelf.lastObservedWidth - currentWidth) > 1.0) {
                weakSelf.lastObservedWidth = currentWidth;

                if (weakSelf.lastAdUnitId.length > 0) {
                    NSString *jsonStr = @"{\"orientation\":\"RESIZE\"}";
                    [weakSelf.plugin fireEvent:@"document" event:@"on.banner.orientation.changed" withData:jsonStr];
                }
            }
        });
    };

    self.boundsObserverView = observerView;
}

#pragma mark - Main Methods

- (void)createBanner:(NSDictionary *)options
             command:(CDVInvokedUrlCommand *)command {
    NSString *adUnitId = options[@"adUnitId"];
    NSString *requestedSize = options[@"size"] ? options[@"size"] : @"ADAPTIVE";
    NSString *newPosition =
        options[@"position"] ? options[@"position"] : self.currentPosition;

    BOOL newIsOverlapping =
        options[@"isOverlapping"] ? [options[@"isOverlapping"] boolValue] : YES;
    BOOL newIsAutoShow =
        options[@"isAutoShow"] ? [options[@"isAutoShow"] boolValue] : YES;
    BOOL newIsCollapsible =
        options[@"collapsible"] ? [options[@"collapsible"] boolValue] : NO;
    NSTimeInterval newMinLoadInterval =
        options[@"retryInterval"]
            ? [options[@"retryInterval"] doubleValue] / 1000.0
            : 5.0;

    if (!adUnitId) {
        CDVPluginResult *pluginResult =
            [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                              messageAsString:@"adUnitId is required."];
        [self.plugin.commandDelegate sendPluginResult:pluginResult
                                           callbackId:command.callbackId];
        return;
    }

    dispatch_async(dispatch_get_main_queue(), ^{

      [self startObservingBounds];

      NSTimeInterval currentTime = [[NSDate date] timeIntervalSince1970];

      if (self.isLoading) {
          CDVPluginResult *pluginResult =
              [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                messageAsString:@"Banner loading"];
          [self.plugin.commandDelegate sendPluginResult:pluginResult
                                             callbackId:command.callbackId];
          return;
      }

      BOOL isSameId = [self.lastAdUnitId isEqualToString:adUnitId];
      BOOL isSameSize = [self.lastSizeStr isEqualToString:requestedSize];

      self.isOverlapping = newIsOverlapping;
      self.isAutoShow = newIsAutoShow;
      self.isCollapsible = newIsCollapsible;
      self.minLoadInterval = newMinLoadInterval;
      self.currentPosition = newPosition;

      if (self.bannerView != nil && isSameId && isSameSize) {
          if (self.isAutoShow) {
              [self showBannerInternal];
              CDVPluginResult *pluginResult =
                  [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                    messageAsString:@"Banner Updated (Cached)"];
              [self.plugin.commandDelegate sendPluginResult:pluginResult
                                                 callbackId:command.callbackId];
          } else {
              [self hideBannerInternal];
              CDVPluginResult *pluginResult =
                  [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                    messageAsString:@"Banner Hidden (Cached)"];
              [self.plugin.commandDelegate sendPluginResult:pluginResult
                                                 callbackId:command.callbackId];
          }
          [self sendLoadedEvent:self.lastAdSize
                  isCollapsible:self.isCollapsible
                      isRefresh:NO];
          return;
      }

      if ((currentTime - self.lastLoadTime) < self.minLoadInterval) {
          NSString *errorMsg =
              [NSString stringWithFormat:@"Request too fast. Please wait "
                                         @"%.0fms to prevent invalid traffic.",
                                         (self.minLoadInterval * 1000.0)];

          CDVPluginResult *pluginResult =
              [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                messageAsString:errorMsg];
          [self.plugin.commandDelegate sendPluginResult:pluginResult
                                             callbackId:command.callbackId];
          return;
      }

      self.lastPosition = self.currentPosition;
      [self loadBanner:adUnitId size:requestedSize command:command];
    });
}

- (void)loadBanner:(NSString *)adUnitId
              size:(NSString *)sizeStr
           command:(CDVInvokedUrlCommand *)command {

    self.isLoading = YES;
    self.lastLoadTime = [[NSDate date] timeIntervalSince1970];
    self.lastAdUnitId = adUnitId;
    self.lastSizeStr = sizeStr;

    UIViewController *rootViewController = self.plugin.viewController;

    GADAdSize adSize = [self getAdSize:sizeStr];
    self.lastAdSize = adSize;

    GADBannerView *pendingView = [[GADBannerView alloc] initWithAdSize:adSize];
    pendingView.adUnitID = adUnitId;
    pendingView.rootViewController = rootViewController;
    pendingView.delegate = self;
    pendingView.hidden = YES;

    __weak typeof(self) weakSelf = self;
    pendingView.paidEventHandler = ^(GADAdValue *_Nonnull value) {
      NSString *jsonStr = [NSString
          stringWithFormat:
              @"{\"value\":%lld, \"currency\":\"%@\", \"precision\":%ld}",
              value.value.longLongValue, value.currencyCode,
              (long)value.precision];
      [weakSelf.plugin fireEvent:@"document"
                           event:@"on.banner.revenue"
                        withData:jsonStr];
    };

    self.pendingBannerView = pendingView;
    [rootViewController.view addSubview:self.pendingBannerView];

    GADRequest *request = [GADRequest request];

    if (self.isCollapsible) {
        GADExtras *extras = [[GADExtras alloc] init];
        NSString *anchor =
            [self.currentPosition isEqualToString:@"top"] ? @"top" : @"bottom";
        extras.additionalParameters = @{@"collapsible" : anchor};
        [request registerAdNetworkExtras:extras];
    }

    [self.pendingBannerView loadRequest:request];

    CDVPluginResult *pluginResult =
        [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                          messageAsString:@"Banner creation initiated."];
    [self.plugin.commandDelegate sendPluginResult:pluginResult
                                       callbackId:command.callbackId];
}

- (GADAdSize)getAdSize:(NSString *)sizeStr {
    if ([sizeStr isEqualToString:@"BANNER"])
        return GADAdSizeBanner;
    if ([sizeStr isEqualToString:@"LARGE_BANNER"])
        return GADAdSizeLargeBanner;
    if ([sizeStr isEqualToString:@"MEDIUM_RECTANGLE"])
        return GADAdSizeMediumRectangle;
    if ([sizeStr isEqualToString:@"FULL_BANNER"])
        return GADAdSizeFullBanner;
    if ([sizeStr isEqualToString:@"LEADERBOARD"])
        return GADAdSizeLeaderboard;

        UIWindow *window = [self getKeyWindow];
        CGFloat screenW = window ? window.bounds.size.width : UIScreen.mainScreen.bounds.size.width;

        if (window) {
            screenW -= (window.safeAreaInsets.left + window.safeAreaInsets.right);
        }

        if ([sizeStr isEqualToString:@"LARGE_ANCHORED_ADAPTIVE"]) {
            return GADLargeAnchoredAdaptiveBannerAdSizeWithWidth(screenW);
        }

    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wdeprecated-declarations"
        return GADCurrentOrientationAnchoredAdaptiveBannerAdSizeWithWidth(screenW);
    #pragma clang diagnostic pop
}

- (void)layoutViews {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *rootVC = self.plugin.viewController;
        UIView *webView = self.plugin.webView;
        if (!rootVC || !webView) return;

        UIWindow *window = [self getKeyWindow];
        if (!window) return;

        UIEdgeInsets safeArea = window.safeAreaInsets;

        CGFloat screenH = window.bounds.size.height;
        CGFloat screenW = window.bounds.size.width;

        if (safeArea.bottom == 0 && screenH >= 812.0) safeArea.bottom = 34.0;
        if (safeArea.top == 0 && screenH >= 812.0) safeArea.top = 44.0;

        CGRect fullScreenRect = CGRectMake(0, 0, screenW, screenH);

        if (self.isBannerVisible && self.bannerView && self.activeBannerHeight > 0) {
            CGSize adSize = self.bannerView.intrinsicContentSize;
            CGFloat bH = adSize.height > 0 ? adSize.height : self.activeBannerHeight;
            CGFloat bW = adSize.width > 0 ? adSize.width : self.bannerView.bounds.size.width;
            CGFloat bX = (screenW - bW) / 2.0;
            CGFloat bY = 0;

            if ([self.currentPosition isEqualToString:@"top"]) {
                bY = safeArea.top;
                self.bannerView.frame = CGRectMake(bX, bY, bW, bH);
                self.bannerView.hidden = NO;

                if (!self.isOverlapping) {
                    CGRect newWebFrame = fullScreenRect;

                    newWebFrame.origin.y = bY + bH;
                    newWebFrame.size.height = screenH - (bY + bH);
                    webView.frame = newWebFrame;
                } else {
                    webView.frame = fullScreenRect;
                }
            } else {
                bY = screenH - safeArea.bottom - bH;
                self.bannerView.frame = CGRectMake(bX, bY, bW, bH);
                self.bannerView.hidden = NO;

                if (!self.isOverlapping) {
                    CGRect newWebFrame = fullScreenRect;
                    newWebFrame.origin.y = 0;
                    newWebFrame.size.height = bY;
                    webView.frame = newWebFrame;
                } else {
                    webView.frame = fullScreenRect;
                }
            }
        } else {
            if (self.bannerView) self.bannerView.hidden = YES;
            webView.frame = fullScreenRect;
        }

        if (self.bannerView && self.isBannerVisible) {
            [webView.superview bringSubviewToFront:self.bannerView];
        }
    });
}

#pragma mark - Smart Visibility Control

- (void)showBannerInternal {
    if (self.bannerView) {
        self.isBannerVisible = YES;
        [self layoutViews];
    }
}

- (void)hideBannerInternal {
    if (self.bannerView) {
        self.isBannerVisible = NO;
        [self layoutViews];
    }
}

- (void)destroyBannerInternal {
    if (self.bannerView) {
        self.isBannerVisible = NO;
        self.activeBannerHeight = 0;

        [self layoutViews];

        [self.bannerView removeFromSuperview];
        self.bannerView.delegate = nil;
        self.bannerView = nil;
    }
}

- (void)showBanner:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
      if (self.bannerView) {
          [self showBannerInternal];
          CDVPluginResult *pluginResult =
              [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
          [self.plugin.commandDelegate sendPluginResult:pluginResult
                                             callbackId:command.callbackId];
      } else {
          CDVPluginResult *pluginResult =
              [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                messageAsString:@"No banner loaded"];
          [self.plugin.commandDelegate sendPluginResult:pluginResult
                                             callbackId:command.callbackId];
      }
    });
}

- (void)hideBanner:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
      [self hideBannerInternal];
      CDVPluginResult *pluginResult =
          [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
      [self.plugin.commandDelegate sendPluginResult:pluginResult
                                         callbackId:command.callbackId];
    });
}

- (void)removeBanner:(CDVInvokedUrlCommand *)command {
    dispatch_async(dispatch_get_main_queue(), ^{
      [self destroyBannerInternal];
      self.lastAdUnitId = @"";
      self.lastSizeStr = @"";
      CDVPluginResult *pluginResult =
          [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
      [self.plugin.commandDelegate sendPluginResult:pluginResult
                                         callbackId:command.callbackId];
    });
}

#pragma mark - Helper Events

- (void)sendLoadedEvent:(GADAdSize)adSize
          isCollapsible:(BOOL)isCollapsible
              isRefresh:(BOOL)isRefresh {
    NSString *jsonStr = [NSString
        stringWithFormat:@"{\"width\":%f, \"height\":%f, \"isCollapsible\":%s}",
                         adSize.size.width, adSize.size.height,
                         isCollapsible ? "true" : "false"];

    if (isRefresh) {
        [self.plugin fireEvent:@"document"
                         event:@"on.banner.refreshed"
                      withData:nil];
    } else {
        [self.plugin fireEvent:@"document"
                         event:@"on.banner.load"
                      withData:jsonStr];
    }
}

#pragma mark - GADBannerViewDelegate

- (void)bannerViewDidReceiveAd:(GADBannerView *)incomingBannerView {
    if (incomingBannerView == self.pendingBannerView) {
        self.isLoading = NO;

        [self.bannerView removeFromSuperview];
        self.bannerView.delegate = nil;

        self.bannerView = self.pendingBannerView;
        self.pendingBannerView = nil;

        self.activeBannerHeight = incomingBannerView.adSize.size.height;
        if (self.activeBannerHeight == 0) {
            self.activeBannerHeight = self.lastAdSize.size.height;
        }

        [self sendLoadedEvent:self.lastAdSize
                isCollapsible:self.isCollapsible
                    isRefresh:NO];

        if (self.isAutoShow && !self.isBannerVisible) {
            [self showBannerInternal];
        } else {
            [self layoutViews];
        }
    } else if (incomingBannerView == self.bannerView) {

        [self sendLoadedEvent:incomingBannerView.adSize
                isCollapsible:self.isCollapsible
                    isRefresh:YES];

        [self layoutViews];
    }
}

- (void)bannerView:(GADBannerView *)incomingBannerView
    didFailToReceiveAdWithError:(NSError *)error {
    if (incomingBannerView == self.pendingBannerView) {
        self.isLoading = NO;

        [incomingBannerView removeFromSuperview];
        incomingBannerView.delegate = nil;
        self.pendingBannerView = nil;

        NSString *jsonStr = [NSString
            stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}",
                             (long)error.code, [error localizedDescription]];
        [self.plugin fireEvent:@"document"
                         event:@"on.banner.failed"
                      withData:jsonStr];
    } else if (incomingBannerView == self.bannerView) {

        NSString *jsonStr = [NSString
            stringWithFormat:@"{\"code\":%ld, \"message\":\"%@\"}",
                             (long)error.code, [error localizedDescription]];
        [self.plugin fireEvent:@"document"
                         event:@"on.banner.failedToRefresh"
                      withData:jsonStr];
    }
}

- (void)bannerViewDidRecordImpression:(GADBannerView *)bannerView {
    [self.plugin fireEvent:@"document"
                     event:@"on.banner.impression"
                  withData:nil];
}

- (void)bannerViewDidRecordClick:(GADBannerView *)bannerView {
    [self.plugin fireEvent:@"document" event:@"on.banner.clicked" withData:nil];
}

- (void)bannerViewWillPresentScreen:(GADBannerView *)bannerView {
    [self.plugin fireEvent:@"document" event:@"on.banner.opened" withData:nil];
}

- (void)bannerViewDidDismissScreen:(GADBannerView *)bannerView {
    [self.plugin fireEvent:@"document" event:@"on.banner.closed" withData:nil];
}

@end
