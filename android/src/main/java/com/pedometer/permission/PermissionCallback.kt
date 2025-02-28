package com.pedometer.permission

interface PermissionCallback {
  /**
   * 権限リクエストが成功した場合に呼び出される
   */
  fun onPermissionGranted()

  /**
   * 権限リクエストが拒否された場合に呼び出される
   */
  fun onPermissionDenied()

  /**
   * 権限リクエスト中にエラーが発生した場合に呼び出される
   *
   * @param message エラーメッセージ
   * @param cause 原因となった例外（存在する場合）
   */
  fun onPermissionError(message: String, cause: Throwable? = null)
}
