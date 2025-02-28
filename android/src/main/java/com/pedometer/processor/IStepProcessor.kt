package com.pedometer.processor

import com.pedometer.util.PedometerResult


interface IStepProcessor {
  /**
   * 新しいセンサー値を処理する
   */
  suspend fun processSensorReading(
    currentSensorSteps: Int,
    timestamp: Long
  ): PedometerResult<StepProcessResult>

  suspend fun initialize(): PedometerResult<String>

  /**
   * 新しいセッションを開始する
   * センサーリセットが検出された場合などに呼び出される
   */
  suspend fun startNewSession(): PedometerResult<String>

  suspend fun finalize(): PedometerResult<Unit>
}

/**
 * 歩数処理の結果
 *
 * @property calculatedSteps 前回からの歩数差分（計算済み）
 * @property sessionId セッションID
 * @property isFirstReading 初回の読み取りかどうか
 * @property isSensorReset センサーリセットが検出されたかどうか
 */
data class StepProcessResult(
  val calculatedSteps: Int,
  val sessionId: String,
  val isFirstReading: Boolean = false,
  val isSensorReset: Boolean = false
) {
  override fun toString(): String {
    return "StepProcessResult(calculatedSteps=$calculatedSteps, " +
      "sessionId='$sessionId', isFirstReading=$isFirstReading, " +
      "isSensorReset=$isSensorReset)"
  }
}
