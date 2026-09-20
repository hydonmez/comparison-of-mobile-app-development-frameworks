import 'dart:async';
import 'dart:io';
import 'package:sensors_plus/sensors_plus.dart';
import 'package:pedometer/pedometer.dart';
import 'package:permission_handler/permission_handler.dart';

/// A high-frequency hardware telemetry manager.
///
/// Requests data at a high frequency (10ms interval) to measure raw architectural overhead,
/// while implementing a strict 60ms (16Hz) emission throttle at the application layer
/// to prevent UI thread blockage.
class SensorTestManager {
  // Singleton pattern for global access and strict allocation control.
  static final SensorTestManager shared = SensorTestManager._internal();
  SensorTestManager._internal();

  // Callbacks
  void Function(List<double>)? _onAccelUpdate;
  void Function(List<double>)? _onGyroUpdate;
  void Function(List<double>)? _onMagnetUpdate;
  void Function(int)? _onStepUpdate;

  // Stream Subscriptions
  StreamSubscription<AccelerometerEvent>? _accelSub;
  StreamSubscription<GyroscopeEvent>? _gyroSub;
  StreamSubscription<MagnetometerEvent>? _magnetSub;
  StreamSubscription<StepCount>? _stepSub;

  // Temporal Throttling Registers
  final Stopwatch _throttleClock = Stopwatch();
  int _lastAccelUpdate = 0;
  int _lastGyroUpdate = 0;
  int _lastMagnetUpdate = 0;
  int _initialStepCount = -1;

  bool _isRegistered = false;

  /// Initializes hardware sensors and binds the execution context to platform channels.
  Future<void> startSensors({
    required void Function(List<double>) accelCallback,
    required void Function(List<double>) gyroCallback,
    required void Function(List<double>) magnetCallback,
    required void Function(int) stepCallback,
  }) async {
    // Safeguard: Prevent multiple registrations.
    if (_isRegistered) stopSensors();

    _onAccelUpdate = accelCallback;
    _onGyroUpdate = gyroCallback;
    _onMagnetUpdate = magnetCallback;
    _onStepUpdate = stepCallback;

    _lastAccelUpdate = 0;
    _lastGyroUpdate = 0;
    _lastMagnetUpdate = 0;
    _initialStepCount = -1;

    _throttleClock.start();

    // Sets the sensor sampling period to 10ms for high-frequency data ingestion.
    const Duration sensorConfig = Duration(milliseconds: 10);

    // 1. ACCELEROMETER
    _accelSub = accelerometerEventStream(samplingPeriod: sensorConfig).listen((
      event,
    ) {
      final int now = _throttleClock.elapsedMilliseconds;
      // Applies a 60ms application-layer throttle.
      if (now - _lastAccelUpdate > 60) {
        _lastAccelUpdate = now;
        _onAccelUpdate?.call([event.x, event.y, event.z]);
      }
    });

    // 2. GYROSCOPE
    _gyroSub = gyroscopeEventStream(samplingPeriod: sensorConfig).listen((
      event,
    ) {
      final int now = _throttleClock.elapsedMilliseconds;
      if (now - _lastGyroUpdate > 60) {
        _lastGyroUpdate = now;
        _onGyroUpdate?.call([event.x, event.y, event.z]);
      }
    });

    // 3. MAGNETOMETER
    _magnetSub = magnetometerEventStream(samplingPeriod: sensorConfig).listen((
      event,
    ) {
      final int now = _throttleClock.elapsedMilliseconds;
      if (now - _lastMagnetUpdate > 60) {
        _lastMagnetUpdate = now;
        _onMagnetUpdate?.call([event.x, event.y, event.z]);
      }
    });

    // 4. PEDOMETER
    await _initializePedometer();

    _isRegistered = true;
  }

  /// Sets up the pedometer stream, assuming permissions were handled upstream.
  Future<void> _initializePedometer() async {
    try {
      bool permissionGranted = false;

      if (Platform.isAndroid) {
        // Checks for existing permission status without triggering a new request.
        final status = await Permission.activityRecognition.status;
        permissionGranted = status.isGranted;
      } else if (Platform.isIOS) {
        permissionGranted = true;
      }

      if (permissionGranted) {
        _stepSub = Pedometer.stepCountStream.listen(
          (event) {
            if (_initialStepCount == -1) {
              _initialStepCount = event.steps;
            }
            // Calculate absolute difference from the baseline step count.
            final int currentSteps = event.steps - _initialStepCount;
            _onStepUpdate?.call(currentSteps >= 0 ? currentSteps : 0);
          },
          onError: (error) {
            print("🛑 PEDOMETER ERROR: $error");
          },
        );
      } else {
        print("🛑 PEDOMETER HALTED: Permission not granted upstream.");
      }
    } catch (e) {
      print("🛑 PEDOMETER INITIALIZATION FAILED: $e");
    }
  }

  /// Gracefully terminates hardware listeners and resets states.
  void stopSensors() {
    _accelSub?.cancel();
    _gyroSub?.cancel();
    _magnetSub?.cancel();
    _stepSub?.cancel();

    _accelSub = null;
    _gyroSub = null;
    _magnetSub = null;
    _stepSub = null;

    _throttleClock.stop();
    _throttleClock.reset();

    _onAccelUpdate = null;
    _onGyroUpdate = null;
    _onMagnetUpdate = null;
    _onStepUpdate = null;

    _isRegistered = false;
    _initialStepCount = -1;
  }
}
