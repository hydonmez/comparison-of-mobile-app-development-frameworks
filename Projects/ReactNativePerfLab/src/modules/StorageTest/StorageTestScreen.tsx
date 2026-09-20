import React, { useCallback, useMemo } from 'react';
import { 
    View, 
    Text, 
    TouchableOpacity, 
    StyleSheet, 
    SafeAreaView,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useStorageTestViewModel } from './useStorageTestViewModel';

/**
 * Isolated component to prevent parent re-renders 
 * during high-frequency progress updates.
 */
const IsolatedProgressView = React.memo(({ progress }: { progress: number }) => {
    const percentage = useMemo(() => Math.floor(progress * 100), [progress]);
    
    return (
        <View style={styles.progressContainer}>
            <View style={styles.track}>
                <View 
                    style={[
                        styles.bar, 
                        { width: `${Math.min(Math.max(progress * 100, 0), 100)}%` }
                    ]} 
                />
            </View>
            <Text style={styles.progressText}>
                {percentage}%
            </Text>
        </View>
    );
});

interface StorageTestScreenProps {
    onNavigateBack: () => void;
}

export const StorageTestScreen: React.FC<StorageTestScreenProps> = ({ onNavigateBack }) => {
    
    const {
        progress,
        isRunning,
        status,
        isWriteCompleted,
        isReportReady,
        runBenchmark,
        exportResults
    } = useStorageTestViewModel();

    const handleWriteAction = useCallback(() => runBenchmark(true), [runBenchmark]);
    const handleReadAction = useCallback(() => runBenchmark(false), [runBenchmark]);

    return (
        <SafeAreaView style={styles.safeArea}>
            <View style={styles.header}>
                <TouchableOpacity onPress={onNavigateBack} style={styles.backButton}>
                    <Icon name="arrow-back" size={24} color="#007AFF" />
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Storage Benchmark</Text>
            </View>

            <View style={styles.content}>
                
                {/* Benchmark Status HUD */}
                <View style={styles.statusHud}>
                    <Icon name="storage" size={50} color="#FF9500" />
                    <Text style={styles.statusText}>{status}</Text>
                </View>

                {/* Progress Visualization */}
                <IsolatedProgressView progress={progress} />

                {/* Execution Controls */}
                <View style={styles.controlsContainer}>
                    <TouchableOpacity
                        style={[styles.primaryButton, isRunning && styles.buttonDisabled]}
                        onPress={handleWriteAction}
                        disabled={isRunning}
                    >
                        <Icon name="create" size={18} color="#FFFFFF" />
                        <Text style={styles.primaryButtonText}>
                            Step 1: Write 2GB Payload
                        </Text>
                    </TouchableOpacity>

                    <TouchableOpacity
                        style={[
                            styles.secondaryButton,
                            (!isRunning && isWriteCompleted) ? styles.secondaryButtonActive : styles.secondaryButtonDisabled
                        ]}
                        onPress={handleReadAction}
                        disabled={isRunning || !isWriteCompleted}
                    >
                        <Icon name="book" size={18} color={(!isRunning && isWriteCompleted) ? "#FF9500" : "#D3D3D3"} />
                        <Text 
                            style={[
                                styles.secondaryButtonText,
                                (!isRunning && isWriteCompleted) ? styles.secondaryButtonTextActive : styles.secondaryButtonTextDisabled
                            ]}
                        >
                            Step 2: Read 2GB Payload
                        </Text>
                    </TouchableOpacity>
                </View>

                {/* Telemetry Export Pipeline */}
                {isReportReady && !isRunning && (
                    <View style={styles.exportContainer}>
                        <Text style={styles.exportTitle}>Benchmark Report Ready</Text>
                        <TouchableOpacity style={styles.exportButton} onPress={exportResults}>
                            <Icon name="system-update-alt" size={16} color="#FFFFFF" />
                            <Text style={styles.exportButtonText}>Save Results</Text>
                        </TouchableOpacity>
                    </View>
                )}
                
                <View style={styles.spacer} />
            </View>
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    safeArea: { flex: 1, backgroundColor: '#FFFFFF' },
    header: { flexDirection: 'row', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: '#E5E5EA' },
    backButton: { paddingRight: 16 },
    headerTitle: { fontSize: 18, fontWeight: '600', color: '#000000', marginLeft: 8 },
    content: { 
        flex: 1, 
        paddingHorizontal: 16, 
        paddingTop: 16, 
        alignItems: 'center', 
        gap: 25 
    },
    statusHud: { alignItems: 'center', paddingTop: 20, gap: 12 },
    statusText: { fontSize: 18, fontWeight: '600', color: '#000000' },
    progressContainer: { alignItems: 'center', width: '100%', paddingHorizontal: 16, gap: 8 },
    track: { width: '100%', height: 8, backgroundColor: 'rgba(211, 211, 211, 0.5)', borderRadius: 4, overflow: 'hidden' },
    bar: { height: '100%', backgroundColor: '#FF9500' },
    progressText: { fontSize: 14, fontFamily: 'monospace', color: '#49454F' },
    controlsContainer: { width: '100%', paddingHorizontal: 16, gap: 15 },
    primaryButton: { backgroundColor: '#FF9500', padding: 16, borderRadius: 12, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8 },
    primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: 'bold' },
    buttonDisabled: { opacity: 0.5 },
    secondaryButton: { backgroundColor: 'transparent', padding: 16, borderRadius: 12, alignItems: 'center', justifyContent: 'center', borderWidth: 1, flexDirection: 'row', gap: 8 },
    secondaryButtonActive: { borderColor: '#FF9500' },
    secondaryButtonDisabled: { borderColor: '#D3D3D3' },
    secondaryButtonText: { fontSize: 16, fontWeight: 'bold' },
    secondaryButtonTextActive: { color: '#FF9500' },
    secondaryButtonTextDisabled: { color: '#D3D3D3' },
    exportContainer: { 
        width: '100%', 
        backgroundColor: 'rgba(128, 128, 128, 0.1)', 
        padding: 16, 
        borderRadius: 12, 
        alignItems: 'center', 
        gap: 10 
    },
    exportTitle: { fontWeight: 'bold', color: '#000000' },
    exportButton: { backgroundColor: '#34C759', padding: 16, borderRadius: 10, flexDirection: 'row', alignItems: 'center', gap: 8 },
    exportButtonText: { color: '#FFFFFF', fontWeight: 'bold' },
    spacer: { flex: 1 }
});