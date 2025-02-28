package com.pedometer

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import android.util.Log
import com.pedometer.database.StepDatabase
import com.pedometer.permission.manager.IPermissionManager
import com.pedometer.permission.manager.PermissionManager
import com.pedometer.processor.IStepProcessor
import com.pedometer.processor.StepProcessor
import com.pedometer.repository.StepRepository
import com.pedometer.sensor.IStepSensorManager
import com.pedometer.sensor.StepSensorManager
import com.pedometer.state.PedometerStateManager
import com.pedometer.util.PedometerResult

class PedometerPackage : BaseReactPackage() {
  companion object {
    private const val TAG = "PedometerPackage"
  }

  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == PedometerModule.NAME) {
      createPedometerModule(reactContext)
    } else {
      null
    }
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      val moduleInfos: MutableMap<String, ReactModuleInfo> = HashMap()
      moduleInfos[PedometerModule.NAME] = ReactModuleInfo(
        PedometerModule.NAME,
        PedometerModule.NAME,
        false,  // canOverrideExistingModule
        false,  // needsEagerInit
        // hasConstants
        false,  // isCxxModule
        true    // isTurboModule
      )
      moduleInfos
    }
  }

  private fun createPedometerModule(reactContext: ReactApplicationContext): PedometerModule {
    try {
      // コルーチンスコープを作成
      val coroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineName("PedometerScope")
      )

      // 状態管理を作成
      val stateManager = PedometerStateManager(reactContext)

      // 権限管理を作成
      val permissionManager: IPermissionManager = PermissionManager(reactContext)

      // データアクセスレイヤーを初期化
      val stepDao = StepDatabase.getInstance(reactContext).stepDao()
      val stepRepository = StepRepository(stepDao)

      // 歩数処理ロジックを作成
      val stepProcessor: IStepProcessor = StepProcessor(stateManager)

      // センサーマネージャーを作成
      val stepSensorManager: IStepSensorManager = StepSensorManager(
        reactContext,
        coroutineScope,
        stepProcessor,
        stepRepository
      )

      // 非同期で初期化
      coroutineScope.launch {
        try {
          stepSensorManager.initialize().also { result ->
            if (result is PedometerResult.Failure) {
              Log.e(TAG, "StepSensorManagerの初期化に失敗しました: ${result.error.message}")
            }
          }
        } catch (e: Exception) {
          Log.e(TAG, "初期化中に例外が発生しました", e)
        }
      }

      // モジュールを作成して返す
      return PedometerModule(
        reactContext,
        permissionManager,
        stepSensorManager,
        stepRepository
      )
    } catch (e: Exception) {
      Log.e(TAG, "PedometerModuleの作成中に例外が発生しました", e)
      throw e
    }
  }
}
