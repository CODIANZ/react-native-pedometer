package com.pedometer.processor

import android.util.Log
import com.pedometer.state.PedometerStateManager
import com.pedometer.util.ErrorHandler
import com.pedometer.util.PedometerResult
import com.pedometer.util.fold
import kotlin.math.abs

class StepProcessor(
  private val stateManager: PedometerStateManager
) : IStepProcessor {
  companion object {
    private const val TAG = "StepProcessor"

    // センサーリセット検出の閾値
    // センサー値が前回より大幅に小さくなった場合にリセットと判断
    private const val SENSOR_RESET_THRESHOLD = 1000
  }

  override suspend fun initialize(): PedometerResult<String> {
    return ErrorHandler.runCatching {
      // デバイス再起動をチェック
      val rebootDetected = stateManager.checkDeviceReboot()

      var sessionId = stateManager.getSessionId()

      // セッションIDが空の場合またはデバイス再起動が検出された場合は新規生成
      if (sessionId.isEmpty() || rebootDetected) {
        val result = stateManager.generateNewSessionId()
        sessionId = result.fold(
          onSuccess = { it },
          onFailure = { throw it }
        )
        Log.d(
          TAG,
          "新しいセッションIDを生成: $sessionId ${if (rebootDetected) "(デバイス再起動を検出)" else ""}"
        )
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
      val sessionId = stateManager.getSessionId()
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
        // センサーリセットの検出
        val isReset = detectSensorReset(currentSensorSteps, lastSensorSteps)

        // 歩数の減少検出（リセットとは区別）
        val stepDiff = currentSensorSteps - lastSensorSteps
        val isStepDecreased = stepDiff < 0 && !isReset

        // 歩数差分の計算
        val calculatedStepDiff = when {
          isReset -> {
            // リセット時は累積歩数に現在値を加算
            currentSensorSteps
          }

          isStepDecreased -> {
            // 歩数減少時は、減少分を補正して加算
            // 小さな減少（5歩以下）は通常の変動として加算、大きな減少は異常値として0扱い
            if (abs(stepDiff) <= 5) abs(stepDiff) else 0
          }

          else -> {
            // 通常時は正の差分
            stepDiff
          }
        }

        // リセットが検出された場合は新しいセッション開始
        var updatedSessionId = sessionId
        if (isReset) {
          val result = startNewSession()
          if (result is PedometerResult.Success) {
            updatedSessionId = result.value
            Log.d(TAG, "新しいセッションを開始: $updatedSessionId（リセット検出）")
          }
        }

        // 現在の状態を更新
        stateManager.setLastSensorSteps(currentSensorSteps)
        stateManager.setLastTimestamp(timestamp)

        // 累積歩数の更新
        if (calculatedStepDiff > 0) {
          val totalSteps = stateManager.getTotalSteps() + calculatedStepDiff
          stateManager.setTotalSteps(totalSteps)
          Log.d(TAG, "累積歩数に加算: +$calculatedStepDiff, 合計=${totalSteps}")
        }

        // 結果を返す
        StepProcessResult(
          calculatedSteps = calculatedStepDiff,
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
      val newSessionId = result.fold(
        onSuccess = { it },
        onFailure = { throw it }
      )
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
    val isLargeDecreased = currentSensorSteps < lastSensorSteps - SENSOR_RESET_THRESHOLD

    // 新規値が異常に小さい（典型的な再起動の兆候）
    val isTooSmall = lastSensorSteps > 500 && currentSensorSteps < 50

    // 新規値が異常に大きく異なる（典型的なセンサーのリセット）
    val isDrasticallyDifferent = abs(currentSensorSteps - lastSensorSteps) > 10000

    // いずれかの条件に合致したらリセットと判断
    val isReset = isLargeDecreased || isTooSmall || isDrasticallyDifferent

    if (isReset) {
      val reason = when {
        isLargeDecreased -> "大幅な減少"
        isTooSmall -> "異常に小さい値"
        else -> "異常に大きな変化"
      }
      Log.d(
        TAG,
        "センサーリセットを検出: 前回=$lastSensorSteps, 現在=$currentSensorSteps, 理由=$reason"
      )
    }

    return isReset
  }

}
