package com.pedometer.state

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.pedometer.util.PedometerError
import com.pedometer.util.PedometerResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class PedometerStateManager(context: Context) {
  companion object {
    private const val TAG = "PedometerStateManager"
    private const val PREFS_NAME = "PedometerPrefs"

    // 保存するキー
    private const val KEY_LAST_SENSOR_STEPS = "lastSensorSteps"
    private const val KEY_SESSION_ID = "sessionId"
    private const val KEY_TOTAL_STEPS = "totalSteps"
    private const val KEY_LAST_TIMESTAMP = "lastTimestamp"
    private const val KEY_IS_TRACKING = "isTracking"
  }

  // SharedPreferences
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  // 並行アクセス制御用ミューテックス
  private val mutex = Mutex()

  /**
   * 最後に記録されたセンサーの歩数値を取得
   *
   * @return 歩数値、未設定の場合は-1
   */
  suspend fun getLastSensorSteps(): Int = mutex.withLock {
    return prefs.getInt(KEY_LAST_SENSOR_STEPS, -1)
  }

  /**
   * 最後に記録されたセンサーの歩数値を保存
   *
   * @param steps 歩数値
   */
  suspend fun setLastSensorSteps(steps: Int): PedometerResult<Unit> = mutex.withLock {
    try {
      prefs.edit().putInt(KEY_LAST_SENSOR_STEPS, steps).apply()
      PedometerResult.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "歩数値の保存に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("歩数値の保存に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * 現在のセッションIDを取得
   *
   * @return セッションID、未設定の場合は空文字列
   */
  suspend fun getSessionId(): String = mutex.withLock {
    return prefs.getString(KEY_SESSION_ID, "") ?: ""
  }

  /**
   * 新しいセッションIDを生成して保存
   *
   * @return 生成されたセッションID
   */
  suspend fun generateNewSessionId(): PedometerResult<String> = mutex.withLock {
    try {
      val newSessionId = "session-${UUID.randomUUID()}"
      prefs.edit().putString(KEY_SESSION_ID, newSessionId).apply()
      Log.d(TAG, "新しいセッションIDを生成: $newSessionId")
      PedometerResult.success(newSessionId)
    } catch (e: Exception) {
      Log.e(TAG, "セッションIDの生成に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("セッションIDの生成に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * セッションIDを保存
   *
   * @param sessionId セッションID
   */
  suspend fun setSessionId(sessionId: String): PedometerResult<Unit> = mutex.withLock {
    try {
      prefs.edit().putString(KEY_SESSION_ID, sessionId).apply()
      Log.d(TAG, "セッションIDを保存: $sessionId")
      PedometerResult.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "セッションIDの保存に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("セッションIDの保存に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * 累積歩数を取得
   *
   * @return 累積歩数
   */
  suspend fun getTotalSteps(): Long = mutex.withLock {
    return prefs.getLong(KEY_TOTAL_STEPS, 0L)
  }

  /**
   * 累積歩数を保存
   *
   * @param steps 累積歩数
   */
  suspend fun setTotalSteps(steps: Long): PedometerResult<Unit> = mutex.withLock {
    try {
      prefs.edit().putLong(KEY_TOTAL_STEPS, steps).apply()
      PedometerResult.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "累積歩数の保存に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("累積歩数の保存に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * 最後のタイムスタンプを取得
   *
   * @return タイムスタンプ（ミリ秒）
   */
  suspend fun getLastTimestamp(): Long = mutex.withLock {
    return prefs.getLong(KEY_LAST_TIMESTAMP, 0L)
  }

  /**
   * 最後のタイムスタンプを保存
   *
   * @param timestamp タイムスタンプ（ミリ秒）
   */
  suspend fun setLastTimestamp(timestamp: Long): PedometerResult<Unit> = mutex.withLock {
    try {
      prefs.edit().putLong(KEY_LAST_TIMESTAMP, timestamp).apply()
      PedometerResult.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "タイムスタンプの保存に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("タイムスタンプの保存に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * 追跡状態を取得
   *
   * @return 追跡中の場合はtrue
   */
  suspend fun isTracking(): Boolean = mutex.withLock {
    return prefs.getBoolean(KEY_IS_TRACKING, false)
  }

  /**
   * 追跡状態を設定
   *
   * @param tracking 追跡中の場合はtrue
   */
  suspend fun setTracking(tracking: Boolean): PedometerResult<Unit> = mutex.withLock {
    try {
      prefs.edit().putBoolean(KEY_IS_TRACKING, tracking).apply()
      PedometerResult.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "追跡状態の保存に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("追跡状態の保存に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * 複数の状態を一度に保存
   *
   * @param state 保存する状態
   */
  suspend fun saveState(state: PedometerState): PedometerResult<Unit> = mutex.withLock {
    try {
      prefs.edit()
        .putInt(KEY_LAST_SENSOR_STEPS, state.lastSensorSteps)
        .putString(KEY_SESSION_ID, state.sessionId)
        .putLong(KEY_TOTAL_STEPS, state.totalSteps)
        .putLong(KEY_LAST_TIMESTAMP, state.lastTimestamp)
        .putBoolean(KEY_IS_TRACKING, state.isTracking)
        .apply()

      Log.d(TAG, "状態を保存: $state")
      PedometerResult.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "状態の保存に失敗しました", e)
      PedometerResult.failure(
        PedometerError.TrackingError("状態の保存に失敗しました: ${e.message}", e)
      )
    }
  }

  /**
   * 現在の状態をすべて取得
   *
   * @return 現在の状態
   */
  suspend fun getState(): PedometerState = mutex.withLock {
    return PedometerState(
      lastSensorSteps = prefs.getInt(KEY_LAST_SENSOR_STEPS, -1),
      sessionId = prefs.getString(KEY_SESSION_ID, "") ?: "",
      totalSteps = prefs.getLong(KEY_TOTAL_STEPS, 0L),
      lastTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP, 0L),
      isTracking = prefs.getBoolean(KEY_IS_TRACKING, false)
    )
  }
}

data class PedometerState(
  val lastSensorSteps: Int = -1,
  val sessionId: String = "",
  val totalSteps: Long = 0L,
  val lastTimestamp: Long = 0L,
  val isTracking: Boolean = false
) {
  override fun toString(): String {
    return "PedometerState(lastSensorSteps=$lastSensorSteps, " +
      "sessionId='$sessionId', totalSteps=$totalSteps, " +
      "lastTimestamp=$lastTimestamp, isTracking=$isTracking)"
  }
}
