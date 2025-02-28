package com.pedometer.processor

import android.util.Log
import com.pedometer.state.PedometerStateManager
import com.pedometer.util.ErrorHandler
import com.pedometer.util.PedometerResult

class StepProcessor(
  private val stateManager: PedometerStateManager
) : IStepProcessor {
  companion object {
    private const val TAG = "StepProcessor"


    // センサーリセット検出の閾値
    // センサー値が前回より大幅に小さくなった場合にリセットと判断
    private const val SENSOR_RESET_THRESHOLD = 1000

    // デバッグモード
    private const val DEBUG_MODE = true
  }

  override suspend fun initialize(): PedometerResult<String> {
    return ErrorHandler.runCatching {
      var sessionId = stateManager.getSessionId()

      // セッションIDが空の場合は新規生成
      if (sessionId.isEmpty()) {
        val result = stateManager.generateNewSessionId()
        if (result is PedometerResult.Failure) {
          throw result.error
        }
        sessionId = (result as PedometerResult.Success).value
      }

      Log.d(TAG, "StepProcessor初期化完了: sessionId=$sessionId")
      sessionId
    }
  }

override suspend fun processSensorReading(
  currentSensorSteps: Int,
  timestamp: Long
): PedometerResult<StepProcessResult> {
  return ErrorHandler.runCatching {
    val lastSensorSteps = stateManager.getLastSensorSteps()
    val lastTimestamp = stateManager.getLastTimestamp()
    val sessionId = stateManager.getSessionId()

    // デバッグログ
    if (DEBUG_MODE) {
      Log.d(TAG, "歩数処理: 現在のセンサー値=$currentSensorSteps, 前回のセンサー値=$lastSensorSteps, 差分=${currentSensorSteps - lastSensorSteps}")
    }

    // 初回の場合
    if (lastSensorSteps == -1) {
      // 状態を更新
      stateManager.setLastSensorSteps(currentSensorSteps)
      stateManager.setLastTimestamp(timestamp)

      // 初回は差分なし
      StepProcessResult(
        calculatedSteps = 0,
        sessionId = sessionId,
        isFirstReading = true
      )
    } else {
      // 時間間隔の計算（分単位）
      val minutesSinceLastRecord = (timestamp - lastTimestamp) / (1000.0 * 60)

      // センサーリセットの検出
      val isReset = detectSensorReset(currentSensorSteps, lastSensorSteps)

      // 歩数差分の計算
      val stepDiff = calculateStepDifference(
        currentSensorSteps,
        lastSensorSteps,
        minutesSinceLastRecord,
        isReset
      )

      // リセットが検出された場合は新しいセッション開始
      var updatedSessionId = sessionId
      if (isReset) {
        val result = startNewSession()
        if (result is PedometerResult.Success) {
          updatedSessionId = result.value
        }
      }

      // 現在の状態を更新
      stateManager.setLastSensorSteps(currentSensorSteps)
      stateManager.setLastTimestamp(timestamp)

      // 有効な歩数差分のみ累積に加算
      // 最小閾値を設定して、微小な変化は無視する
      val MIN_STEP_THRESHOLD = 0 // センサー値が少なくとも2以上変化した場合のみ有効とする
      if (stepDiff > MIN_STEP_THRESHOLD) {
        val totalSteps = stateManager.getTotalSteps() + stepDiff
        stateManager.setTotalSteps(totalSteps)
        Log.d(TAG, "累積歩数に加算: +$stepDiff, 合計=${totalSteps}")
      } else {
        Log.d(TAG, "閾値以下の歩数変化: $stepDiff - 累積に加算しません")
      }

      // 結果を返す
      StepProcessResult(
        calculatedSteps = if (stepDiff > MIN_STEP_THRESHOLD) stepDiff else 0,
        sessionId = updatedSessionId,
        isFirstReading = false,
        isSensorReset = isReset
      )
    }
  }
}

  override suspend fun startNewSession(): PedometerResult<String> {
    return ErrorHandler.runCatching {
      val result = stateManager.generateNewSessionId()
      if (result is PedometerResult.Failure) {
        throw result.error
      }

      val newSessionId = (result as PedometerResult.Success).value
      Log.d(TAG, "新しいセッションを開始: $newSessionId")
      newSessionId
    }
  }

  override suspend fun finalize(): PedometerResult<Unit> {
    // 現在の状態をすべて保存
    // すでに個別に保存しているので追加の処理は必要ない
    return PedometerResult.success(Unit)
  }

  private fun detectSensorReset(currentSensorSteps: Int, lastSensorSteps: Int): Boolean {
    // センサー値が前回より大幅に減少した場合はリセットと判断
    val isReset = currentSensorSteps < lastSensorSteps - SENSOR_RESET_THRESHOLD

    if (isReset) {
      Log.d(TAG, "センサーリセットを検出: 前回=$lastSensorSteps, 現在=$currentSensorSteps")
    }

    return isReset
  }

  /**
   * 歩数差分を計算する
   *
   * @param currentSensorSteps 現在のセンサー値
   * @param lastSensorSteps 前回のセンサー値
   * @param minutesSinceLastRecord 前回記録からの経過時間（分）
   * @param isReset センサーリセットが検出された場合はtrue
   * @return 計算された歩数差分
   */
  private fun calculateStepDifference(
    currentSensorSteps: Int,
    lastSensorSteps: Int,
    minutesSinceLastRecord: Double,
    isReset: Boolean
  ): Int {
    // リセット時は現在のセンサー値をそのまま使用
    if (isReset) {
      Log.d(TAG, "センサーリセットを検出: 現在のセンサー値($currentSensorSteps)を使用")
      return currentSensorSteps
    }

    // センサーからは累積値が返ってくるので差分を計算する
    val stepDiff = currentSensorSteps - lastSensorSteps

    // 差分が0の場合は明示的に0を返す
    if (stepDiff == 0) {
      return 0
    }

    // 負の値は異常なので0として扱う
    if (stepDiff < 0) {
      return 0
    }

    return stepDiff
  }
}
