package com.pedometer.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "steps",
  indices = [
    Index(value = ["timestamp"]),
    Index(value = ["session_id"])
  ]
)
data class StepEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,

  // 記録時間（ミリ秒単位のUTCタイムスタンプ）
  @ColumnInfo(name = "timestamp")
  val timestamp: Long,

  // センサーから取得した累積歩数
  @ColumnInfo(name = "sensor_total_steps")
  val sensorTotalSteps: Int,

  // 計算された差分歩数（前回記録からの増加分）
  @ColumnInfo(name = "calculated_steps")
  val calculatedSteps: Int,

  // センサーリセット間のセッションID
  // デバイス再起動などでセンサーがリセットされた場合に変更される
  @ColumnInfo(name = "session_id")
  val sessionId: String
)
