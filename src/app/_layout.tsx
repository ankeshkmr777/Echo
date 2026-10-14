import { AnimatedSplashOverlay } from '@/components/animated-icon';
import AppTabs from '@/components/app-tabs';
import { DarkTheme, DefaultTheme, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { useColorScheme } from 'react-native';
// 1. Import the new permission gate component
import RadioAccessGate from '../components/RadioAccessGate';

SplashScreen.preventAutoHideAsync();

export default function TabLayout() {
  const colorScheme = useColorScheme();
  useEffect(() => {
    SplashScreen.hideAsync();
  }, []);
  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      {/* 2. Wrap the gate around your app's main visual components */}
      <RadioAccessGate>
        <AnimatedSplashOverlay />
        <AppTabs />
      </RadioAccessGate>
    </ThemeProvider>
  );
}