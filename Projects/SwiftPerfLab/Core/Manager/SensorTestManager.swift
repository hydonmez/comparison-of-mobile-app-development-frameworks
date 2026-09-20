import Foundation
import CoreMotion
import QuartzCore

/// A high-performance hardware sensor telemetry engine for cross-platform benchmarking.
///
/// Uses pure closure-based callbacks instead of Combine (`@Published`, `ObservableObject`).
/// Decoupling hardware polling from the UI rendering pipeline eliminates reactive broadcasting
/// overhead and UI diffing memory allocations. Closures are detached from `self` to avoid ARC overhead.
final class SensorEngine {
    
    static let shared = SensorEngine()
    
    private let motionManager = CMMotionManager()
    private let pedometer = CMPedometer()
    
    /// A dedicated serial background operation queue (`maxConcurrentOperationCount = 1`)
    /// offloads high-frequency hardware reads and ensures thread-safe state mutations within closures.
    private let sensorQueue: OperationQueue = {
        let queue = OperationQueue()
        queue.name = "com.perflab.sensorQueue"
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        return queue
    }()
    
    private init() {
        // Configured for an aggressive 100Hz (0.01s) sampling rate.
        // This forces the CPU to process 300 independent hardware interrupts per second across all three IMU sensors.
        motionManager.accelerometerUpdateInterval = 0.01
        motionManager.gyroUpdateInterval = 0.01
        motionManager.magnetometerUpdateInterval = 0.01
    }
    
    /// Initiates high-frequency sensor telemetry.
    ///
    /// - Parameters:
    ///   - onAccelUpdate: Closure invoked on the Main Thread with struct-based accelerometer data.
    ///   - onGyroUpdate: Closure invoked on the Main Thread with struct-based gyroscope data.
    ///   - onMagnetUpdate: Closure invoked on the Main Thread with struct-based magnetometer data.
    ///   - onStepUpdate: Closure invoked on the Main Thread with primitive pedometer data.
    func startSensors(
        onAccelUpdate: @escaping (CMAcceleration) -> Void,
        onGyroUpdate: @escaping (CMRotationRate) -> Void,
        onMagnetUpdate: @escaping (CMMagneticField) -> Void,
        onStepUpdate: @escaping (Int) -> Void
    ) {
        // The target minimum interval (in seconds) between callback dispatches to the Main Thread.
        // 0.06s yields approximately 15Hz, matching visual rendering requirements without CPU saturation.
        let throttleInterval: CFTimeInterval = 0.06
        
        // Accelerometer Telemetry
        if motionManager.isAccelerometerAvailable {
            var localLastAccelUpdate: CFTimeInterval = 0
            
            motionManager.startAccelerometerUpdates(to: sensorQueue) { data, _ in
                guard let data = data else { return }
                let now = CACurrentMediaTime()
                
                // Throttles updates to match the target interval.
                if now - localLastAccelUpdate > throttleInterval {
                    localLastAccelUpdate = now
                    
                    // Value type (struct) copy incurs zero heap allocation cost.
                    let acceleration = data.acceleration
                    
                    DispatchQueue.main.async {
                        onAccelUpdate(acceleration)
                    }
                }
            }
        }
        
        // Gyroscope Telemetry
        if motionManager.isGyroAvailable {
            var localLastGyroUpdate: CFTimeInterval = 0
            
            motionManager.startGyroUpdates(to: sensorQueue) { data, _ in
                guard let data = data else { return }
                let now = CACurrentMediaTime()
                
                if now - localLastGyroUpdate > throttleInterval {
                    localLastGyroUpdate = now
                    let rotationRate = data.rotationRate
                    
                    DispatchQueue.main.async {
                        onGyroUpdate(rotationRate)
                    }
                }
            }
        }
        
        // Magnetometer Telemetry
        if motionManager.isMagnetometerAvailable {
            var localLastMagnetUpdate: CFTimeInterval = 0
            
            motionManager.startMagnetometerUpdates(to: sensorQueue) { data, _ in
                guard let data = data else { return }
                let now = CACurrentMediaTime()
                
                if now - localLastMagnetUpdate > throttleInterval {
                    localLastMagnetUpdate = now
                    let magneticField = data.magneticField
                    
                    DispatchQueue.main.async {
                        onMagnetUpdate(magneticField)
                    }
                }
            }
        }
        
        // Pedometer Telemetry
        // Unlike IMU sensors, the pedometer emits updates deterministically based on physical steps.
        if CMPedometer.isStepCountingAvailable() {
            pedometer.startUpdates(from: Date()) { data, _ in
                guard let data = data else { return }
                let steps = data.numberOfSteps.intValue
                
                DispatchQueue.main.async {
                    onStepUpdate(steps)
                }
            }
        }
    }
    
    /// Terminates all active hardware telemetry and stops the pedometer session.
    func stopSensors() {
        motionManager.stopAccelerometerUpdates()
        motionManager.stopGyroUpdates()
        motionManager.stopMagnetometerUpdates()
        pedometer.stopUpdates()
    }
}