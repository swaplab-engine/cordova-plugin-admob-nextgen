#import "GlobalSettingsExecutor.h"
#import "AdMobNextGen.h"

@interface GlobalSettingsExecutor()
@property (nonatomic, weak) AdMobNextGen *plugin;
@end

@implementation GlobalSettingsExecutor

- (instancetype)initWithPlugin:(AdMobNextGen *)plugin {
    self = [super init];
    if (self) {
        self.plugin = plugin;
    }
    return self;
}

- (void)setAppVolume:(CDVInvokedUrlCommand *)command {
    if (command.arguments.count > 0) {
        NSNumber *volumeNum = [command.arguments objectAtIndex:0];
        if ([volumeNum isKindOfClass:[NSNumber class]]) {
            float volume = [volumeNum floatValue];
            GADMobileAds.sharedInstance.applicationVolume = volume;

            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid volume value"];
    [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setAppMuted:(CDVInvokedUrlCommand *)command {
    if (command.arguments.count > 0) {
        NSNumber *mutedNum = [command.arguments objectAtIndex:0];
        if ([mutedNum isKindOfClass:[NSNumber class]]) {
            BOOL muted = [mutedNum boolValue];
            GADMobileAds.sharedInstance.applicationMuted = muted;

            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            return;
        }
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid mute value"];
    [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setRequestConfiguration:(CDVInvokedUrlCommand *)command {
    if (command.arguments.count == 0) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Configuration object required"];
        [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    NSDictionary *config = [command.arguments objectAtIndex:0];
    if (![config isKindOfClass:[NSDictionary class]]) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid configuration format"];
        [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }

    GADRequestConfiguration *requestConfiguration = GADMobileAds.sharedInstance.requestConfiguration;

    NSNumber *coppaTag = [self parseBooleanFromDict:config key:@"tagForChildDirectedTreatment"];
    NSNumber *tfuaTag = [self parseBooleanFromDict:config key:@"tagForUnderAgeOfConsent"];

    if (coppaTag != nil && [coppaTag boolValue]) {
        requestConfiguration.ageRestrictedTreatment = GADAgeRestrictedTreatmentChild;

    } else if (tfuaTag != nil && [tfuaTag boolValue]) {
        requestConfiguration.ageRestrictedTreatment = GADAgeRestrictedTreatmentTeen;

    } else if ((coppaTag != nil && ![coppaTag boolValue]) || 
               (tfuaTag != nil && ![tfuaTag boolValue])) {
        requestConfiguration.ageRestrictedTreatment = GADAgeRestrictedTreatmentUnspecified;

    }

    if (config[@"maxAdContentRating"] != nil) {
        NSString *rating = config[@"maxAdContentRating"];
        if ([rating isEqualToString:@"G"]) {
            requestConfiguration.maxAdContentRating = GADMaxAdContentRatingGeneral;
        } else if ([rating isEqualToString:@"PG"]) {
            requestConfiguration.maxAdContentRating = GADMaxAdContentRatingParentalGuidance;
        } else if ([rating isEqualToString:@"T"]) {
            requestConfiguration.maxAdContentRating = GADMaxAdContentRatingTeen;
        } else if ([rating isEqualToString:@"MA"]) {
            requestConfiguration.maxAdContentRating = GADMaxAdContentRatingMatureAudience;
        }

    }

    if (config[@"testDeviceIds"] != nil && [config[@"testDeviceIds"] isKindOfClass:[NSArray class]]) {
        NSArray *ids = config[@"testDeviceIds"];
        NSMutableArray<NSString *> *testDevices = [NSMutableArray array];
        for (id deviceId in ids) {
            if ([deviceId isKindOfClass:[NSString class]]) {
                [testDevices addObject:deviceId];
            }
        }
        requestConfiguration.testDeviceIdentifiers = testDevices;

    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Configuration Updated"];
    [self.plugin.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark - Helper Parsers

- (NSNumber *)parseBooleanFromDict:(NSDictionary *)dict key:(NSString *)key {
    id value = dict[key];

    if (value == nil || [value isKindOfClass:[NSNull class]]) {
        return nil; 
    }

    if ([value isKindOfClass:[NSNumber class]]) {
        return [value boolValue] ? @YES : @NO;
    }

    if ([value isKindOfClass:[NSString class]]) {
        NSString *strVal = (NSString *)value;
        if ([[strVal lowercaseString] isEqualToString:@"true"]) {
            return @YES;
        }
        if ([[strVal lowercaseString] isEqualToString:@"false"]) {
            return @NO;
        }
    }

    return nil;
}

@end
