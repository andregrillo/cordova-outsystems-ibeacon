/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

#import "AppDelegate+CDVLocationManager.h"
#import <objc/runtime.h>


BOOL isGrantedNotificationAccess;

@implementation AppDelegate (CDVLocationManager)


+ (void)load {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        
        Class class = [self class];

        SEL originalSelector = @selector(application:didFinishLaunchingWithOptions:);
        SEL swizzledSelector = @selector(xxx_application:didFinishLaunchingWithOptions:);
        
        Method originalMethod = class_getInstanceMethod(class, originalSelector);
        Method swizzledMethod = class_getInstanceMethod(class, swizzledSelector);
        
        BOOL didAddMethod = class_addMethod(class, originalSelector, method_getImplementation(swizzledMethod), method_getTypeEncoding(swizzledMethod));
        
        if (didAddMethod) {
            class_replaceMethod(class, swizzledSelector, method_getImplementation(originalMethod), method_getTypeEncoding(originalMethod));
        } else {
            method_exchangeImplementations(originalMethod, swizzledMethod);
        }
        
    });
}

- (BOOL) xxx_application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    
    //Granting notification permission
    isGrantedNotificationAccess = false;
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    UNAuthorizationOptions options = UNAuthorizationOptionAlert + UNAuthorizationOptionSound;
    [center requestAuthorizationWithOptions:options completionHandler:^(BOOL granted, NSError * _Nullable error) {
        if (error == nil) {
            isGrantedNotificationAccess = true;
        }
    }];
    center.delegate = self;
//    [self showNotification];

    
    
    BOOL launchedWithoutOptions = launchOptions == nil;
    
    if (!launchedWithoutOptions) {
        [self requestMoreBackgroundExecutionTime];
    }
    
    return [self xxx_application:application didFinishLaunchingWithOptions:launchOptions];
    
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completionHandler{

    UNNotificationPresentationOptions presentationOptions = UNNotificationPresentationOptionSound +UNNotificationPresentationOptionAlert;

    completionHandler(presentationOptions);
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center didReceiveNotificationResponse:(UNNotificationResponse *)response withCompletionHandler:(void (^)(void))completionHandler{
    
    // Notification was tapped while app was in background.
    NSString* notificationID = response.notification.request.identifier;
    
    [[NSUserDefaults standardUserDefaults] setObject:notificationID forKey:@"deepLink"];
        
    completionHandler();
    
}


- (void)showNotification{
    if (isGrantedNotificationAccess) {
        UNUserNotificationCenter * center = [UNUserNotificationCenter currentNotificationCenter];
        UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
        content.body = @"Welcome!";
        content.sound = [UNNotificationSound defaultSound];
        
        //Setting the notification trigger
        UNTimeIntervalNotificationTrigger *trigger = [UNTimeIntervalNotificationTrigger triggerWithTimeInterval:10 repeats:NO];
        
        //Setting request for notification
        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:@"OSLocalNotification" content:content trigger:trigger];
        
        //Adds notification to current notification center
        [center addNotificationRequest:request withCompletionHandler:nil];
    }
}

- (UIBackgroundTaskIdentifier) backgroundTaskIdentifier {
    NSNumber *asNumber = objc_getAssociatedObject(self, @selector(backgroundTaskIdentifier));
    UIBackgroundTaskIdentifier  taskId = [asNumber unsignedIntValue];
    return taskId;
}

- (void)setBackgroundTaskIdentifier:(UIBackgroundTaskIdentifier)backgroundTaskIdentifier {
    NSNumber *asNumber = [NSNumber numberWithUnsignedInt:backgroundTaskIdentifier];
    objc_setAssociatedObject(self, @selector(backgroundTaskIdentifier), asNumber, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (void) requestMoreBackgroundExecutionTime {

    UIApplication *application = [UIApplication sharedApplication];

    self.backgroundTaskIdentifier = [application beginBackgroundTaskWithExpirationHandler:^{
        self.backgroundTaskIdentifier = UIBackgroundTaskInvalid;

    }];
}


@end
