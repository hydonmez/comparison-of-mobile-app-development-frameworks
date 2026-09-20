import React, { useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Platform,
  SafeAreaView,
  BackHandler
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';

import { useJsonTestViewModel } from './useJsonTestViewModel';

interface JsonTestScreenProps {
  onNavigateBack: () => void;
}

/**
 * JSON Test UI.
 * Heavy synchronous CPU workloads block the JavaScript thread. To ensure benchmark 
 * integrity and prevent ANR crashes, hardware and software navigation is intercepted 
 * and disabled during the active benchmark phase.
 */
export const JsonTestScreen: React.FC<JsonTestScreenProps> = ({ onNavigateBack }) => {
  const {
    status,
    isRunning,
    isReportReady,
    parsedCount,
    startBenchmark,
    exportResults
  } = useJsonTestViewModel();

  // --- Hardware I/O Interception ---
  useEffect(() => {
    const handleBackPress = () => {
      // Prevents state corruption and ANR crashes by neutralizing the hardware back button 
      // during active serialization loops.
      if (isRunning) {
        return true; 
      }
      
      onNavigateBack();
      return true;
    };

    const backHandler = BackHandler.addEventListener('hardwareBackPress', handleBackPress);
    return () => backHandler.remove();
  }, [isRunning, onNavigateBack]);

  return (
    <SafeAreaView style={styles.safeArea}>
      
      {/* --- Navigation Bar --- */}
      <View style={styles.navBar}>
        <TouchableOpacity 
          style={styles.backButton} 
          onPress={onNavigateBack}
          disabled={isRunning}
        >
          <Text style={[styles.backButtonText, isRunning && styles.textDisabled]}>
            {Platform.OS === 'ios' ? '⟨ Back' : '← Back'}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.container}>
        
        {/* --- Header Section --- */}
        <View style={styles.headerContainer}>
          <Icon name="data-object" size={50} color="#9C27B0" />
          <Text style={styles.headerTitle}>10MB+ Data Deserialization</Text>
        </View>

        {/* --- Status HUD --- */}
        <View style={styles.hudContainer}>
          <View style={styles.statusBox}>
            {/* Enforce monospaced typography to prevent dynamic font-width variations, 
                eliminating layout recalculation bottlenecks during state updates. */}
            <Text style={styles.statusText}>{status}</Text>
          </View>
          
          {parsedCount > 0 && (
            <Text style={styles.successText}>
              {parsedCount} GitHub events processed
            </Text>
          )}
        </View>

        {/* --- Benchmark Execution Trigger --- */}
        {/* Disable UI updates during the run to save thread capacity. */}
        <TouchableOpacity
          style={[styles.primaryButton, isRunning && styles.buttonDisabled]}
          onPress={startBenchmark}
          disabled={isRunning}
          activeOpacity={0.8}
        >
          <Icon name="memory" size={18} color="#FFFFFF" style={styles.iconSpacing} />
          <Text style={styles.buttonText}>
            Start JSON Parsing Benchmark
          </Text>
        </TouchableOpacity>

        {/* --- Telemetry Export --- */}
        {isReportReady && (
          <TouchableOpacity
            style={styles.exportButton}
            onPress={exportResults}
            activeOpacity={0.8}
          >
            <Icon name="system-update-alt" size={18} color="#FFFFFF" style={styles.iconSpacing} />
            <Text style={styles.buttonText}>Export Results (CSV)</Text>
          </TouchableOpacity>
        )}

        <View style={styles.spacer} />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  navBar: {
    width: '100%',
    height: 50,
    justifyContent: 'center',
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5EA',
  },
  backButton: {
    paddingVertical: 8,
    paddingRight: 16,
    alignSelf: 'flex-start',
  },
  backButtonText: {
    fontSize: 17,
    color: '#007AFF',
    fontWeight: '500',
  },
  textDisabled: {
    color: '#A1A1A6',
  },
  container: {
    flex: 1,
    padding: 16,
    alignItems: 'center',
  },
  headerContainer: {
    alignItems: 'center',
    marginTop: 20,
    marginBottom: 25,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000000',
    marginTop: 10,
  },
  hudContainer: {
    width: '100%',
    alignItems: 'center',
    marginBottom: 25,
  },
  statusBox: {
    width: '100%',
    backgroundColor: '#F2F2F7',
    padding: 16,
    borderRadius: 12,
    marginBottom: 15,
  },
  statusText: {
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 14,
    textAlign: 'center',
    color: '#000000',
  },
  successText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#34C759',
  },
  primaryButton: {
    flexDirection: 'row',
    width: '100%',
    backgroundColor: '#9C27B0',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  exportButton: {
    flexDirection: 'row',
    width: '100%',
    backgroundColor: '#007AFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 10,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  iconSpacing: {
    marginRight: 8,
  },
  buttonText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  spacer: {
    flex: 1,
  },
});