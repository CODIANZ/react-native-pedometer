package com.pedometer

import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = PedometerModule.NAME)
class PedometerModule(reactContext: ReactApplicationContext) :
  NativePedometerSpec(reactContext) {

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "Pedometer"
  }

  /**
   * 歩数計センサーが利用可能かどうかを確認
   */
  override fun isAvailable(promise: Promise) {
  }

  /**
   * 権限をリクエスト
   */
  override fun requestPermission(promise: Promise) {
  }

  /**
   * 歩数追跡を開始
   */
  override fun startTracking(promise: Promise) {
  }

  /**
   * 歩数追跡を停止
   */
  override fun stopTracking(promise: Promise) {
  }

  /**
   * 指定期間の歩数を取得
   *
   * @param from 開始時間（ミリ秒）
   * @param to 終了時間（ミリ秒）
   */
  override fun queryCount(from: Double, to: Double, promise: Promise) {
  }
}
