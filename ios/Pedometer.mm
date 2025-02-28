#import "Pedometer.h"
#import <CoreMotion/CoreMotion.h>

@interface Pedometer()
@property (nonatomic, strong) CMPedometer *pedometer;
@end

@implementation Pedometer
RCT_EXPORT_MODULE()

- (instancetype)init {
    self = [super init];
    if (self) {
        _pedometer = [[CMPedometer alloc] init];
    }
    return self;
}

- (void)isAvailable:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject
{
    BOOL available = [CMPedometer isStepCountingAvailable];
    resolve(@(available));
}

- (void)requestPermission:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject
{
    NSDate *start = [NSDate dateWithTimeIntervalSinceNow:-86400]; // 24時間前
    NSDate *end = [NSDate date];
    
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.pedometer queryPedometerDataFromDate:start toDate:end withHandler:^(CMPedometerData * _Nullable pedometerData, NSError * _Nullable error) {
            if (error) {
                NSLog(@"Pedometer Error: %@", error.localizedDescription);
                reject(@"permission_denied", @"Motion activity permission denied", error);
            } else {
                NSLog(@"Pedometer Permission granted");
                resolve(@(YES));
            }
        }];
    });
}

- (void)startTracking:(RCTPromiseResolveBlock)resolve
       reject:(RCTPromiseRejectBlock)reject
{
    NSDate *start = [NSDate date];
    
    [self.pedometer startPedometerUpdatesFromDate:start withHandler:^(CMPedometerData * _Nullable pedometerData, NSError * _Nullable error) {
        if (error) {
            NSLog(@"Error: %@", error);
        }
        if (pedometerData) {
            NSLog(@"Steps: %@", pedometerData.numberOfSteps);
        }
    }];
    
    resolve(nil);
}

- (void)stopTracking:(RCTPromiseResolveBlock)resolve
      reject:(RCTPromiseRejectBlock)reject
{
    [self.pedometer stopPedometerUpdates];
    resolve(nil);
}

- (void)queryCount:(double)from
                to:(double)to
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject
{
    NSDate *startDate = [NSDate dateWithTimeIntervalSince1970:from / 1000];
    NSDate *endDate = [NSDate dateWithTimeIntervalSince1970:to / 1000];
    
    [self.pedometer queryPedometerDataFromDate:startDate toDate:endDate withHandler:^(CMPedometerData * _Nullable pedometerData, NSError * _Nullable error) {
        if (error) {
            reject(@"error", @"Failed to query step count", error);
        } else if (pedometerData) {
            resolve(pedometerData.numberOfSteps);
        } else {
            resolve(@(0));
        }
    }];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativePedometerSpecJSI>(params);
}

@end