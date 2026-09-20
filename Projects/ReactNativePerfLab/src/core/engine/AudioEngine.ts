import { create } from 'zustand';
import TrackPlayer, {
    State,
    Event,
    Capability
} from 'react-native-track-player';
import { EventSubscription } from 'react-native';

export interface AudioEngineState {
    currentTime: number;
    totalDuration: number;
    isPlaying: boolean;
    isInitialized: boolean;
}

export const useAudioEngine = create<AudioEngineState>(() => ({
    currentTime: 0,
    totalDuration: 0,
    isPlaying: false,
    isInitialized: false,
}));

// Safe references to prevent memory leaks across re-renders or hot-reloads
let stateSubscription: EventSubscription | null = null;
let timeObserverInterval: ReturnType<typeof setInterval> | null = null;

export class AudioEngine {
   
    static async setupSession(): Promise<void> {
        if (useAudioEngine.getState().isInitialized) return;

        try {
            await TrackPlayer.setupPlayer({
                minBuffer: 5.0,
                maxBuffer: 5.0,
                playBuffer: 1.0,
                backBuffer: 2.5
            });

            await TrackPlayer.updateOptions({
                capabilities: [
                    Capability.Play,
                    Capability.Pause,
                    Capability.SeekTo,
                ]
            });

            useAudioEngine.setState({ isInitialized: true });
            this.setupEOFObserver();

        } catch (error) {
            console.error("[AudioEngine] Session configuration failed:", error);
        }
    }

   static async loadAudio(asset: number): Promise<boolean> {
        await this.setupSession();
        await this.release(); // Enforce a clean slate before loading a new asset

        try {
            await TrackPlayer.add([{
                // Cast the required asset ID (number) to string to satisfy TypeScript, 
                // allowing the native module to correctly resolve the bundled asset.
                url: (asset as unknown) as string,
                title: 'Telemetry Audio',
                artist: 'Benchmark Lab'
            }]);

            const progress = await TrackPlayer.getProgress();
            useAudioEngine.setState({ totalDuration: progress.duration });

            return true;
        } catch (error) {
            console.error("[AudioEngine] Asset loading failed:", error);
            return false;
        }
    }

    static async play(): Promise<void> {
        const state = useAudioEngine.getState();
       
        // Rewind if playback has reached the end
        if (state.totalDuration > 0 && state.currentTime >= state.totalDuration - 0.5) {
            await this.seek(0);
        }

        await TrackPlayer.play();
        useAudioEngine.setState({ isPlaying: true });
       
        this.startTimeObserver(); // Engage bridge listener only during active playback
    }

    static async pause(): Promise<void> {
        await TrackPlayer.pause();
        useAudioEngine.setState({ isPlaying: false });
        this.stopTimeObserver();
    }

    static async seek(toSeconds: number): Promise<void> {
        await TrackPlayer.seekTo(toSeconds);
        useAudioEngine.setState({ currentTime: toSeconds });
    }

    static async skip(bySeconds: number): Promise<void> {
        const state = useAudioEngine.getState();
        const newTime = Math.min(Math.max(state.currentTime + bySeconds, 0), state.totalDuration);
        await this.seek(newTime);
    }

    /**
     * Poll progress periodically during playback to minimize CPU overhead.
     */
    private static startTimeObserver(): void {
        this.stopTimeObserver();
        timeObserverInterval = setInterval(async () => {
            const progress = await TrackPlayer.getProgress();
            useAudioEngine.setState((state) => ({
                currentTime: progress.position,
                totalDuration: progress.duration > 0 ? progress.duration : state.totalDuration
            }));
        }, 100);
    }

    private static stopTimeObserver(): void {
        if (timeObserverInterval) {
            clearInterval(timeObserverInterval);
            timeObserverInterval = null;
        }
    }

    private static setupEOFObserver(): void {
        if (stateSubscription) return;

        stateSubscription = TrackPlayer.addEventListener(
            Event.PlaybackState,
            async (event) => {
                // Ensure duration is captured in case initial metadata extraction failed
                if (event.state === State.Ready || event.state === State.Playing) {
                    const progress = await TrackPlayer.getProgress();
                    useAudioEngine.setState((state) => ({
                        totalDuration: progress.duration > 0 ? progress.duration : state.totalDuration
                    }));
                }

                // Handle End of File (EOF) state
                if (event.state === State.Ended) {
                    const duration = useAudioEngine.getState().totalDuration;
                    useAudioEngine.setState({
                        isPlaying: false,
                        currentTime: duration
                    });
                    this.stopTimeObserver();
                }
            }
        );
    }

    static async release(): Promise<void> {
        await this.pause();
        await TrackPlayer.reset();
       
        useAudioEngine.setState({
            currentTime: 0,
            totalDuration: 0,
            isPlaying: false
        });
    }
}