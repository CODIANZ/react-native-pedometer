package com.pedometer.permission

import com.facebook.react.bridge.Promise
import com.pedometer.util.PedometerError

class ReactPermissionAdapter(private val promise: Promise) : PermissionCallback {

  /**
   * 権限リクエストが成功した場合に呼び出される
   * Promiseを解決する
   */
  override fun onPermissionGranted() {
    promise.resolve(true)
  }

  /**
   * 権限リクエストが拒否された場合に呼び出される
   * Promiseを拒否する
   */
  override fun onPermissionDenied() {
    val error = PedometerError.PermissionError("ユーザーが権限を拒否しました")
    promise.reject(error.code, error.message, error)
  }

  /**
   * 権限リクエスト中にエラーが発生した場合に呼び出される
   * Promiseを拒否する
   *
   * @param message エラーメッセージ
   * @param cause 原因となった例外（存在する場合）
   */
  override fun onPermissionError(message: String, cause: Throwable?) {
    val error = PedometerError.PermissionError(message, cause)
    promise.reject(error.code, error.message, error)
  }
}
