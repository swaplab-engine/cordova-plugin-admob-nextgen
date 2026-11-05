#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>
#import <GoogleMobileAds/GoogleMobileAds.h>
#import <GoogleMobileAds/GoogleMobileAds_Beta.h>

@class AdMobNextGen;

@interface PictureInPictureExecutor : NSObject <GADPictureInPictureAdDelegate>

- (instancetype)initWithPlugin:(AdMobNextGen *)plugin;

- (void)createPictureInPicture:(NSDictionary *)options command:(CDVInvokedUrlCommand *)command;
- (void)showPictureInPicture:(CDVInvokedUrlCommand *)command;
- (void)hidePictureInPicture:(CDVInvokedUrlCommand *)command;
- (void)removePictureInPicture:(CDVInvokedUrlCommand *)command;

@end
