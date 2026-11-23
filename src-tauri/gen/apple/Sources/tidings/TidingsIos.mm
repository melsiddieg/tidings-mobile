#import "TidingsIos.h"

#import <AVFoundation/AVFoundation.h>
#import <UIKit/UIKit.h>

@interface TidingsIosController : NSObject
@property (nonatomic, strong, nullable) UIVisualEffectView *privacyView;
@property (nonatomic, assign) BOOL appInactive;
+ (instancetype)shared;
- (void)install;
@end

@implementation TidingsIosController

+ (instancetype)shared {
    static TidingsIosController *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[TidingsIosController alloc] init];
    });
    return instance;
}

- (void)install {
    [self configureAudioSession];

    NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
    [nc addObserver:self
           selector:@selector(handleDidFinishLaunching:)
               name:UIApplicationDidFinishLaunchingNotification
             object:nil];
    [nc addObserver:self
           selector:@selector(handleWillResignActive:)
               name:UIApplicationWillResignActiveNotification
             object:nil];
    [nc addObserver:self
           selector:@selector(handleDidBecomeActive:)
               name:UIApplicationDidBecomeActiveNotification
             object:nil];
    [nc addObserver:self
           selector:@selector(handleAudioInterruption:)
               name:AVAudioSessionInterruptionNotification
             object:[AVAudioSession sharedInstance]];
    [nc addObserver:self
           selector:@selector(handleScreenCapturedDidChange:)
               name:UIScreenCapturedDidChangeNotification
             object:nil];
}

- (void)configureAudioSession {
    AVAudioSession *session = [AVAudioSession sharedInstance];
    NSError *error = nil;
    // AVAudioSessionCategoryPlayback + AVAudioSessionModeSpokenAudio keeps podcast
    // audio playing when the screen locks or the Ring/Silent switch is muted, and
    // automatically integrates with Bluetooth, AirPlay, CarPlay, and Lock Screen controls.
    [session setCategory:AVAudioSessionCategoryPlayback
                    mode:AVAudioSessionModeSpokenAudio
                 options:0
                   error:&error];
    [session setActive:YES error:nil];
}

- (nullable UIWindow *)activeWindow {
    for (UIScene *scene in [UIApplication sharedApplication].connectedScenes) {
        if (![scene isKindOfClass:[UIWindowScene class]]) {
            continue;
        }
        UIWindowScene *windowScene = (UIWindowScene *)scene;
        for (UIWindow *window in windowScene.windows) {
            if (window.isKeyWindow) {
                return window;
            }
        }
        if (windowScene.windows.firstObject != nil) {
            return windowScene.windows.firstObject;
        }
    }
    return nil;
}

- (BOOL)isAnyScreenCaptured {
    for (UIScene *scene in [UIApplication sharedApplication].connectedScenes) {
        if ([scene isKindOfClass:[UIWindowScene class]]) {
            UIWindowScene *windowScene = (UIWindowScene *)scene;
            if (windowScene.screen.isCaptured) {
                return YES;
            }
        }
    }
    return NO;
}

- (void)updatePrivacyShield {
    BOOL shouldHide = self.appInactive || [self isAnyScreenCaptured];
    if (shouldHide) {
        UIWindow *window = [self activeWindow];
        if (window == nil) {
            return;
        }
        if (self.privacyView == nil) {
            UIBlurEffect *blur = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemThickMaterial];
            UIVisualEffectView *view = [[UIVisualEffectView alloc] initWithEffect:blur];
            view.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
            view.userInteractionEnabled = YES;

            UILabel *label = [[UILabel alloc] init];
            label.text = @"Tidings";
            label.font = [UIFont systemFontOfSize:22 weight:UIFontWeightSemibold];
            label.textColor = [UIColor secondaryLabelColor];
            label.translatesAutoresizingMaskIntoConstraints = NO;
            [view.contentView addSubview:label];
            [NSLayoutConstraint activateConstraints:@[
                [label.centerXAnchor constraintEqualToAnchor:view.contentView.centerXAnchor],
                [label.centerYAnchor constraintEqualToAnchor:view.contentView.centerYAnchor],
            ]];

            self.privacyView = view;
        }
        self.privacyView.frame = window.bounds;
        if (self.privacyView.superview != window) {
            [window addSubview:self.privacyView];
        } else {
            [window bringSubviewToFront:self.privacyView];
        }
    } else if (self.privacyView != nil) {
        [self.privacyView removeFromSuperview];
    }
}

- (void)handleDidFinishLaunching:(NSNotification *)note {
    (void)note;
    [self configureAudioSession];
    [self updatePrivacyShield];
}

- (void)handleWillResignActive:(NSNotification *)note {
    (void)note;
    self.appInactive = YES;
    [self updatePrivacyShield];
}

- (void)handleDidBecomeActive:(NSNotification *)note {
    (void)note;
    self.appInactive = NO;
    [self configureAudioSession];
    [self updatePrivacyShield];
}

- (void)handleScreenCapturedDidChange:(NSNotification *)note {
    (void)note;
    dispatch_async(dispatch_get_main_queue(), ^{
        [self updatePrivacyShield];
    });
}

- (void)handleAudioInterruption:(NSNotification *)note {
    NSNumber *typeValue = note.userInfo[AVAudioSessionInterruptionTypeKey];
    if (typeValue != nil && typeValue.unsignedIntegerValue == AVAudioSessionInterruptionTypeEnded) {
        [self configureAudioSession];
    }
}

@end

void tidings_ios_setup(void) {
    [[TidingsIosController shared] install];
}
