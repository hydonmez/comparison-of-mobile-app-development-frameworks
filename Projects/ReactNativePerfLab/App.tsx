import React, { useEffect, useState, useCallback } from 'react';
import { 
    View, 
    Text, 
    TouchableOpacity, 
    StyleSheet, 
    ScrollView, 
    SafeAreaView,
    StatusBar,
    AppState,
    AppStateStatus,
    Platform,
    PermissionsAndroid
} from 'react-native';

import Icon from 'react-native-vector-icons/MaterialIcons';

import { AudioTestScreen } from './src/modules/AudioTest/AudioTestScreen';
import { VideoTestScreen } from './src/modules/VideoTest/VideoTestScreen';
import { JsonTestScreen } from './src/modules/JsonTest/JsonTestScreen';
import { ListTestScreen } from './src/modules/ListTest/ListTestScreen';
import { MapTestScreen } from './src/modules/MapTest/MapTestScreen';
import { SensorTestScreen } from './src/modules/SensorTest/SensorTestScreen';
import { StorageTestScreen } from './src/modules/StorageTest/StorageTestScreen';

import { useLaunchPerformanceStore } from './src/core/manager/LaunchPerformanceManager';

type BenchmarkRoute = 
    | 'dashboard' 
    | 'video_test' 
    | 'audio_test' 
    | 'storage_test' 
    | 'json_test' 
    | 'map_test' 
    | 'list_test' 
    | 'filter_test' 
    | 'sensor_test';

/**
 * Main application entry point and global lifecycle orchestrator.
 */
const App = () => {
    const [activeScreen, setActiveScreen] = useState<BenchmarkRoute>('dashboard');
    const launchStore = useLaunchPerformanceStore();

    /**
     * Initializes global environment and requests necessary hardware permissions.
     */
    useEffect(() => {
        const provisionGlobalEnvironment = async () => {
            if (Platform.OS === 'android' && Platform.Version >= 29) {
                try {
                    await PermissionsAndroid.request(
                        PermissionsAndroid.PERMISSIONS.ACTIVITY_RECOGNITION
                    );
                } catch (error) {
                    console.warn("Global permission provisioning failed:", error);
                }
            }
        };

        provisionGlobalEnvironment();
    }, []);

    /**
     * Observes application lifecycle to calculate startup metrics.
     */
    useEffect(() => {
        launchStore.osReady();

        const handleAppStateChange = (nextAppState: AppStateStatus) => {
            if (nextAppState === 'active') {
                launchStore.hotStartDetected();
            }
        };

        const subscription = AppState.addEventListener('change', handleAppStateChange);
        return () => subscription.remove();
    }, []);

    useEffect(() => {
        if (activeScreen === 'dashboard') {
            launchStore.reportBenchmark();
        }
    }, [activeScreen]);

    const navigate = useCallback((route: BenchmarkRoute) => {
        setActiveScreen(route);
    }, []);

    const renderDashboard = () => (
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
            <View style={styles.telemetrySection}>
                <View style={styles.sectionHeaderRow}>
                    <Icon name="timer" size={20} color="#5E5CE6" />
                    <Text style={styles.sectionHeaderTitle}>System Latency Distribution</Text>
                </View>

                {/* Cold Start Telemetry Node */}
                <View style={[styles.telemetryCard, { backgroundColor: 'rgba(50, 173, 230, 0.1)' }]}>
                    <View style={styles.cardHeader}>
                        <Icon name="power-settings-new" size={16} color="#32ADE6" />
                        <Text style={styles.cardHeaderTitle}>Cold Start</Text>
                    </View>
                    <Text style={styles.cardSubtitle}>Post-Zygote / JS Engine Initialization</Text>
                    
                    <Text style={styles.mainMetric}>
                        {launchStore.totalColdStartMs > 0 ? launchStore.totalColdStartMs.toFixed(1) : "--"} ms
                    </Text>
                    
                    <View style={styles.metricRow}>
                        <View style={[styles.dot, { backgroundColor: '#5E5CE6' }]} />
                        <Text style={styles.subMetricText}>OS Overhead: {launchStore.osDurationMs.toFixed(1)} ms</Text>
                    </View>
                    <View style={styles.metricRow}>
                        <View style={[styles.dot, { backgroundColor: '#FF2D55' }]} />
                        <Text style={styles.subMetricText}>UI Rendering: {launchStore.softwareDurationMs.toFixed(1)} ms</Text>
                    </View>
                </View>

                {/* Hot Start Telemetry Node */}
                <View style={[styles.telemetryCard, { backgroundColor: 'rgba(255, 149, 0, 0.1)' }]}>
                    <View style={styles.cardHeader}>
                        <Icon name="restore" size={16} color="#FF9500" />
                        <Text style={styles.cardHeaderTitle}>Hot Start</Text>
                    </View>
                    <Text style={styles.cardSubtitle}>State Restoration from RAM</Text>
                    
                    <Text style={[styles.mainMetric, { color: launchStore.hotStartMs > 0 ? '#000' : 'rgba(0,0,0,0.4)' }]}>
                        {launchStore.hotStartMs > 0 ? `${launchStore.hotStartMs.toFixed(1)} ms` : "-- ms"}
                    </Text>
                    
                    <View style={styles.metricRow}>
                        <Icon 
                            name={launchStore.hotStartMs > 0 ? "memory" : "hourglass-empty"} 
                            size={14} 
                            color={launchStore.hotStartMs > 0 ? "#34C759" : "#8E8E93"} 
                        />
                        <Text style={[styles.subMetricText, { color: launchStore.hotStartMs > 0 ? '#34C759' : '#8E8E93', fontWeight: 'bold' }]}>
                            {launchStore.hotStartMs > 0 ? 'Verified: Cached Process' : 'Awaiting Foreground Transition...'}
                        </Text>
                    </View>
                </View>
            </View>

            <View style={styles.testSuiteSection}>
                <Text style={styles.suiteHeader}>Active Benchmarking Suites</Text>
                <TestRowView title="Video Playback (1080p/60fps)" iconName="ondemand-video" color="#FF3B30" onClick={() => navigate('video_test')} />
                <TestRowView title="Audio Playback (LPCM/FLAC)" iconName="graphic-eq" color="#FF2D55" onClick={() => navigate('audio_test')} />
                <TestRowView title="I/O Storage Throughput" iconName="storage" color="#8E8E93" onClick={() => navigate('storage_test')} />
                <TestRowView title="JSON Deserialization (10MB+)" iconName="data-object" color="#00B0FF" onClick={() => navigate('json_test')} />
                <TestRowView title="Map Surface Rendering" iconName="map" color="#34C759" onClick={() => navigate('map_test')} />
                <TestRowView title="Virtual List Performance" iconName="grid-view" color="#007AFF" onClick={() => navigate('list_test')} />
                <TestRowView title="Hardware Sensor Fusion" iconName="sensors" color="#FF9500" onClick={() => navigate('sensor_test')} />
            </View>
        </ScrollView>
    );

    return (
        <SafeAreaView style={styles.container}>
            <StatusBar barStyle="dark-content" backgroundColor="#FFFFFF" />
            
            {activeScreen === 'dashboard' && (
                <View style={styles.topBar}>
                    <Text style={styles.topBarTitle}>Benchmark Lab (RN)</Text>
                </View>
            )}

            {activeScreen === 'dashboard' && renderDashboard()}
            {activeScreen === 'video_test' && <VideoTestScreen onNavigateBack={() => navigate('dashboard')} />}
            {activeScreen === 'audio_test' && <AudioTestScreen onNavigateBack={() => navigate('dashboard')} />}
            {activeScreen === 'storage_test' && <StorageTestScreen onNavigateBack={() => navigate('dashboard')} />}
            {activeScreen === 'json_test' && <JsonTestScreen onNavigateBack={() => navigate('dashboard')} />}
            {activeScreen === 'map_test' && <MapTestScreen onNavigateBack={() => navigate('dashboard')} />}
            {activeScreen === 'list_test' && <ListTestScreen onNavigateBack={() => navigate('dashboard')} />}
            {activeScreen === 'sensor_test' && <SensorTestScreen onNavigateBack={() => navigate('dashboard')} />}
        </SafeAreaView>
    );
};

interface TestRowViewProps {
    title: string;
    iconName: string;
    color: string;
    onClick: () => void;
}

const TestRowView = React.memo(({ title, iconName, color, onClick }: TestRowViewProps) => (
    <TouchableOpacity style={styles.rowContainer} onPress={onClick} activeOpacity={0.7}>
        <View style={[styles.iconBox, { backgroundColor: color }]}>
            <Icon name={iconName} size={18} color="#FFFFFF" />
        </View>
        <Text style={styles.rowTitle}>{title}</Text>
        <Icon name="chevron-right" size={24} color="#C7C7CC" />
    </TouchableOpacity>
));

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F2F2F7' },
    topBar: {
        paddingHorizontal: 16, paddingVertical: 12, backgroundColor: '#FFFFFF',
        borderBottomWidth: 1, borderBottomColor: '#E5E5EA'
    },
    topBarTitle: { fontSize: 20, fontWeight: 'bold', color: '#000000' },
    scrollContent: { padding: 16, gap: 20, paddingBottom: 40 },
    telemetrySection: { gap: 12 },
    sectionHeaderRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
    sectionHeaderTitle: { fontSize: 18, fontWeight: 'bold', color: '#000000' },
    telemetryCard: { padding: 14, borderRadius: 14, gap: 8 },
    cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 6 },
    cardHeaderTitle: { fontSize: 12, fontWeight: 'bold', color: '#8E8E93' },
    cardSubtitle: { fontSize: 10, color: '#8E8E93', marginTop: -4 },
    mainMetric: { 
        fontSize: 22, fontWeight: '900', color: '#000', 
        fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', marginVertical: 4 
    },
    metricRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
    dot: { width: 6, height: 6, borderRadius: 3 },
    subMetricText: { fontSize: 11, fontWeight: '500', color: '#8E8E93' },
    testSuiteSection: { gap: 10 },
    suiteHeader: { fontSize: 14, fontWeight: 'bold', color: '#8E8E93', marginTop: 10, marginBottom: 4 },
    rowContainer: {
        flexDirection: 'row', alignItems: 'center', backgroundColor: '#FFFFFF',
        padding: 16, borderRadius: 12, gap: 14
    },
    iconBox: { width: 32, height: 32, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
    rowTitle: { flex: 1, fontSize: 15, fontWeight: 'bold', color: '#000000' }
});

export default App;