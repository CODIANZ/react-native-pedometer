import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface Spec extends TurboModule {
  // Permission
  isAvailable: () => Promise<boolean>;
  requestPermission: () => Promise<boolean>;
  // Tracking
  startTracking: () => Promise<void>;
  stopTracking: () => Promise<void>;

  /**
   * @param from UTC timestamp in milliseconds
   * @param to UTC timestamp in milliseconds
   * @returns Number of steps taken between the given timestamps
   */
  queryCount: (from: number, to: number) => Promise<number>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Pedometer');
