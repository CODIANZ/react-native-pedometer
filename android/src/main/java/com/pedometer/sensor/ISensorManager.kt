package com.pedometer.sensor

import com.pedometer.util.PedometerResult


interface IStepSensorManager {
  suspend fun initialize(): PedometerResult<Unit>
  fun startTracking(): PedometerResult<String>
  fun stopTracking(): PedometerResult<String>
  fun isSensorAvailable(): Boolean
}
