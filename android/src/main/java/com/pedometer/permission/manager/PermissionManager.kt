package com.pedometer.permission.manager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedometer.PedometerConstants
import com.pedometer.permission.PermissionCallback

class PermissionManager(private val context: Context) : IPermissionManager {

  companion object {
    private const val TAG = "PermissionManager"
  }

  // 権限リクエストハンドラ
  private val requestHandler = PermissionRequestHandler()

  /**
   * 必要な権限が付与されているかどうかを確認する
   */
  override fun checkPermission(): Boolean {
    val permission = getRequiredPermission()
    val permissionStatus = ContextCompat.checkSelfPermission(context, permission)
    return permissionStatus == PackageManager.PERMISSION_GRANTED
  }

  /**
   * 必要な権限をリクエストする
   */
  override fun requestPermission(
    activity: Activity,
    callback: PermissionCallback,
    timeoutMs: Long
  ) {
    try {
      // 既に権限がある場合は即時成功
      if (checkPermission()) {
        callback.onPermissionGranted()
        return
      }

      // タイムアウト時間の設定（デフォルトは30秒）
      val actualTimeoutMs =
        if (timeoutMs <= 0) PedometerConstants.PERMISSION_REQUEST_TIMEOUT else timeoutMs

      // リクエストハンドラにリクエストを登録
      requestHandler.addRequest(
        PedometerConstants.PERMISSION_REQUEST_CODE,
        callback,
        actualTimeoutMs
      )

      // 権限リクエストを実行
      val permission = getRequiredPermission()
      ActivityCompat.requestPermissions(
        activity,
        arrayOf(permission),
        PedometerConstants.PERMISSION_REQUEST_CODE
      )

      Log.d(TAG, "権限リクエストを開始: $permission")
    } catch (e: Exception) {
      // リクエスト中にエラーが発生した場合はコールバックを呼び出し
      Log.e(TAG, "権限リクエスト中にエラーが発生しました", e)
      callback.onPermissionError("権限リクエスト中にエラーが発生しました: ${e.message}", e)
    }
  }

  /**
   * プラットフォームバージョンに応じた必要な権限を取得する
   */
  private fun getRequiredPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Android 10以降はACTIVITY_RECOGNITIONが標準APIに含まれる
      Manifest.permission.ACTIVITY_RECOGNITION
    } else {
      // Android 9以前はGoogleプレイサービスの権限を使用
      "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
    }
  }

  /**
   * アクティビティのonRequestPermissionsResultから呼び出される
   */
  override fun handlePermissionResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ): Boolean {
    // リクエストコードが一致しない場合は処理しない
    if (requestCode != PedometerConstants.PERMISSION_REQUEST_CODE) {
      return false
    }

    // 権限が付与されたかどうかを確認
    val isGranted = grantResults.isNotEmpty() &&
      grantResults[0] == PackageManager.PERMISSION_GRANTED

    // リクエストハンドラに結果を通知
    val handled = requestHandler.handleResult(requestCode, isGranted)

    Log.d(
      TAG,
      "権限リクエスト結果: リクエストコード=$requestCode, 付与=$isGranted, 処理済み=$handled"
    )
    return handled
  }

  /**
   * 保留中のリクエストをキャンセルする
   * アプリのライフサイクル終了時などに呼び出す
   */
  override fun cancelPendingRequests() {
    requestHandler.cancelAllRequests()
  }
}
