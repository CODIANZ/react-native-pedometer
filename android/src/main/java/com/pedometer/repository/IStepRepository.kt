package com.pedometer.repository

import com.pedometer.model.StepSummary
import com.pedometer.model.RawStepData
import com.pedometer.util.PedometerResult

interface IStepRepository {
  /**
   * 歩数データを記録
   *
   * @param rawStepData 生の歩数データ
   * @return 記録結果
   */
  suspend fun recordStep(rawStepData: RawStepData): PedometerResult<Unit>

  /**
   * 指定期間の歩数を取得
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   * @return 歩数サマリー
   */
  suspend fun getStepsBetween(from: Long, to: Long): PedometerResult<StepSummary>
}
