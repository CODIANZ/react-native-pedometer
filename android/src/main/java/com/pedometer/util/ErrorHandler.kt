package com.pedometer.util

import android.util.Log
import com.facebook.react.bridge.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

object ErrorHandler {
  private const val TAG = "ErrorHandler"

  /**
   * 同期処理を実行し、例外をキャッチしてPromiseを解決または拒否する
   *
   * @param promise React Native Promise
   * @param action 実行する処理
   */
  fun handle(promise: Promise, action: () -> Any?) {
    try {
      when (val result = action()) {
        // PedometerResultの場合はそのままPromiseに渡す
        is PedometerResult<*> -> result.resolvePromise(promise)

        // null以外の値の場合はそのまま解決
        else -> promise.resolve(result)
      }
    } catch (e: PedometerError) {
      // PedometerErrorの場合はそのままPromiseを拒否
      Log.e(TAG, "処理中にPedometerErrorが発生しました", e)
      promise.reject(e.code, e.message, e)
    } catch (e: Exception) {
      // その他の例外の場合は適切なPedometerErrorにラップして拒否
      Log.e(TAG, "処理中に予期しない例外が発生しました", e)
      val wrappedError = PedometerError.fromThrowable(e)
      promise.reject(wrappedError.code, wrappedError.message, wrappedError)
    }
  }

  /**
   * 非同期処理（コルーチン）を実行し、例外をキャッチしてPromiseを解決または拒否する
   *
   * @param scope コルーチンスコープ
   * @param context コルーチンコンテキスト
   * @param promise React Native Promise
   * @param action 実行する非同期処理
   */
  fun handleCoroutine(
    scope: CoroutineScope,
    context: CoroutineContext,
    promise: Promise,
    action: suspend () -> Any?
  ) {
    scope.launch(context) {
      try {
        when (val result = action()) {
          // PedometerResultの場合はそのままPromiseに渡す
          is PedometerResult<*> -> result.resolvePromise(promise)

          // null以外の値の場合はそのまま解決
          else -> promise.resolve(result)
        }
      } catch (e: PedometerError) {
        // PedometerErrorの場合はそのままPromiseを拒否
        Log.e(TAG, "非同期処理中にPedometerErrorが発生しました", e)
        promise.reject(e.code, e.message, e)
      } catch (e: Exception) {
        // その他の例外の場合は適切なPedometerErrorにラップして拒否
        Log.e(TAG, "非同期処理中に予期しない例外が発生しました", e)
        val wrappedError = PedometerError.fromThrowable(e)
        promise.reject(wrappedError.code, wrappedError.message, wrappedError)
      }
    }
  }

  inline fun <T> runCatching(action: () -> T): PedometerResult<T> {
    return try {
      PedometerResult.success(action())
    } catch(e: Exception) {
      // エラー処理
      PedometerResult.failure(e)
    }
  }
}
