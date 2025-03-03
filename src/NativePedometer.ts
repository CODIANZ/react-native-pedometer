import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface StepData {
  timestamp: number;
  steps: number;
  calculatedSteps: number;
  sensorSteps?: number;
  sessionId: string;
}

export interface Spec extends TurboModule {
  // Permission
  isAvailable: () => Promise<boolean>;
  requestPermission: () => Promise<boolean>;
  // Tracking
  startTracking: () => Promise<void>;
  stopTracking: () => Promise<void>;
  // Data traversal

  /**
   * @param from UTC timestamp in milliseconds
   * @param to UTC timestamp in milliseconds
   * @returns The total number of steps between the given timestamps
   */
  queryCount: (from: number, to: number) => Promise<number>;

  /**
   * Android only
   * @param from UTC timestamp in milliseconds
   * @param to UTC timestamp in milliseconds
   * @returns Detailed step data between the given timestamps
   */
  querySteps: (from: number, to: number) => Promise<StepData[]>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Pedometer');
