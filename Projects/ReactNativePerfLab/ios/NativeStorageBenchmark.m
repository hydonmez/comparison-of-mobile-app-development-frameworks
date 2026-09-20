// ios/NativeStorageBenchmark.m

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(NativeStorageBenchmark, RCTEventEmitter)

// Method signatures exposed to JavaScript
RCT_EXTERN_METHOD(writeData:(NSInteger)megabytes
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(readData:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelTask)

@end