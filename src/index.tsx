import Pedometer from './NativePedometer';

export const {
  isAvailable,
  requestPermission,
  startTracking,
  stopTracking,
  queryCount,
} = Pedometer;
