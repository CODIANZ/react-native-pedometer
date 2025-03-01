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

  /**
   * @param from UTC timestamp in milliseconds
   * @param to UTC timestamp in milliseconds
   * @returns Detailed step data between the given timestamps
   */
  querySteps: (from: number, to: number) => Promise<StepData[]>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Pedometer');
