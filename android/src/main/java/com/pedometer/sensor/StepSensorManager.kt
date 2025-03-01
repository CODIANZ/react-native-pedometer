package com.pedometer.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.pedometer.model.RawStepData
import com.pedometer.processor.IStepProcessor
import com.pedometer.repository.IStepRepository
import com.pedometer.util.ErrorHandler
import com.pedometer.util.PedometerError
import com.pedometer.util.PedometerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.util.concurrent.atomic.AtomicBoolean

class StepSensorManager(
  private val context: Context,
  private val coroutineScope: CoroutineScope,
  private val stepProcessor: IStepProcessor,
  private val stepRepository: IStepRepository
) : IStepSensorManager {

  companion object {
    private const val TAG = "StepSensorManager"
  }

  // センサー関連
  private var sensorManager: SensorManager? = null
  private var stepSensor: Sensor? = null

  // トラッキング状態
  private val isTracking = AtomicBoolean(false)

  // 現在のセッションID
  private var currentSessionId = ""

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  private val sensorProcessingDispatcher = newSingleThreadContext("SensorProcessing")


  init {
    try {
      sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
      sensorManager?.let { manager ->
        stepSensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
          Log.w(TAG, "歩数センサーがこのデバイスでは利用できません")
        }
      } ?: Log.e(TAG, "センサーマネージャーの取得に失敗しました")
    } catch (e: Exception) {
      Log.e(TAG, "センサーの初期化中にエラーが発生しました", e)
      sensorManager = null
      stepSensor = null
    }
  }

  override fun isSensorAvailable(): Boolean {
    return stepSensor != null
  }

  override suspend fun initialize(): PedometerResult<Unit> {
    return ErrorHandler.runCatching {
      ensureNewSession()
      // StepProcessorを初期化
      val result = stepProcessor.initialize()
      if (result is PedometerResult.Failure) {
        throw result.error // 直接エラーをスローする
      }

      // 初期セッションIDを設定
      currentSessionId = (result as PedometerResult.Success).value

      // StepRepositoryにセッションIDを設定
      try {
        if (stepRepository is com.pedometer.repository.StepRepository) {
          (stepRepository as com.pedometer.repository.StepRepository).setSessionId(currentSessionId)
          Log.d(TAG, "リポジトリにセッションIDを設定: $currentSessionId")
        }
      } catch (e: Exception) {
        Log.e(TAG, "セッションID設定中にエラーが発生しました: ${e.message}", e)
      }

      Log.d(TAG, "StepSensorManager初期化完了: sessionId=$currentSessionId")
      Unit // 直接Unit型を返す
    }
  }


  override fun startTracking(): PedometerResult<String> {
    return ErrorHandler.runCatching {
      // 前提条件チェック
      if (sensorManager == null) {
        throw PedometerError.SensorError("センサーマネージャーが初期化されていません")
      }

      if (stepSensor == null) {
        throw PedometerError.SensorError("このデバイスでは歩数センサーが利用できません")
      }

      // すでに追跡中の場合は何もしない
      if (isTracking.getAndSet(true)) {
        return@runCatching currentSessionId
      }

      // センサーリスナーを登録
      val registered = sensorManager?.registerListener(
        sensorEventListener,
        stepSensor,
        SensorManager.SENSOR_DELAY_NORMAL
      ) ?: false

      // 登録できなかった場合はエラー
      if (!registered) {
        isTracking.set(false)
        throw PedometerError.SensorError("センサーリスナーの登録に失敗しました")
      }

      // キューに溜まったイベントをフラッシュ
      sensorManager?.flush(sensorEventListener)

      Log.d(TAG, "歩数追跡を開始しました: sessionId=$currentSessionId")
      currentSessionId // 直接String型を返す
    }
  }

  override fun stopTracking(): PedometerResult<String> {
    return ErrorHandler.runCatching {
      if (sensorManager == null) {
        throw PedometerError.SensorError("センサーマネージャーが初期化されていません")
      }

      // 追跡中でない場合は何もしない
      if (!isTracking.getAndSet(false)) {
        return@runCatching "TRACKING_STOPPED"
      }

      // センサーリスナーを登録解除
      sensorManager?.unregisterListener(sensorEventListener)

      // 最終処理
      // 注意: このコードはコルーチンを起動して非同期で実行するよう修正
      coroutineScope.launch(Dispatchers.IO) {
        val finalizeResult = stepProcessor.finalize()
        if (finalizeResult is PedometerResult.Failure) {
          Log.w(TAG, "最終処理中にエラーが発生しました: ${finalizeResult.error.message}")
        }
      }

      Log.d(TAG, "歩数追跡を停止しました")
      "TRACKING_STOPPED"
    }
  }

  private val sensorEventListener = object : SensorEventListener {
    private var isFirstEvent = true

    override fun onSensorChanged(event: SensorEvent) {
      if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) {
        return
      }

      if (isFirstEvent) {
        // 初回のイベントではキューのフラッシュを行い古いデータをクリア
        sensorManager?.flush(this)
        isFirstEvent = false
      }

      val currentSensorSteps = event.values[0].toInt()
      val currentTime = System.currentTimeMillis()

      // 非同期で歩数処理を実行
      processSensorReading(currentSensorSteps, currentTime)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
      Log.d(TAG, "センサー精度が変更されました: ${sensor?.name}, 精度=$accuracy")
    }
  }

  private fun processSensorReading(sensorSteps: Int, timestamp: Long) {
    // 追跡中でない場合は処理しない
    if (!isTracking.get()) {
      return
    }

    // 単一スレッドで処理することで並行実行を防ぐ
    coroutineScope.launch(sensorProcessingDispatcher) {
      try {
        Log.d(
          TAG,
          "センサー処理開始 [${Thread.currentThread().id}]: センサー値=$sensorSteps, 時間=$timestamp"
        )

        // StepProcessorでセンサーデータを処理
        val result = stepProcessor.processSensorReading(sensorSteps, timestamp)

        if (result is PedometerResult.Failure) {
          Log.e(TAG, "歩数処理中にエラーが発生しました: ${result.error.message}")
          return@launch
        }

        val processResult = (result as PedometerResult.Success).value

        // セッションIDの更新
        if (processResult.sessionId != currentSessionId) {
          val oldSessionId = currentSessionId
          currentSessionId = processResult.sessionId
          Log.d(TAG, "セッションIDを更新: $oldSessionId -> $currentSessionId")

          // StepRepositoryにも新しいセッションIDを設定
          try {
            if (stepRepository is com.pedometer.repository.StepRepository) {
              (stepRepository as com.pedometer.repository.StepRepository).setSessionId(
                currentSessionId
              )
            }
          } catch (e: Exception) {
            Log.e(TAG, "セッションID更新中にエラーが発生しました: ${e.message}", e)
          }
        }

        // 歩数データを記録（差分が0より大きい場合、初回の場合、またはリセット検出時）
        if (processResult.calculatedSteps > 0 || processResult.isFirstReading || processResult.isSensorReset) {
          recordStepData(
            processResult.calculatedSteps,
            sensorSteps,
            timestamp,
            processResult.sessionId
          )

          // リセット検出時のログ記録
          if (processResult.isSensorReset) {
            Log.d(
              TAG,
              "センサーリセット後の最初のイベント: センサー値=$sensorSteps, 計算歩数=${processResult.calculatedSteps}"
            )
          }
        }

        Log.d(
          TAG,
          "センサー処理完了 [${Thread.currentThread().id}]: センサー値=$sensorSteps, 計算歩数=${processResult.calculatedSteps}"
        )
      } catch (e: Exception) {
        Log.e(TAG, "センサーデータ処理中に予期しないエラーが発生しました: ${e.message}", e)
      }
    }
  }

  private suspend fun ensureNewSession() {
    try {
      // 新しいセッションの開始（常に新しいセッションで開始）
      val result = stepProcessor.startNewSession()
      if (result is PedometerResult.Success) {
        currentSessionId = result.value
        Log.d(TAG, "新しいセッションを開始: $currentSessionId")

        // StepRepositoryにも設定
        try {
          if (stepRepository is com.pedometer.repository.StepRepository) {
            (stepRepository as com.pedometer.repository.StepRepository).setSessionId(
              currentSessionId
            )
          }
        } catch (e: Exception) {
          Log.e(TAG, "セッションID設定中にエラー: ${e.message}", e)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "新規セッション作成に失敗: ${e.message}", e)
    }
  }

  /**
   * 歩数データをリポジトリに記録する
   *
   * @param calculatedSteps 計算された歩数差分
   * @param sensorTotalSteps センサーから取得した累積歩数
   * @param timestamp タイムスタンプ
   * @param sessionId セッションID
   */
  private suspend fun recordStepData(
    calculatedSteps: Int,
    sensorTotalSteps: Int,
    timestamp: Long,
    sessionId: String
  ) {
    try {
      val rawStepData = RawStepData(
        timestamp = timestamp,
        sensorTotalSteps = sensorTotalSteps,
        calculatedSteps = calculatedSteps,
        sessionId = sessionId
      )

      stepRepository.recordStep(rawStepData)

      if (calculatedSteps > 0) {
        Log.d(
          TAG,
          "歩数を記録しました: ${calculatedSteps}歩, センサー値=${sensorTotalSteps}, セッション=$sessionId"
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "歩数の記録に失敗しました", e)
    }
  }
}
