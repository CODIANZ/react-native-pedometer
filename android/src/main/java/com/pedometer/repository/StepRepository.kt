package com.pedometer.repository

import android.util.Log
import com.pedometer.database.StepDao
import com.pedometer.database.StepEntity
import com.pedometer.model.StepSummary
import com.pedometer.model.RawStepData
import com.pedometer.util.ErrorHandler
import com.pedometer.util.PedometerError
import com.pedometer.util.PedometerResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StepRepository(private val stepDao: StepDao) : IStepRepository {
  companion object {
    private const val TAG = "StepRepository"

    // データ保持期間（30日間）
    private const val DEFAULT_KEEP_DURATION = 30L * 24 * 60 * 60 * 1000

    // クリーンアップの実行確率（1%）
    private const val CLEANUP_PROBABILITY = 0.01
  }

  // 現在のセッションID
  private var sessionId = ""

  // 同時アクセスの排他制御
  private val mutex = Mutex()

  /**
   * セッションIDを設定
   *
   * @param newSessionId 新しいセッションID
   */
  suspend fun setSessionId(newSessionId: String) {
    mutex.withLock {
      sessionId = newSessionId
      Log.d(TAG, "セッションIDを設定: $sessionId")
    }
  }

  /**
   * 現在のセッションIDを取得
   *
   * @return 現在のセッションID
   */
  suspend fun getSessionId(): String = mutex.withLock { sessionId }

  /**
   * 歩数データを記録
   *
   * @param rawStepData 生の歩数データ
   * @return 記録結果
   */
  override suspend fun recordStep(rawStepData: RawStepData): PedometerResult<Unit> {
    return ErrorHandler.runCatching {
      mutex.withLock {
        // セッションIDの取得（データにセッションIDがない場合は現在のものを使用）
        val effectiveSessionId = rawStepData.sessionId.ifEmpty {
          sessionId.ifEmpty {
            Log.w(TAG, "空のセッションIDで歩数を記録しようとしています")
            "unknown-${System.currentTimeMillis()}"
          }
        }

        // エンティティを作成してDBに保存
        val entity = StepEntity(
          timestamp = rawStepData.timestamp,
          sensorTotalSteps = rawStepData.sensorTotalSteps,
          calculatedSteps = rawStepData.calculatedSteps,
          sessionId = effectiveSessionId
        )

        try {
          stepDao.insert(entity)

          // 低確率でデータクリーンアップを実行
          if (shouldCleanup()) {
            Log.d(TAG, "古いデータのクリーンアップを実行")
            stepDao.cleanupOldData(DEFAULT_KEEP_DURATION)
          }

          Unit // 直接Unit型を返す
        } catch (e: Exception) {
          Log.e(TAG, "歩数データの記録に失敗しました", e)
          throw PedometerError.DatabaseError("歩数データの記録に失敗しました: ${e.message}", e)
        }
      }
    }
  }

  /**
   * 指定期間の歩数データを取得
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   * @return 歩数データのリスト
   */
  override suspend fun getStepsBetween(from: Long, to: Long): PedometerResult<List<StepEntity>> {
    return ErrorHandler.runCatching {
      if (to < from) {
        throw PedometerError.InvalidParameterError("終了時間は開始時間より後である必要があります")
      }

      try {
        val entities = stepDao.getEntitiesBetween(from, to)
        Log.d(TAG, "デバッグ用歩数データを取得: ${entities.size}件")
        entities
      } catch (e: Exception) {
        Log.e(TAG, "デバッグ用歩数データの取得に失敗しました", e)
        throw PedometerError.DatabaseError(
          "デバッグ用歩数データの取得に失敗しました: ${e.message}",
          e
        )
      }
    }
  }

  /**
   * 指定期間の詳細な歩数サマリーを取得（セッションごと）
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   * @return セッションごとの歩数サマリーのリスト
   */
  suspend fun getDetailedStepSummary(
    from: Long,
    to: Long
  ): PedometerResult<List<SessionStepSummary>> {
    return ErrorHandler.runCatching {
      if (to < from) {
        throw PedometerError.InvalidParameterError("終了時間は開始時間より後である必要があります")
      }

      try {
        val sessionSummaries = stepDao.getStepSummaryBySessionId(from, to)

        val result = sessionSummaries.map { session ->
          SessionStepSummary(
            sessionId = session.session_id,
            startTime = session.start_time,
            endTime = session.end_time,
            stepCount = session.total_steps
          )
        }

        result // 直接List<SessionStepSummary>型を返す
      } catch (e: Exception) {
        Log.e(TAG, "詳細な歩数サマリーの取得に失敗しました", e)
        throw PedometerError.DatabaseError(
          "詳細な歩数サマリーの取得に失敗しました: ${e.message}",
          e
        )
      }
    }
  }

  /**
   * 最新の歩数データを取得
   *
   * @return 最新の歩数データ、存在しない場合はnull
   */
  suspend fun getLatestStep(): StepEntity? {
    return try {
      stepDao.getLatestStep()
    } catch (e: Exception) {
      Log.e(TAG, "最新の歩数データの取得に失敗しました", e)
      throw PedometerError.DatabaseError("最新の歩数データの取得に失敗しました: ${e.message}", e)
    }
  }

  /**
   * 指定されたセッションの最新の歩数データを取得
   *
   * @param sessionId セッションID
   * @return 最新の歩数データ、存在しない場合はnull
   */
  suspend fun getLatestStepBySessionId(sessionId: String): StepEntity? {
    return try {
      stepDao.getLatestStepBySessionId(sessionId)
    } catch (e: Exception) {
      Log.e(TAG, "セッションの最新歩数データの取得に失敗しました: sessionId=$sessionId", e)
      throw PedometerError.DatabaseError(
        "セッションの最新歩数データの取得に失敗しました: ${e.message}",
        e
      )
    }
  }

  /**
   * 指定したセッションの最新ステップを取得
   *
   * @param sessionId セッションID
   * @return 最新ステップ、存在しない場合はnull
   */
  suspend fun getLatestStepBySession(sessionId: String): Int? {
    return try {
      val entity = stepDao.getLatestStepBySessionId(sessionId)
      entity?.calculatedSteps
    } catch (e: Exception) {
      Log.e(TAG, "セッションの最新ステップ取得中にエラー: $sessionId", e)
      null
    }
  }

  /**
   * 指定した時間より前の最新ステップを取得
   *
   * @param timestamp タイムスタンプ
   * @return 指定時間前の最新ステップ、存在しない場合は0
   */
  suspend fun getLatestStepBefore(timestamp: Long): Int {
    return try {
      val entity = stepDao.getLatestStepBefore(timestamp)
      entity?.calculatedSteps ?: 0
    } catch (e: Exception) {
      Log.e(TAG, "過去の歩数記録取得中にエラー: ${e.message}", e)
      0
    }
  }

  /**
   * クリーンアップを実行すべきかを判断
   * ランダムな確率で実行（毎回ではなく稀に実行）
   */
  private fun shouldCleanup(): Boolean {
    return Math.random() < CLEANUP_PROBABILITY
  }
}

/**
 * セッションごとの歩数サマリー
 */
data class SessionStepSummary(
  val sessionId: String,
  val startTime: Long,
  val endTime: Long,
  val stepCount: Int
)
