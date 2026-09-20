import React, { useCallback, useMemo } from 'react';
import { 
    View, 
    Text, 
    TouchableOpacity, 
    StyleSheet, 
    SafeAreaView,
    ScrollView,
    Platform
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useSensorTestViewModel } from './useSensorTestViewModel';

interface SensorTestScreenProps {
    onNavigateBack: () => void;
}

/**
 * High-Performance Sensor UI
 * Orchestrates the visualization of high-frequency sensor telemetry.
 * 
 * Performance Mitigation:
 * Uses strict memoization and isolated component boundaries to prevent 
 * the React diffing algorithm from causing JS thread strain during 
 * rapid state emissions.
 */
export const SensorTestScreen: React.FC<SensorTestScreenProps> = ({ onNavigateBack }) => {
    
    // Extracting state here triggers a root-level render on every tick.
    const {
        isRunning,
        status,
        isReportReady,
        progress,
        accelData,
        gyroData,
        magnetData,
        stepCount,
        startTest,
        stopTest,
        exportResults
    } = useSensorTestViewModel();

    const handleToggleState = useCallback(() => {
        if (isRunning) {
            stopTest(false);
        } else {
            startTest();
        }
    }, [isRunning, startTest, stopTest]);

    // Memoized to prevent unnecessary re-renders during high-frequency updates.
    const renderHeader = useMemo(() => (
        <View style={styles.header}>
            <TouchableOpacity onPress={onNavigateBack} style={styles.backButton}>
                <Text style={styles.backButtonText}>← Back</Text>
            </TouchableOpacity>
            <Text style={styles.headerTitle}>Sensor Performance</Text>
            <View style={{ width: 60 }} />
        </View>
    ), [onNavigateBack]);

    // Memoized to shield interactive controls from layout jitter.
    const renderControls = useMemo(() => (
        <View style={styles.controlPanel}>
            <TouchableOpacity 
                style={[styles.primaryButton, { backgroundColor: isRunning ? '#FF3B30' : '#FF9500' }]} 
                onPress={handleToggleState}
                activeOpacity={0.8}
            >
                <Icon name={isRunning ? "stop" : "play-arrow"} size={22} color="#FFFFFF" />
                <View style={{ width: 8 }} />
                <Text style={styles.primaryButtonText}>
                    {isRunning ? "Stop Benchmark" : "Start Sensor Benchmark"}
                </Text>
            </TouchableOpacity>

            {isReportReady && !isRunning && (
                <TouchableOpacity 
                    style={styles.exportButton} 
                    onPress={exportResults}
                    activeOpacity={0.8}
                >
                    <Icon name="system-update-alt" size={22} color="#FFFFFF" />
                    <View style={{ width: 8 }} />
                    <Text style={styles.primaryButtonText}>Export Results (CSV)</Text>
                </TouchableOpacity>
            )}
        </View>
    ), [isRunning, isReportReady, handleToggleState, exportResults]);

    return (
        <SafeAreaView style={styles.safeArea}>
            {renderHeader}

            <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
                
                <View style={styles.statusHub}>
                    <Text style={[styles.statusText, { color: isRunning ? '#FF9500' : '#8E8E93' }]}>
                        {status}
                    </Text>

                    {isRunning && (
                        <View style={styles.progressWrapper}>
                            <View style={styles.progressBarBg}>
                                {/* Updates simultaneously with sensor data. */}
                                <View style={[styles.progressBarFill, { width: `${progress * 100}%` }]} />
                            </View>
                        </View>
                    )}
                </View>

                {/* Isolated components to prevent sibling re-renders */}
                <View style={styles.halContainer}>
                    <SensorInfoCard iconName="open-in-full" title="Accelerometer (G-Force)" data={accelData} color="#007AFF" />
                    <SensorInfoCard iconName="sync" title="Gyroscope (Rad/s)" data={gyroData} color="#34C759" />
                    <SensorInfoCard iconName="explore" title="Magnetometer (µT)" data={magnetData} color="#FF3B30" />
                    <PedometerCard stepCount={stepCount} />
                </View>

                {renderControls}

            </ScrollView>
        </SafeAreaView>
    );
};

// --- Sub-Composables & Formatter ---

// Formats the value to 3 decimal places.
const formatValue = (value: number): string => {
    return value.toFixed(3);
};

interface SensorInfoCardProps {
    iconName: string;
    title: string;
    data: number[];
    color: string;
}

// Explicit equality check prevents unnecessary sibling re-renders.
const SensorInfoCard = React.memo(({ iconName, title, data, color }: SensorInfoCardProps) => {
    return (
        <View style={styles.cardContainer}>
            <View style={styles.cardHeader}>
                <Icon name={iconName} size={20} color={color} />
                <Text style={styles.cardTitle}>{title}</Text>
            </View>
            <View style={styles.valueRow}>
                {/* Inlined components to reduce VDOM depth and improve rendering performance. */}
                <View style={styles.valueCol}>
                    <Text style={styles.valueLabel}>X</Text>
                    <Text style={styles.valueNumber}>{formatValue(data[0] || 0)}</Text>
                </View>
                <View style={styles.valueCol}>
                    <Text style={styles.valueLabel}>Y</Text>
                    <Text style={styles.valueNumber}>{formatValue(data[1] || 0)}</Text>
                </View>
                <View style={styles.valueCol}>
                    <Text style={styles.valueLabel}>Z</Text>
                    <Text style={styles.valueNumber}>{formatValue(data[2] || 0)}</Text>
                </View>
            </View>
        </View>
    );
}, (prevProps, nextProps) => {
    // Strict index comparison to circumvent JS array reference inequality.
    return prevProps.data[0] === nextProps.data[0] &&
           prevProps.data[1] === nextProps.data[1] &&
           prevProps.data[2] === nextProps.data[2];
});

const PedometerCard = React.memo(({ stepCount }: { stepCount: number }) => {
    return (
        <View style={styles.cardContainer}>
            <View style={styles.pedometerRow}>
                <View>
                    <Text style={styles.pedometerTitle}>Pedometer (Step Count)</Text>
                    <Text style={styles.pedometerValue}>{stepCount}</Text>
                </View>
                <Icon name="directions-walk" size={32} color="#5856D6" />
            </View>
        </View>
    );
});

// --- Styles ---
const styles = StyleSheet.create({
    safeArea: { flex: 1, backgroundColor: '#FFFFFF' },
    header: {
        flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
        padding: 16, borderBottomWidth: 1, borderBottomColor: '#E5E5EA',
    },
    backButton: { paddingRight: 16 },
    backButtonText: { fontSize: 18, color: '#007AFF', fontWeight: '600' },
    headerTitle: { fontSize: 18, fontWeight: 'bold', color: '#000000' },
    scrollContent: { padding: 16, gap: 25 },
    statusHub: { alignItems: 'center', gap: 15 },
    statusText: { fontSize: 18, fontWeight: 'bold' },
    progressWrapper: { width: '100%', paddingHorizontal: 8, gap: 8 },
    progressBarBg: { width: '100%', height: 8, backgroundColor: '#E5E5EA', borderRadius: 4, overflow: 'hidden' },
    progressBarFill: { height: '100%', backgroundColor: '#FF9500' },
    halContainer: { gap: 15 },
    cardContainer: { backgroundColor: '#F2F2F7', borderRadius: 12, padding: 16, gap: 10 },
    cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 8 },
    cardTitle: { fontSize: 14, fontWeight: 'bold', color: '#000000' },
    valueRow: { flexDirection: 'row', justifyContent: 'space-between', width: '100%' },
    valueCol: { flex: 1, alignItems: 'center' },
    valueLabel: { fontSize: 10, color: '#8E8E93', marginBottom: 2 },
    // Monospaced font prevents layout jitter during rapid numerical shifts.
    valueNumber: { fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontSize: 13, fontWeight: 'bold', color: '#000000' },
    pedometerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    pedometerTitle: { fontSize: 12, fontWeight: 'bold', color: '#5856D6', marginBottom: 4 },
    pedometerValue: { fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontSize: 22, fontWeight: 'bold', color: '#000000' },
    controlPanel: { gap: 15, paddingBottom: 20 },
    primaryButton: { height: 56, borderRadius: 12, alignItems: 'center', justifyContent: 'center', flexDirection: 'row' },
    exportButton: { backgroundColor: '#34C759', height: 56, borderRadius: 12, alignItems: 'center', justifyContent: 'center', flexDirection: 'row' },
    primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: 'bold' }
});