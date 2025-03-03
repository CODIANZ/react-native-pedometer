import Pedometer, { type StepData } from './NativePedometer';

export type { StepData };

export const {
  isAvailable,
  requestPermission,
  startTracking,
  stopTracking,
  querySteps,
  queryCount,
} = Pedometer;
