package com.pedometer

object PedometerConstants {
  // エラーコード
  const val ERROR_PERMISSION = "PERMISSION_ERROR"
  const val ERROR_TRACKING = "TRACKING_ERROR"
  const val ERROR_STEPS = "STEPS_ERROR"
  const val ERROR_DATABASE = "DATABASE_ERROR"
  const val ERROR_SENSOR = "SENSOR_ERROR"
  const val ERROR_INVALID_PARAMETER = "INVALID_PARAMETER_ERROR"
  const val ERROR_UNSUPPORTED = "UNSUPPORTED_OPERATION_ERROR"
  const val ERROR_UNEXPECTED = "UNEXPECTED_ERROR"

  // イベント名
  const val EVENT_START_TRACKING = "onStartTracking"
  const val EVENT_STOP_TRACKING = "onStopTracking"
  const val EVENT_STEPS_UPDATED = "onStepsUpdated"

  // パーミッション
  const val PERMISSION_REQUEST_CODE = 123
  const val PERMISSION_REQUEST_TIMEOUT = 30_000L // 30秒

  // 設定値
  const val DEFAULT_KEEP_DATA_DAYS = 30L // データ保持日数
}
