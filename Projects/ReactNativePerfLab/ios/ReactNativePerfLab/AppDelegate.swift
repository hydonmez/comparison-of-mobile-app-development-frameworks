import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

  var window: UIWindow?
  var reactNativeDelegate: ReactNativeDelegate?
  var reactNativeFactory: RCTReactNativeFactory?

  // MARK: - High-Resolution Clock Calibration
  public static var bootMillis: Double = 0.0
  public static var bootNanos: UInt64 = 0
  @objc public static var osStartEpochMs: Double = 0.0

  static func getHighResEpochMs() -> Double {
    let currentNanos = DispatchTime.now().uptimeNanoseconds
    let elapsedMs = Double(currentNanos - bootNanos) / 1_000_000.0
    return bootMillis + elapsedMs
  }

  override init() {
    super.init()
    AppDelegate.bootMillis = Date().timeIntervalSince1970 * 1000.0
    AppDelegate.bootNanos = DispatchTime.now().uptimeNanoseconds
    AppDelegate.osStartEpochMs = AppDelegate.getHighResEpochMs()
  }

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    let delegate = ReactNativeDelegate()
    let factory = RCTReactNativeFactory(delegate: delegate)
    delegate.dependencyProvider = RCTAppDependencyProvider()

    reactNativeDelegate = delegate
    reactNativeFactory = factory

    window = UIWindow(frame: UIScreen.main.bounds)

    factory.startReactNative(
      withModuleName: "ReactNativePerfLab",
      in: window,
      launchOptions: launchOptions
    )

    return true
  }

  // Orientation.getOrientation()
  func application(
    _ application: UIApplication,
    supportedInterfaceOrientationsFor window: UIWindow?
  ) -> UIInterfaceOrientationMask {
    return Orientation.getOrientation()
  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    self.bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}