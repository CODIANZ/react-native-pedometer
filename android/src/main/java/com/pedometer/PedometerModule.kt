package com.pedometer

import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineName
import android.util.Log
import com.pedometer.database.StepEntity
import com.pedometer.permission.ReactPermissionAdapter
import com.pedometer.permission.manager.IPermissionManager
import com.pedometer.repository.IStepRepository
import com.pedometer.sensor.IStepSensorManager
import com.pedometer.util.ErrorHandler
import com.pedometer.util.PedometerError
import com.pedometer.util.PedometerResult

@ReactModule(name = PedometerModule.NAME)
class PedometerModule(
  reactContext: ReactApplicationContext,
  private val permissionManager: IPermissionManager,
  private val stepSensorManager: IStepSensorManager,
  private val stepRepository: IStepRepository
) : NativePedometerSpec(reactContext) {

  companion object {
    const val NAME = "Pedometer"
    private const val TAG = "PedometerModule"
  }

  // コルーチンスコープ
  private val moduleJob = SupervisorJob()
  private val moduleScope = CoroutineScope(
    moduleJob + Dispatchers.Main + CoroutineName("PedometerModule")
  )

  // 追跡状態
  private var isCurrentlyTracking = false

  override fun getName(): String {
    return NAME
  }

  /**
   * 歩数計センサーが利用可能かどうかを確認
   */
  override fun isAvailable(promise: Promise) {
    ErrorHandler.handle(promise) {
      val isAvailable = stepSensorManager.isSensorAvailable()
      PedometerResult.success(isAvailable)
    }
  }

  /**
   * 権限をリクエスト
   */
  override fun requestPermission(promise: Promise) {
    ErrorHandler.handle(promise) {
      val activity = reactApplicationContext.currentActivity
        ?: throw PedometerError.PermissionError("権限リクエストにはアクティビティが必要です")

      // 既に権限がある場合は即時成功
      if (permissionManager.checkPermission()) {
        PedometerResult.success(true)
      } else {
        // React用のアダプターを作成し、PermissionManagerに渡す
        val callback = ReactPermissionAdapter(promise)
        permissionManager.requestPermission(
          activity,
          callback,
          PedometerConstants.PERMISSION_REQUEST_TIMEOUT
        )

        // Promiseは非同期で解決されるため、nullを返す
        null
      }
    }
  }

  /**
   * 歩数追跡を開始
   */
  override fun startTracking(promise: Promise) {
    ErrorHandler.handle(promise) {
      // 権限チェック
      if (!permissionManager.checkPermission()) {
        return@handle PedometerResult.Failure(
          PedometerError.PermissionError("権限がありません")
        )
      }

      // 既に追跡中の場合は何もしない
      if (isCurrentlyTracking) {
        return@handle PedometerResult.success(true)
      }

      // センサー追跡を開始
      when (val result = stepSensorManager.startTracking()) {
        is PedometerResult.Success -> {
          val sessionId = result.value
          val eventData = Arguments.createMap().apply {
            putString("sessionId", sessionId)
          }
          sendEvent(PedometerConstants.EVENT_START_TRACKING, eventData)
          isCurrentlyTracking = true
          PedometerResult.success(true)
        }

        is PedometerResult.Failure -> {
          result
        }
      }
    }
  }

  /**
   * 歩数追跡を停止
   */
  override fun stopTracking(promise: Promise) {
    ErrorHandler.handle(promise) {
      // 追跡中でない場合は何もしない
      if (!isCurrentlyTracking) {
        return@handle PedometerResult.success(true)
      }

      // センサー追跡を停止
      when (val result = stepSensorManager.stopTracking()) {
        is PedometerResult.Success -> {
          isCurrentlyTracking = false
          sendEvent(PedometerConstants.EVENT_STOP_TRACKING, null)
          PedometerResult.success(true)
        }

        is PedometerResult.Failure -> result
      }
    }
  }

  /**
   * 指定期間の累積歩数を取得
   *
   * @param from 開始時間（UTCミリ秒）
   * @param to 終了時間（UTCミリ秒）
   */
  override fun queryCount(from: Double, to: Double, promise: Promise) {
    ErrorHandler.handleCoroutine(moduleScope, Dispatchers.IO, promise) {
      try {
        when (val result = stepRepository.getStepsBetween(from.toLong(), to.toLong())) {
          is PedometerResult.Success -> {
            val stepEntities = result.value
            // 各StepEntityのcalculatedStepsを合計して累積歩数を計算
            val totalSteps = stepEntities.sumOf { it.calculatedSteps }
            PedometerResult.success(totalSteps)
          }
          is PedometerResult.Failure -> result
        }
      } catch (e: Exception) {
        Log.e(TAG, "累積歩数の取得中にエラーが発生しました", e)
        PedometerResult.Failure(
          PedometerError.fromThrowable(e)
        )
      }
    }
  }

  /**
   * 指定期間の歩数データを取得
   *
   * @param from 開始時間（UTCミリ秒）
   * @param to 終了時間（UTCミリ秒）
   */
  override fun querySteps(from: Double, to: Double, promise: Promise) {
    ErrorHandler.handleCoroutine(moduleScope, Dispatchers.IO, promise) {
      try {
        val result = stepRepository
          .getStepsBetween(from.toLong(), to.toLong())

        when (result) {
          is PedometerResult.Success -> {
            val stepEntities = result.value
            val stepsArray = Arguments.createArray()

            // StepEntityをJSオブジェクトに変換
            for (entity in stepEntities) {
              val stepData = convertStepEntityToJSMap(entity)
              stepsArray.pushMap(stepData)
            }

            PedometerResult.success(stepsArray)
          }

          is PedometerResult.Failure -> result
        }
      } catch (e: Exception) {
        Log.e(TAG, "詳細な歩数データの取得中にエラーが発生しました", e)
        PedometerResult.Failure(
          PedometerError.fromThrowable(e)
        )
      }
    }
  }

  /**
   * StepEntityをJavaScriptのMapに変換する
   */
  private fun convertStepEntityToJSMap(entity: StepEntity): WritableMap {
    return Arguments.createMap().apply {
      putDouble("timestamp", entity.timestamp.toDouble())
      putInt("steps", entity.calculatedSteps)
      putInt("calculatedSteps", entity.calculatedSteps)
      putInt("sensorSteps", entity.sensorTotalSteps)
      putString("sessionId", entity.sessionId)
    }
  }

  /**
   * アクティビティのonRequestPermissionsResultから呼び出される
   */
  fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ): Boolean {
    // 権限マネージャーに結果を通知
    return permissionManager.handlePermissionResult(
      requestCode,
      permissions,
      grantResults
    )
  }

  /**
   * モジュールが無効化されたときに呼び出される
   */
  override fun invalidate() {
    super.invalidate()

    try {
      // 追跡中の場合は停止
      if (isCurrentlyTracking) {
        stepSensorManager.stopTracking()
        isCurrentlyTracking = false
      }

      // 保留中の権限リクエストをキャンセル
      permissionManager.cancelPendingRequests()
    } catch (e: Exception) {
      Log.e(TAG, "モジュール無効化中にエラーが発生しました", e)
    } finally {
      // コルーチンスコープをキャンセル
      moduleJob.cancel()
    }
  }

  /**
   * イベントをJSに送信する
   *
   * @param eventName イベント名
   * @param params パラメータ
   */
  private fun sendEvent(eventName: String, params: WritableMap?) {
    try {
      reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(eventName, params)
    } catch (e: Exception) {
      Log.e(TAG, "イベント送信中にエラーが発生しました: $eventName", e)
    }
  }
}
