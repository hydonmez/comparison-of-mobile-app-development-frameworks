/**
 * @format
 */

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

import { useLaunchPerformanceStore } from './src/core/manager/LaunchPerformanceManager';
useLaunchPerformanceStore.getState().appStarted();

AppRegistry.registerComponent(appName, () => App);