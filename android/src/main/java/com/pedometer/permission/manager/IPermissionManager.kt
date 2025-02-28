package com.pedometer.permission.manager

import android.app.Activity
import com.pedometer.permission.PermissionCallback

interface IPermissionManager {
  /**
   * 必要な権限が付与されているかどうかを確認する
   */
  fun checkPermission(): Boolean

  /**
   * 必要な権限をリクエストする
   */
  fun requestPermission(activity: Activity, callback: PermissionCallback, timeoutMs: Long = 0)

  /**
   * アクティビティのonRequestPermissionsResultから呼び出される
   */
  fun handlePermissionResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ): Boolean

  /**
   * 保留中のリクエストをキャンセルする
   * アプリのライフサイクル終了時などに呼び出す
   */
  fun cancelPendingRequests()
}
