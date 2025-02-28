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
   * 指定期間の歩数を取得
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   * @return 歩数サマリー
   */
  override suspend fun getStepsBetween(from: Long, to: Long): PedometerResult<StepSummary> {
    return ErrorHandler.runCatching {
      if (to < from) {
        throw PedometerError.InvalidParameterError("終了時間は開始時間より後である必要があります")
      }

      try {
        // 歩数を取得（NULLの場合は0）
        val stepCount = stepDao.getStepsBetween(from, to) ?: 0

        val summary = StepSummary(
          startTime = from,
          endTime = to,
          stepCount = stepCount
        )

        summary // 直接StepSummary型を返す
      } catch (e: Exception) {
        Log.e(TAG, "歩数の取得に失敗しました", e)
        throw PedometerError.DatabaseError("歩数の取得に失敗しました: ${e.message}", e)
      }
    }
  }

  /**
   * デバッグ用に指定期間内の全歩数データを取得
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   * @return 全歩数データのリスト
   */
  suspend fun getAllStepsForDebug(from: Long, to: Long): PedometerResult<List<StepEntity>> {
    return ErrorHandler.runCatching {
      if (to < from) {
        throw PedometerError.InvalidParameterError("終了時間は開始時間より後である必要があります")
      }

      try {
        val allSteps = stepDao.getAllStepsBetween(from, to)
        Log.d(TAG, "デバッグ用歩数データを取得: ${allSteps.size}件")
        allSteps
      } catch (e: Exception) {
        Log.e(TAG, "デバッグ用歩数データの取得に失敗しました", e)
        throw PedometerError.DatabaseError("デバッグ用歩数データの取得に失敗しました: ${e.message}", e)
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
