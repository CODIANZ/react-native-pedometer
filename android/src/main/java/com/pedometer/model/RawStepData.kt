package com.pedometer.model

data class RawStepData(
  // タイムスタンプ（ミリ秒単位）
  val timestamp: Long,

  // センサーから直接取得した歩数値（デバイス起動時からの累積）
  val sensorTotalSteps: Int,

  // 計算された差分歩数（前回記録からの増加分）
  val calculatedSteps: Int = 0,

  // セッションID（センサーリセット間の識別子）
  // デバイス再起動などでセンサーがリセットされた場合に変更される
  val sessionId: String = ""
)
