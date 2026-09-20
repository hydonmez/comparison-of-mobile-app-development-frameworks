#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(LaunchPerformanceNative, RCTEventEmitter)

RCT_EXTERN_METHOD(requiresMainQueueSetup)

// Export the supportedEvents method to the React Native bridge
RCT_EXTERN_METHOD(supportedEvents)

@end