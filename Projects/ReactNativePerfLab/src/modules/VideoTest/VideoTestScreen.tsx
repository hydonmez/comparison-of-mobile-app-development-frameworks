import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TouchableWithoutFeedback,
  SafeAreaView,
  StatusBar,
  Platform,
  BackHandler,
} from 'react-native';
import Video, { OnProgressData } from 'react-native-video';
import SystemNavigationBar from 'react-native-system-navigation-bar';

import { useVideoTestViewModel } from './useVideoTestViewModel';
import { usePerformanceStore } from '../../core/manager/PerformanceManager';

interface VideoTestScreenProps {
  onNavigateBack: () => void;
}

export const VideoTestScreen: React.FC<VideoTestScreenProps> = ({ onNavigateBack }) => {
  const viewModel = useVideoTestViewModel();

  useEffect(() => {
    const handleHardwareBackPress = () => {
      if (viewModel.isFullScreen) {
        viewModel.stopTestAndExport(false);
        return true;
      } else if (onNavigateBack) {
        onNavigateBack();
        return true;
      }
      return false;
    };

    const backHandler = BackHandler.addEventListener('hardwareBackPress', handleHardwareBackPress);

    return () => {
      backHandler.remove();
      viewModel.stopTestAndExport(false);
      if (Platform.OS === 'android') {
        SystemNavigationBar.navigationShow();
      }
    };
  }, [viewModel.isFullScreen, onNavigateBack, viewModel]);

  if (viewModel.isFullScreen) {
    return (
      <ActiveVideoBenchmarkOverlay
        viewModel={viewModel}
        onTerminate={() => viewModel.stopTestAndExport(false)}
      />
    );
  }

  return (
    <IdlePreparationUI
      onStart={() => viewModel.startTest()}
      isReportReady={viewModel.isReportReady}
      onExport={() => viewModel.shareResults()}
      onNavigateBack={onNavigateBack}
    />
  );
};

interface ActiveOverlayProps {
  viewModel: ReturnType<typeof useVideoTestViewModel>;
  onTerminate: () => void;
}

const ActiveVideoBenchmarkOverlay: React.FC<ActiveOverlayProps> = ({ viewModel, onTerminate }) => {
  const [controlsVisible, setControlsVisible] = useState(false);
  const [isPaused, setIsPaused]               = useState(false);
  const [currentTime, setCurrentTime]         = useState(0);
  const [duration, setDuration]               = useState(0);
  const videoRef = useRef<any>(null);
  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (Platform.OS === 'android') {
      StatusBar.setTranslucent(true);
      StatusBar.setBackgroundColor('transparent');
      SystemNavigationBar.stickyImmersive();
    }
    StatusBar.setHidden(true, 'fade');

    return () => {
      if (Platform.OS === 'android') {
        StatusBar.setTranslucent(false);
        SystemNavigationBar.navigationShow();
      }
      StatusBar.setHidden(false, 'fade');
      viewModel.forceRotation(false);
    };
  }, [viewModel]);

  // Show controls and automatically hide them after 3 seconds
  const showControlsTemporarily = () => {
    setControlsVisible(true);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    hideTimer.current = setTimeout(() => setControlsVisible(false), 3000);
  };

  const handlePlayPause = () => {
    setIsPaused(prev => !prev);
    showControlsTemporarily();
  };

  const handleSeek = (seconds: number) => {
    const target = Math.max(0, Math.min(currentTime + seconds, duration));
    videoRef.current?.seek(target);
    setCurrentTime(target);
    showControlsTemporarily();
  };

  // Time format: mm:ss
  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const progress = duration > 0 ? currentTime / duration : 0;

  return (
    <TouchableWithoutFeedback onPress={showControlsTemporarily}>
      <View style={styles.benchmarkContainer}>
        <Video
          ref={videoRef}
          source={require('../../assets/test_video_1080p.mp4')}
          style={StyleSheet.absoluteFill}
          resizeMode="cover"
          paused={!viewModel.isTesting || isPaused}
          controls={false}
          onProgress={(data: OnProgressData) => setCurrentTime(data.currentTime)}
          onLoad={(data: any) => setDuration(data.duration)}
          onEnd={() => {
            viewModel.stopTestAndExport(true);
          }}
          onError={(e) => {
            console.error("[PerfLab_Error]", e);
            onTerminate();
          }}
          bufferConfig={{
            minBufferMs: 5000,
            maxBufferMs: 5000,
            bufferForPlaybackMs: 1000,
            bufferForPlaybackAfterRebufferMs: 2500,
          }}
        />

        {/* Static HUD — always visible */}
        <View style={styles.hudLayer} pointerEvents="box-none">
          <TouchableOpacity style={styles.terminateButton} onPress={onTerminate} activeOpacity={0.8}>
            <Text style={styles.terminateText}>Terminate Benchmark</Text>
          </TouchableOpacity>
          <View style={styles.fpsContainer}>
            <FPSIndicator />
          </View>
        </View>

        {/* Tap controls — visible on interaction */}
        {controlsVisible && (
          <View style={styles.controlsOverlay} pointerEvents="box-none">

            {/* Center: Rewind / Play-Pause / Fast Forward */}
            <View style={styles.centerControls} pointerEvents="auto">
              <TouchableOpacity style={styles.seekButton} onPress={() => handleSeek(-10)} activeOpacity={0.8}>
                <Text style={styles.seekIcon}>↺</Text>
                <Text style={styles.seekLabel}>10</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.playPauseButton} onPress={handlePlayPause} activeOpacity={0.8}>
                <Text style={styles.playPauseIcon}>{isPaused ? '▶' : '⏸'}</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.seekButton} onPress={() => handleSeek(10)} activeOpacity={0.8}>
                <Text style={styles.seekIcon}>↻</Text>
                <Text style={styles.seekLabel}>10</Text>
              </TouchableOpacity>
            </View>

            {/* Bottom: Progress bar + time */}
            <View style={styles.progressContainer} pointerEvents="auto">
              <Text style={styles.timeText}>{formatTime(currentTime)}</Text>
              <View style={styles.progressBar}>
                <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
              </View>
              <Text style={styles.timeText}>{formatTime(duration)}</Text>
            </View>

          </View>
        )}
      </View>
    </TouchableWithoutFeedback>
  );
};

const FPSIndicator: React.FC = () => {
  const currentFPS = usePerformanceStore((state) => state.currentFPS);
  const isCritical = currentFPS < 50;
  return (
    <View style={styles.fpsPill}>
      <Text style={[styles.fpsText, { color: isCritical ? '#FF3B30' : '#34C759' }]}>
        FPS: {currentFPS}
      </Text>
    </View>
  );
};

interface IdleUIProps {
  onStart: () => void;
  isReportReady: boolean;
  onExport: () => void;
  onNavigateBack: () => void;
}

const IdlePreparationUI: React.FC<IdleUIProps> = ({ onStart, isReportReady, onExport, onNavigateBack }) => {
  return (
    <SafeAreaView style={styles.idleContainer}>
      <TouchableOpacity style={styles.navBar} onPress={onNavigateBack} activeOpacity={0.6}>
        <Text style={styles.backIcon}>←</Text>
        <Text style={styles.navTitle}>Video Benchmark</Text>
        <View style={styles.navSpacer} />
      </TouchableOpacity>

      <View style={styles.contentCenter}>
        <View style={styles.iconPlaceholder}>
          <Text style={styles.playIconText}>▶</Text>
        </View>
        <Text style={styles.titleText}>Full Screen Video Benchmark</Text>
        <Text style={styles.descriptionText}>
          The video plays from start to finish while hardware telemetry is recorded.
          The display will be locked to landscape orientation for high-fidelity decoding.
        </Text>
        <View style={styles.spacer40} />

        {isReportReady && (
          <View style={styles.reportCard}>
            <Text style={styles.reportTitle}>Benchmark Report Ready</Text>
            <TouchableOpacity style={styles.exportButton} onPress={onExport} activeOpacity={0.8}>
              <Text style={styles.exportIcon}>➦</Text>
              <Text style={styles.exportButtonText}>Export Results (CSV)</Text>
            </TouchableOpacity>
          </View>
        )}

        <View style={styles.spacer40} />
        <TouchableOpacity style={styles.startButton} onPress={onStart} activeOpacity={0.8}>
          <Text style={styles.startButtonText}>START FULL SCREEN BENCHMARK</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  benchmarkContainer: {
    ...StyleSheet.absoluteFill,
    backgroundColor: '#000000',
    zIndex: 999,
  },

  // Static HUD
  hudLayer: { ...StyleSheet.absoluteFill, zIndex: 10 },
  terminateButton: {
    position: 'absolute', top: 50, left: 20,
    backgroundColor: 'rgba(255, 59, 48, 0.85)',
    paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8,
  },
  terminateText: { color: '#FFFFFF', fontSize: 12, fontWeight: '600' },
  fpsContainer: { position: 'absolute', top: 50, right: 20 },
  fpsPill: { backgroundColor: 'rgba(0,0,0,0.6)', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },
  fpsText: { fontWeight: 'bold', fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontSize: 14 },

  // Controls Overlay
  controlsOverlay: {
    ...StyleSheet.absoluteFill,
    zIndex: 20,
    backgroundColor: 'rgba(0,0,0,0.35)',
    justifyContent: 'space-between',
    paddingBottom: 24,
  },
  centerControls: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 40,
  },
  seekButton: { alignItems: 'center', justifyContent: 'center' },
  seekIcon: { color: '#FFFFFF', fontSize: 32 },
  seekLabel: { color: '#FFFFFF', fontSize: 11, marginTop: -4 },
  playPauseButton: {
    width: 64, height: 64, borderRadius: 32,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center', justifyContent: 'center',
  },
  playPauseIcon: { color: '#FFFFFF', fontSize: 28 },

  // Progress Bar
  progressContainer: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: 20, gap: 10,
  },
  timeText: { color: '#FFFFFF', fontSize: 12, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', minWidth: 36 },
  progressBar: {
    flex: 1, height: 4, backgroundColor: 'rgba(255,255,255,0.3)', borderRadius: 2, overflow: 'hidden',
  },
  progressFill: { height: '100%', backgroundColor: '#FFFFFF', borderRadius: 2 },

  // Idle UI
  idleContainer: { flex: 1, backgroundColor: '#FFFFFF' },
  navBar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingTop: 16, paddingBottom: 10 },
  backIcon: { fontSize: 24, fontWeight: 'bold', color: '#000000' },
  navTitle: { fontSize: 20, fontWeight: 'bold', color: '#000000', marginLeft: 16 },
  navSpacer: { flex: 1 },
  contentCenter: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
  iconPlaceholder: { width: 100, height: 100, justifyContent: 'center', alignItems: 'center', marginBottom: 20 },
  playIconText: { fontSize: 80, color: '#007AFF' },
  titleText: { fontSize: 22, fontWeight: 'bold', marginBottom: 10, color: '#000000', textAlign: 'center' },
  descriptionText: { fontSize: 15, color: '#8E8E93', textAlign: 'center', paddingHorizontal: 16 },
  spacer40: { height: 40 },
  reportCard: { width: '100%', backgroundColor: 'rgba(142,142,147,0.1)', borderRadius: 12, padding: 16, alignItems: 'center' },
  reportTitle: { fontWeight: 'bold', marginBottom: 10, color: '#000000' },
  exportButton: { backgroundColor: '#34C759', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12, borderRadius: 10 },
  exportIcon: { color: '#FFFFFF', fontSize: 16, fontWeight: 'bold' },
  exportButtonText: { color: '#FFFFFF', fontWeight: '600', marginLeft: 8 },
  startButton: { width: '100%', height: 56, backgroundColor: '#007AFF', justifyContent: 'center', alignItems: 'center', borderRadius: 12 },
  startButtonText: { color: '#FFFFFF', fontWeight: 'bold', fontSize: 16 },
});