#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(HardwareTelemetryModule, NSObject)

// Using "reject" instead of "withRejecter" to match Swift's naming convention.
RCT_EXTERN_METHOD(getOptimizedRAMUsage:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(syncCpuBaseline:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

// Using "resolver:" and "rejecter:" labels to match the Swift file implementation.
RCT_EXTERN_METHOD(getProcessCpuUsage:(double)deltaSeconds
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getBatteryLevel:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getThermalStateString:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end