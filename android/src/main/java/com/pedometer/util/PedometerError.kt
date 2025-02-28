package com.pedometer.util

import com.pedometer.PedometerConstants

sealed class PedometerError(
  val code: String,
  override val message: String,
  override val cause: Throwable? = null
) : Exception(message, cause) {

  /**
   * 権限関連のエラー
   */
  class PermissionError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_PERMISSION, message, cause)

  /**
   * 歩数追跡関連のエラー
   */
  class TrackingError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_TRACKING, message, cause)

  /**
   * 歩数データ取得関連のエラー
   */
  class StepsError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_STEPS, message, cause)

  /**
   * データベース関連のエラー
   */
  class DatabaseError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_DATABASE, message, cause)

  /**
   * センサー関連のエラー
   */
  class SensorError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_SENSOR, message, cause)

  /**
   * パラメータ不正のエラー
   */
  class InvalidParameterError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_INVALID_PARAMETER, message, cause)

  /**
   * サポートされていない操作のエラー
   */
  class UnsupportedOperationError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_UNSUPPORTED, message, cause)

  /**
   * 予期しないエラー
   */
  class UnexpectedError(
    message: String,
    cause: Throwable? = null
  ) : PedometerError(PedometerConstants.ERROR_UNEXPECTED, message, cause)

  companion object {
    /**
     * Throwableから適切なPedometerErrorを作成する
     */
    fun fromThrowable(throwable: Throwable): PedometerError {
      return when (throwable) {
        is PedometerError -> throwable
        is SecurityException -> PermissionError(
          "セキュリティエラー: ${throwable.message}",
          throwable
        )

        is IllegalStateException -> TrackingError(
          "不正な状態: ${throwable.message}",
          throwable
        )

        is IllegalArgumentException -> InvalidParameterError(
          "不正なパラメータ: ${throwable.message}",
          throwable
        )

        else -> UnexpectedError(
          "予期しないエラー: ${throwable.message}",
          throwable
        )
      }
    }
  }
}
