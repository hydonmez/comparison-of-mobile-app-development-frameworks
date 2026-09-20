import React from 'react';
import {
    View,
    Text,
    TouchableOpacity,
    StyleSheet,
    SafeAreaView,
    ActivityIndicator,
    Platform
} from 'react-native';
import Slider from '@react-native-community/slider';
import Icon from 'react-native-vector-icons/MaterialIcons';

import { useAudioTestViewModel, formatTime } from './useAudioTestViewModel';
import { usePerformanceStore } from '../../core/manager/PerformanceManager';
import { useAudioEngine } from '../../core/engine/AudioEngine';

/**
 * Native Audio Benchmark UI.
 * Evaluates hardware decoding throughput and RAM footprint. Uses vector graphics 
 * and decoupled components to secure a stable 60 FPS under load.
 */

interface AudioTestScreenProps {
    onNavigateBack: () => void;
}

export const AudioTestScreen: React.FC<AudioTestScreenProps> = ({ onNavigateBack }) => {
    
    const {
        isTesting,
        isAudioLoaded,
        errorMessage,
        isPlaying,
        startTest,
        stopTest,
        seekAudio,
        skip
    } = useAudioTestViewModel();

    return (
        <SafeAreaView style={styles.safeArea}>
            <BenchmarkHeader onNavigateBack={onNavigateBack} />

            <View style={styles.container}>
                <MediaArtwork />

                <DiagnosticPanel errorMessage={errorMessage} />

                {/* Isolated subscriber handles 4Hz state mutations independently */}
                <PlaybackTimelineLayer
                    isAudioLoaded={isAudioLoaded}
                    seekAudio={seekAudio}
                />

                <TransportControls
                    isPlaying={isPlaying}
                    isAudioLoaded={isAudioLoaded}
                    onSkipBack={() => skip(-15)}
                    onSkipForward={() => skip(15)}
                    onTogglePlayback={() => isPlaying ? stopTest(false) : startTest()}
                />

                <Spacer />

                <TelemetryHUDLayer isTesting={isTesting} />
            </View>
        </SafeAreaView>
    );
};

// MARK: - Isolated Subcomponents

const Spacer = () => <View style={{ flex: 1 }} />;

const BenchmarkHeader = React.memo(({ onNavigateBack }: { onNavigateBack: () => void }) => (
    <View style={styles.header}>
        <TouchableOpacity onPress={onNavigateBack} style={styles.backButton}>
            <Icon name="arrow-back-ios" size={20} color="#007AFF" />
            <Text style={styles.backButtonText}>Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Audio Performance</Text>
        <View style={{ width: 60 }} />
    </View>
));

const MediaArtwork = React.memo(() => (
    <View style={styles.artworkContainer}>
        <View style={styles.artworkPlaceholder}>
            <Icon name="music-note" size={100} color="#FFFFFF" />
        </View>
    </View>
));

const DiagnosticPanel = React.memo(({ errorMessage }: { errorMessage: string | null }) => (
    <View style={styles.titleContainer}>
        {errorMessage ? (
            <View style={styles.errorBox}>
                <Icon name="error-outline" size={20} color="#FFFFFF" />
                <Text style={styles.errorText}>Error: {errorMessage}</Text>
            </View>
        ) : (
            <>
                <Text style={styles.mainTitle}>React Native Audio Benchmark</Text>
                <View style={styles.engineBadge}>
                    <Text style={styles.engineText}>
                        Engine: {Platform.OS === 'ios' ? 'AVFoundation' : 'ExoPlayer'} (Optimized)
                    </Text>
                </View>
            </>
        )}
    </View>
));

/**
 * VDOM Bottleneck Mitigation.
 * Binds directly to the Zustand store to bypass parent reconciliation, 
 * processing rendering updates purely within this leaf node.
 */
const PlaybackTimelineLayer = React.memo(({
    isAudioLoaded,
    seekAudio
}: {
    isAudioLoaded: boolean;
    seekAudio: (val: number) => void;
}) => {
    
    const currentTime = useAudioEngine(state => state.currentTime);
    const totalDuration = useAudioEngine(state => state.totalDuration);

    const safePosition = (Number.isNaN(currentTime) || !Number.isFinite(currentTime)) ? 0 : currentTime;
    const safeDuration = (Number.isNaN(totalDuration) || !Number.isFinite(totalDuration) || totalDuration <= 0) ? 1 : totalDuration;

    return (
        <View style={styles.timelineContainer}>
            <Slider
                style={styles.slider}
                minimumValue={0}
                maximumValue={safeDuration}
                value={safePosition}
                onSlidingComplete={seekAudio}
                minimumTrackTintColor="#FF9500"
                maximumTrackTintColor="#E5E5EA"
                thumbTintColor="#FF9500"
                disabled={!isAudioLoaded}
            />
            <View style={styles.timeRow}>
                <Text style={styles.timeText}>{formatTime(safePosition)}</Text>
                <Text style={styles.timeText}>{formatTime(safeDuration)}</Text>
            </View>
        </View>
    );
});

const TransportControls = React.memo(({
    isPlaying,
    isAudioLoaded,
    onSkipBack,
    onSkipForward,
    onTogglePlayback
}: {
    isPlaying: boolean;
    isAudioLoaded: boolean;
    onSkipBack: () => void;
    onSkipForward: () => void;
    onTogglePlayback: () => void;
}) => {
    const activeColor = Platform.OS === 'ios' ? '#007AFF' : '#FF9500';
    const iconColor = isAudioLoaded ? activeColor : '#C7C7CC';

    return (
        <View style={styles.transportContainer}>
            <TouchableOpacity onPress={onSkipBack} disabled={!isAudioLoaded} style={styles.skipButton}>
                <Icon name="fast-rewind" size={44} color={iconColor} />
            </TouchableOpacity>

            <TouchableOpacity onPress={onTogglePlayback} disabled={!isAudioLoaded} style={styles.playButton}>
                <Icon 
                    name={isPlaying ? "pause-circle-filled" : "play-circle-filled"} 
                    size={80} 
                    color={iconColor} 
                />
            </TouchableOpacity>

            <TouchableOpacity onPress={onSkipForward} disabled={!isAudioLoaded} style={styles.skipButton}>
                <Icon name="fast-forward" size={44} color={iconColor} />
            </TouchableOpacity>
        </View>
    );
});

const TelemetryHUDLayer = React.memo(({ isTesting }: { isTesting: boolean }) => {
    
    // Subscribes strictly to hardware telemetry streams
    const currentFPS = usePerformanceStore(state => state.currentFPS);
    const thermalState = usePerformanceStore(state => state.thermalStateString);

    return (
        <View style={styles.telemetryHUD}>
            <View style={styles.telemetryColumn}>
                <Text style={[styles.fpsText, { color: currentFPS < 50 ? '#FF3B30' : '#34C759' }]}>
                    FPS: {currentFPS}
                </Text>
                <Text style={styles.thermalText}>Thermal: {thermalState}</Text>
            </View>
            
            <Spacer />
            
            {isTesting && (
                <View style={styles.recBadge}>
                    <ActivityIndicator size="small" color="#FF3B30" style={{ transform: [{ scale: 0.7 }] }} />
                    <Text style={styles.recText}>REC</Text>
                </View>
            )}
        </View>
    );
}, (prevProps, nextProps) => prevProps.isTesting === nextProps.isTesting);

// MARK: - Stylesheet

const styles = StyleSheet.create({
    safeArea: { flex: 1, backgroundColor: '#FFFFFF' },
    container: { flex: 1, padding: 20, alignItems: 'center' },
    
    header: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        paddingHorizontal: 16, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#E5E5EA',
    },
    backButton: { flexDirection: 'row', alignItems: 'center', paddingRight: 16 },
    backButtonText: { fontSize: 18, color: '#007AFF', fontWeight: '500', marginLeft: 4 },
    headerTitle: { fontSize: 18, fontWeight: '700', color: '#000000' },

    artworkContainer: { marginTop: 30, marginBottom: 20 },
    artworkPlaceholder: {
        width: 250, height: 250, borderRadius: 24,
        backgroundColor: '#FF9500',
        alignItems: 'center', justifyContent: 'center',
        shadowColor: '#FF2D55', shadowOffset: { width: 0, height: 12 },
        shadowOpacity: 0.35, shadowRadius: 15, elevation: 10,
    },

    titleContainer: { alignItems: 'center', gap: 8, marginBottom: 30 },
    errorBox: { 
        flexDirection: 'row', alignItems: 'center', gap: 8,
        backgroundColor: '#FF3B30', paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8 
    },
    errorText: { color: '#FFFFFF', fontWeight: '600', fontSize: 14 },
    mainTitle: { fontSize: 22, fontWeight: '700', color: '#1C1C1E' },
    engineBadge: { backgroundColor: '#F2F2F7', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8 },
    engineText: { fontSize: 13, color: '#8E8E93', fontWeight: '600' },

    timelineContainer: { width: '100%', paddingHorizontal: 10, marginBottom: 30 },
    slider: { width: '100%', height: 40 },
    timeRow: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 15 },
    timeText: { fontSize: 12, color: '#8E8E93', fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontWeight: '500' },

    transportContainer: { flexDirection: 'row', alignItems: 'center', gap: 40 },
    skipButton: { padding: 10 },
    playButton: {
        shadowColor: '#000', shadowOffset: { width: 0, height: 6 },
        shadowOpacity: 0.15, shadowRadius: 8, elevation: 5
    },

    telemetryHUD: {
        flexDirection: 'row', alignItems: 'center', width: '100%',
        backgroundColor: '#F2F2F7', padding: 16, borderRadius: 16, marginBottom: 10
    },
    telemetryColumn: { gap: 4 },
    fpsText: { fontSize: 16, fontWeight: '700', fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
    thermalText: { fontSize: 12, color: '#8E8E93', fontWeight: '500' },
    recBadge: {
        flexDirection: 'row', alignItems: 'center', gap: 6,
        backgroundColor: 'rgba(255, 59, 48, 0.12)', paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8
    },
    recText: { fontSize: 12, fontWeight: '800', color: '#FF3B30' }
});