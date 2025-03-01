package com.pedometer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface StepDao {
  /**
   * 歩数データを挿入する
   */
  @Insert
  suspend fun insert(step: StepEntity)

  /**
   * 指定期間内の歩数データを取得する
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   * @return 全歩数データのリスト
   */
  @Query(
    """
        SELECT * FROM steps
        WHERE timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
    """
  )
  suspend fun getEntitiesBetween(from: Long, to: Long): List<StepEntity>

  /**
   * 最新の歩数データを取得する
   */
  @Query(
    """
        SELECT * FROM steps
        ORDER BY timestamp DESC
        LIMIT 1
    """
  )
  suspend fun getLatestStep(): StepEntity?

  /**
   * 指定したセッションの最新データを取得する
   *
   * @param sessionId セッションID
   */
  @Query(
    """
        SELECT * FROM steps
        WHERE session_id = :sessionId
        ORDER BY timestamp DESC
        LIMIT 1
    """
  )
  suspend fun getLatestStepBySessionId(sessionId: String): StepEntity?

  /**
   * 指定した時間より前の最新の歩数データを取得
   *
   * @param timestamp タイムスタンプ
   */
  @Query(
    """
    SELECT * FROM steps
    WHERE timestamp <= :timestamp
    ORDER BY timestamp DESC
    LIMIT 1
  """
  )
  suspend fun getLatestStepBefore(timestamp: Long): StepEntity?

  /**
   * 指定した日時より前のデータを削除する
   */
  @Query(
    """
        DELETE FROM steps
        WHERE timestamp < :timestamp
    """
  )
  suspend fun deleteStepsBefore(timestamp: Long)

  /**
   * セッションごとの歩数サマリーを取得する
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   */
  @Query(
    """
        SELECT session_id,
               MIN(timestamp) as start_time,
               MAX(timestamp) as end_time,
               SUM(calculated_steps) as total_steps
        FROM steps
        WHERE timestamp BETWEEN :from AND :to
        GROUP BY session_id
        ORDER BY start_time
    """
  )
  suspend fun getStepSummaryBySessionId(from: Long, to: Long): List<SessionSummary>

  /**
   * 各セッションの最初と最後のセンサー値を取得する
   * これによりセンサーリセットを検出できる
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   */
  @Query(
    """
        SELECT s.session_id,
               first_record.sensor_total_steps AS first_sensor_value,
               last_record.sensor_total_steps AS last_sensor_value
        FROM (SELECT DISTINCT session_id FROM steps WHERE timestamp BETWEEN :from AND :to) s
        JOIN (
            SELECT session_id, MIN(timestamp) AS min_time
            FROM steps
            WHERE timestamp BETWEEN :from AND :to
            GROUP BY session_id
        ) first_times ON s.session_id = first_times.session_id
        JOIN (
            SELECT session_id, MAX(timestamp) AS max_time
            FROM steps
            WHERE timestamp BETWEEN :from AND :to
            GROUP BY session_id
        ) last_times ON s.session_id = last_times.session_id
        JOIN steps first_record ON first_times.session_id = first_record.session_id AND first_times.min_time = first_record.timestamp
        JOIN steps last_record ON last_times.session_id = last_record.session_id AND last_times.max_time = last_record.timestamp
    """
  )
  suspend fun getSessionSensorValues(from: Long, to: Long): List<SessionSensorValues>

  /**
   * 古いデータのクリーンアップ
   */
  @Transaction
  suspend fun cleanupOldData(keepDuration: Long) {
    val cutoffTime = System.currentTimeMillis() - keepDuration
    deleteStepsBefore(cutoffTime)
  }
}

/**
 * セッションごとの歩数サマリー
 */
data class SessionSummary(
  val session_id: String,
  val start_time: Long,
  val end_time: Long,
  val total_steps: Int
)

/**
 * セッションごとのセンサー値
 * セッション内でのセンサー値の変化を追跡するために使用
 */
data class SessionSensorValues(
  val session_id: String,
  val first_sensor_value: Int,
  val last_sensor_value: Int
)
