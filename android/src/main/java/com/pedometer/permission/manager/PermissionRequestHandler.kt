package com.pedometer.permission.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pedometer.permission.PermissionCallback
import java.util.concurrent.ConcurrentHashMap

internal class PermissionRequestHandler {
  companion object {
    private const val TAG = "PermissionRequestHandler"
  }

  // 保留中のリクエストマップ (リクエストコード -> リクエスト情報)
  private val pendingRequests = ConcurrentHashMap<Int, RequestInfo>()

  // メインスレッドへのハンドラ
  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * 新しいリクエストを追加する
   *
   * @param requestCode リクエストコード
   * @param callback コールバック
   * @param timeoutMs タイムアウト時間（ミリ秒）、0以下の場合はタイムアウトなし
   */
  fun addRequest(requestCode: Int, callback: PermissionCallback, timeoutMs: Long) {
    val existingRequest = pendingRequests[requestCode]
    if (existingRequest != null) {
      // 既存のリクエストが存在する場合は、タイムアウト処理をキャンセル
      existingRequest.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }

      // エラーコールバックを呼び出し
      existingRequest.callback.onPermissionError(
        "新しい権限リクエストが既存のリクエストを上書きしました"
      )
    }

    // タイムアウト処理
    var timeoutRunnable: Runnable? = null
    if (timeoutMs > 0) {
      timeoutRunnable = Runnable {
        val request = pendingRequests.remove(requestCode)
        request?.callback?.onPermissionError("権限リクエストがタイムアウトしました")
        Log.w(TAG, "リクエストコード $requestCode の権限リクエストがタイムアウトしました")
      }
      mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    // リクエスト情報を保存
    pendingRequests[requestCode] = RequestInfo(
      callback = callback,
      timestamp = System.currentTimeMillis(),
      timeoutRunnable = timeoutRunnable
    )

    Log.d(
      TAG,
      "新しい権限リクエストを追加: リクエストコード=$requestCode, タイムアウト=${timeoutMs}ms"
    )
  }

  /**
   * リクエスト結果を処理する
   *
   * @param requestCode リクエストコード
   * @param granted 権限が付与された場合はtrue
   * @return リクエストが見つかった場合はtrue
   */
  fun handleResult(requestCode: Int, granted: Boolean): Boolean {
    val request = pendingRequests.remove(requestCode) ?: return false

    // タイムアウト処理をキャンセル
    request.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }

    // コールバックを呼び出し
    if (granted) {
      request.callback.onPermissionGranted()
    } else {
      request.callback.onPermissionDenied()
    }

    Log.d(TAG, "権限リクエスト結果を処理: リクエストコード=$requestCode, 付与=$granted")
    return true
  }

  /**
   * すべての保留中のリクエストをキャンセルする
   */
  fun cancelAllRequests() {
    // すべてのタイムアウト処理をキャンセル
    pendingRequests.values.forEach { request ->
      request.timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
      request.callback.onPermissionError("権限リクエストがキャンセルされました")
    }

    val count = pendingRequests.size
    pendingRequests.clear()

    if (count > 0) {
      Log.d(TAG, "$count 件の保留中権限リクエストをキャンセルしました")
    }
  }

  /**
   * リクエスト情報
   */
  private data class RequestInfo(
    val callback: PermissionCallback,
    val timestamp: Long,
    val timeoutRunnable: Runnable?
  )
}
