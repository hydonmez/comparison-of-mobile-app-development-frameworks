import { NativeModules, NativeEventEmitter, EmitterSubscription } from 'react-native';

/// Cross-platform video engine bridge for hardware benchmarking.
const { NativeVideoEngine } = NativeModules;

// Safely initialize the event emitter.
const videoEngineEmitter = new NativeEventEmitter(NativeVideoEngine || {});

export type EngineStateCallback = (hasEnded: boolean) => void;

class VideoEngine {
  private static instance: VideoEngine;
  private hasEnded: boolean = false;
  private listeners: Set<EngineStateCallback> = new Set();
  
  private eventSubscription: EmitterSubscription | null = null;

  private constructor() {
    this.setupEventListener();
  }

  public static getInstance(): VideoEngine {
    if (!VideoEngine.instance) {
      VideoEngine.instance = new VideoEngine();
    }
    return VideoEngine.instance;
  }

  private setupEventListener(): void {
    if (this.eventSubscription) return;

    this.eventSubscription = videoEngineEmitter.addListener(
      'onVideoEnded',
      () => {
        this.hasEnded = true;
        this.notifyListeners();
      }
    );
  }

  public subscribe(callback: EngineStateCallback): () => void {
    this.listeners.add(callback);
    callback(this.hasEnded);
    
    return () => {
      this.listeners.delete(callback);
    };
  }

  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener(this.hasEnded));
  }

  public async prepareVideo(fileName: string, extension: string): Promise<void> {
    try {
      this.hasEnded = false;
      this.notifyListeners();
      
      if (NativeVideoEngine && NativeVideoEngine.prepareVideo) {
        await NativeVideoEngine.prepareVideo(fileName, extension);
      }
    } catch (error) {
      console.error(`[PerfLab_Video_Bridge] Decoder preparation failure:`, error);
    }
  }

  public playFromStart(): void {
    this.hasEnded = false;
    this.notifyListeners();

    if (NativeVideoEngine && NativeVideoEngine.playFromStart) {
      NativeVideoEngine.playFromStart();
    }
  }

  public stop(): void {
    if (NativeVideoEngine && NativeVideoEngine.stop) {
      NativeVideoEngine.stop();
    }
  }

  public purgeMemory(): void {
    if (NativeVideoEngine && NativeVideoEngine.purgeMemory) {
      NativeVideoEngine.purgeMemory();
    }
    
    this.hasEnded = false;
    this.listeners.clear();
    
    if (this.eventSubscription) {
      this.eventSubscription.remove();
      this.eventSubscription = null;
    }
    
    this.setupEventListener();
  }
}

export const engine = VideoEngine.getInstance();