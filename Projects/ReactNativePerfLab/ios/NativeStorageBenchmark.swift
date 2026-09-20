import Foundation
import React

// Native module that offloads continuous binary memory allocations 
// and I/O operations directly to the Swift runtime.
@objc(NativeStorageBenchmark)
class NativeStorageBenchmark: RCTEventEmitter {
    
    private var activeTask: Task<Void, Never>?
    
    override func supportedEvents() -> [String]! {
        return ["onStorageProgress"]
    }
    
    override class func requiresMainQueueSetup() -> Bool {
        return false
    }

    @objc
    func writeData(_ megabytes: Int,
                   resolver resolve: @escaping RCTPromiseResolveBlock,
                   rejecter reject: @escaping RCTPromiseRejectBlock) {
        
        activeTask?.cancel()
        
        activeTask = Task.detached(priority: .userInitiated) {
            do {
                // Invoke the native Swift I/O engine
                try await StorageEngine.shared.writeData(megabytes: megabytes) { progress in
                    self.sendEvent(withName: "onStorageProgress", body: progress)
                }
                
                let checksum = await StorageEngine.shared.securityChecksum
                resolve(checksum)
            } catch {
                reject("WRITE_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc
    func readData(_ resolve: @escaping RCTPromiseResolveBlock,
                  rejecter reject: @escaping RCTPromiseRejectBlock) {
        
        activeTask?.cancel()
        
        activeTask = Task.detached(priority: .userInitiated) {
            do {
                try await StorageEngine.shared.readData { progress in
                    self.sendEvent(withName: "onStorageProgress", body: progress)
                }
                
                let checksum = await StorageEngine.shared.securityChecksum
                resolve(checksum)
            } catch {
                reject("READ_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc
    func cancelTask() {
        activeTask?.cancel()
    }
}