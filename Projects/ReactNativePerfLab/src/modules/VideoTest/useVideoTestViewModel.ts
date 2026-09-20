import { useState, useCallback, useRef, useEffect } from 'react';
import { Platform } from 'react-native';
import Orientation from 'react-native-orientation-locker';
import Share from 'react-native-share';

import { usePerformanceStore } from '../../core/manager/PerformanceManager';
import { ExportManager } from '../../core/manager/ExportManager';
import { engine } from '../../core/engine/VideoEngine';

export const useVideoTestViewModel = () => {
  const [isTesting, setIsTesting] = useState<boolean>(false);
  const [isFullScreen, setIsFullScreen] = useState<boolean>(false);
  const [exportUri, setExportUri] = useState<string | null>(null);
  const [isReportReady, setIsReportReady] = useState<boolean>(false);

  const lastTestSuffix = useRef<string>("Partial");
  const isTestingRef = useRef<boolean>(false);

  useEffect(() => {
    isTestingRef.current = isTesting;
  }, [isTesting]);

  useEffect(() => {
    const unsubscribe = engine.subscribe((hasEnded: boolean) => {
      if (hasEnded && isTestingRef.current) {
        stopTestAndExport(true);
      }
    });
    return () => unsubscribe();
  }, []);

  const forceRotation = useCallback((isLandscape: boolean) => {
    // iOS takes slightly longer to process orientation changes than Android.
    // 400ms is a safe threshold for iOS.
    const delay = Platform.OS === 'ios' ? 400 : 200;
    setTimeout(() => {
      if (isLandscape) {
        Orientation.lockToLandscape();
      } else {
        Orientation.lockToPortrait();
      }
    }, delay);
  }, []);

  const startTest = useCallback(() => {
    if (isTestingRef.current) return;

    setIsReportReady(false);
    setExportUri(null);

    forceRotation(true);

    // Wait slightly longer than the rotation delay to ensure orientation locks.
    const uiDelay = Platform.OS === 'ios' ? 500 : 250;
    setTimeout(() => {
      setIsTesting(true);
      setIsFullScreen(true);
      usePerformanceStore.getState().startMonitoring();
      engine.playFromStart();
    }, uiDelay);

  }, [forceRotation]);

  const stopTestAndExport = useCallback(async (isFinished: boolean = false) => {
    if (!isTestingRef.current && !isFinished) return;

    setIsTesting(false);
    setIsFullScreen(false);

    engine.stop();
    usePerformanceStore.getState().stopMonitoring();
    forceRotation(false);

    lastTestSuffix.current = isFinished ? "Complete" : "Partial";

    try {
      const testName = `Video_ReactNative_Component_${lastTestSuffix.current}`;
      const logs = usePerformanceStore.getState().currentLogs;

      setTimeout(async () => {
        const uri = await ExportManager.generateCSV(logs, testName);
        if (uri) {
          setExportUri(uri);
          setIsReportReady(true);
        }
      }, 100);

    } catch (error) {
      console.error(`[PerfLab] Telemetry Export Failed:`, error);
    }
  }, [forceRotation]);

  const shareResults = useCallback(async () => {
    if (!exportUri) return;

    try {
      const validUrl = exportUri.startsWith('file://') ? exportUri : `file://${exportUri}`;
      await Share.open({
        url: validUrl,
        type: 'text/csv',
        failOnCancel: false
      });
    } catch (error: any) {
      if (error.message !== 'User did not share') {
        console.error(`❌ [PerfLab] Share interface failed: ${error.message}`);
      }
    }
  }, [exportUri]);

  return {
    isTesting,
    isFullScreen,
    exportUri,
    isReportReady,
    startTest,
    stopTestAndExport,
    shareResults,
    forceRotation,
  };
};