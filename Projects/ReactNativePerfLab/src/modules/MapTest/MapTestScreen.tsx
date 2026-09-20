import React, { useRef, useMemo, useEffect } from 'react';
import { 
    View, 
    Text, 
    TouchableOpacity, 
    ActivityIndicator, 
    StyleSheet, 
    SafeAreaView,
    Platform
} from 'react-native';
import MapView, { Marker, PROVIDER_DEFAULT, PROVIDER_GOOGLE, Region } from 'react-native-maps';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { useMapTestViewModel, MapEngineController } from './useMapTestViewModel';

/**
 * Cross-Platform Map Rendering Surface
 * A highly optimized presentation layer for geospatial rendering benchmarks.
 */
interface MapTestScreenProps {
    onNavigateBack: () => void;
}

/**
 * Converts abstract zoom levels into absolute lat/lon deltas.
 */
const calculateRegion = (coordinate: { latitude: number; longitude: number }, zoom: number): Region => {
    const spanDelta = 360.0 / Math.pow(2.0, zoom);
    return {
        latitude: coordinate.latitude,
        longitude: coordinate.longitude,
        latitudeDelta: spanDelta,
        longitudeDelta: spanDelta,
    };
};

export const MapTestScreen: React.FC<MapTestScreenProps> = ({ onNavigateBack }) => {
    const {
        points,
        isRunning,
        status,
        isReportReady,
        initialTarget, // Fetching with the updated name from ViewModel
        startTest,
        stopTest,
        exportResults
    } = useMapTestViewModel();

    const mapRef = useRef<MapView>(null);

    /**
     * Initial State Configuration
     * Android uses absolute Camera distances (Google Maps).
     * iOS uses calculated 2D Regions (MapKit).
     */
    const initialMapConfiguration = useMemo(() => {
        if (Platform.OS === 'android') {
            return {
                initialCamera: {
                    center: initialTarget.center,
                    zoom: initialTarget.zoom,
                    pitch: 0,
                    heading: 0,
                    altitude: 0
                }
            };
        } else {
            return {
                initialRegion: calculateRegion(initialTarget.center, initialTarget.zoom)
            };
        }
    }, [initialTarget]);

    /**
     * Animation Controller
     * Routes agnostic ViewModel commands into engine-specific API calls.
     */
    const mapController: MapEngineController = useMemo(() => ({
        animateToTarget: (target, durationMs) => {
            if (Platform.OS === 'android') {
                // Trigger Google Maps camera update
                mapRef.current?.animateCamera({
                    center: target.center,
                    zoom: target.zoom,
                    pitch: 0,
                    heading: 0
                }, { duration: durationMs });
            } else {
                // Trigger MapKit region update
                const targetRegion = calculateRegion(target.center, target.zoom);
                mapRef.current?.animateToRegion(targetRegion, durationMs);
            }
        }
    }), []);

    // Lifecycle Security Failsafe
    useEffect(() => {
        return () => {
            stopTest(false);
        };
    }, [stopTest]);

    return (
        <SafeAreaView style={styles.container}>
            
            {/* MARK: - Cross-Platform Navigation Header */}
            <View style={styles.header}>
                <TouchableOpacity onPress={onNavigateBack} style={styles.backButton}>
                    <Icon name="arrow-back" size={24} color="#007AFF" />
                    <Text style={styles.backButtonText}>Back</Text>
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Map Performance Benchmark</Text>
                <View style={styles.headerSpacer} />
            </View>

            {/* MARK: - Telemetry Status Bar */}
            <View style={styles.statusBar}>
                <Text style={styles.statusText} numberOfLines={1}>{status}</Text>
                {isRunning && <ActivityIndicator size="small" color="#9C27B0" />}
            </View>

            {/* MARK: - Map Rendering Surface */}
            <View style={styles.mapContainer}>
                <MapView
                    ref={mapRef}
                    style={styles.map}
                    provider={Platform.OS === 'android' ? PROVIDER_GOOGLE : PROVIDER_DEFAULT}
                    
                    // Inject platform-specific initial state using the spread operator
                    {...initialMapConfiguration}
                    
                    pitchEnabled={false}
                    rotateEnabled={false}
                    scrollEnabled={false}
                    zoomEnabled={false}
                    showsBuildings={false}
                    showsIndoors={false}
                    showsTraffic={false}
                    showsCompass={false}
                    showsMyLocationButton={false}
                    mapType="standard"
                >
                    {points.map((point) => (
                        <Marker
                            key={`${point.coordinate.latitude}-${point.coordinate.longitude}`}
                            coordinate={point.coordinate}
                            title={point.title}
                            pinColor="red"
                            tracksViewChanges={false} 
                        />
                    ))}
                </MapView>
            </View>

            {/* MARK: - Control Interface */}
            <View style={styles.controlsContainer}>
                <TouchableOpacity 
                    style={[styles.button, isRunning && styles.buttonDisabled]} 
                    onPress={() => startTest(mapController)}
                    disabled={isRunning}
                    activeOpacity={0.8}
                >
                    <Icon 
                        name={isRunning ? "flight" : "play-arrow"} 
                        size={20} 
                        color="#FFFFFF" 
                    />
                    <Text style={styles.buttonText}>
                        {isRunning ? "Benchmark in Progress..." : "Start Automated Tour"}
                    </Text>
                </TouchableOpacity>

                {isReportReady && !isRunning && (
                    <TouchableOpacity 
                        style={[styles.button, styles.exportButton]} 
                        onPress={exportResults}
                        activeOpacity={0.8}
                    >
                        <Icon name="file-download" size={20} color="#FFFFFF" />
                        <Text style={styles.buttonText}>Export Telemetry (CSV)</Text>
                    </TouchableOpacity>
                )}
            </View>
        </SafeAreaView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#FFFFFF',
    },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 16,
        paddingVertical: 12,
        backgroundColor: '#FFFFFF',
        borderBottomWidth: StyleSheet.hairlineWidth,
        borderBottomColor: '#E5E5EA',
    },
    backButton: {
        flexDirection: 'row',
        alignItems: 'center',
        flex: 1,
    },
    backButtonText: {
        fontSize: 17,
        color: '#007AFF',
        marginLeft: 4,
    },
    headerTitle: {
        fontSize: 17,
        fontWeight: '600',
        color: '#000000',
        textAlign: 'center',
        flex: 2,
    },
    headerSpacer: {
        flex: 1,
    },
    statusBar: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: '#F2F2F7',
        paddingHorizontal: 16,
        paddingVertical: 12,
    },
    statusText: {
        fontSize: 14,
        fontWeight: '600',
        color: '#3C3C43',
        flex: 1,
    },
    mapContainer: {
        flex: 1,
    },
    map: {
        ...StyleSheet.absoluteFill,
    },
    controlsContainer: {
        padding: 16,
        backgroundColor: '#FFFFFF',
        gap: 12,
    },
    button: {
        backgroundColor: '#9C27B0',
        paddingVertical: 16,
        paddingHorizontal: 16,
        borderRadius: 12,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
    },
    buttonDisabled: {
        opacity: 0.6,
    },
    exportButton: {
        backgroundColor: '#34C759',
    },
    buttonText: {
        color: '#FFFFFF',
        fontSize: 16,
        fontWeight: '600',
    }
});