package com.pedometer.util

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.facebook.react.bridge.Promise

sealed class PedometerResult<out T> {

  data class Success<T>(val value: T) : PedometerResult<T>()

  data class Failure(val error: PedometerError) : PedometerResult<Nothing>()

  companion object {
    /**
     * 成功ケースを作成
     */
    fun <T> success(value: T): PedometerResult<T> = Success(value)

    /**
     * 失敗ケースを作成
     */
    fun failure(error: PedometerError): PedometerResult<Nothing> = Failure(error)

    /**
     * Throwableから失敗ケースを作成
     */
    fun failure(throwable: Throwable): PedometerResult<Nothing> {
      val error = when (throwable) {
        is PedometerError -> throwable
        else -> PedometerError.UnexpectedError(
          "予期しないエラー: ${throwable.message}",
          throwable
        )
      }
      return Failure(error)
    }

    /**
     * エラーメッセージから失敗ケースを作成
     */
    fun failure(message: String, cause: Throwable? = null): PedometerResult<Nothing> {
      return Failure(PedometerError.UnexpectedError(message, cause))
    }

    /**
     * 関数を実行し、結果をPedometerResultでラップする
     */
    inline fun <T> runCatching(block: () -> T): PedometerResult<T> {
      return try {
        Success(block())
      } catch (e: Exception) {
        failure(e)
      }
    }
  }

  /**
   * 成功時に指定された関数を実行する
   */
  inline fun <R> map(transform: (T) -> R): PedometerResult<R> {
    return when (this) {
      is Success -> try {
        Success(transform(value))
      } catch (e: Exception) {
        Failure(
          PedometerError.UnexpectedError(
            "変換中にエラーが発生しました: ${e.message}",
            e
          )
        )
      }

      is Failure -> this
    }
  }

  /**
   * 成功時に指定された関数を実行し、結果のPedometerResultを返す
   */
  inline fun <R> flatMap(transform: (T) -> PedometerResult<R>): PedometerResult<R> {
    return when (this) {
      is Success -> try {
        transform(value)
      } catch (e: Exception) {
        Failure(
          PedometerError.UnexpectedError(
            "変換中にエラーが発生しました: ${e.message}",
            e
          )
        )
      }

      is Failure -> this
    }
  }

  /**
   * Arrowライブラリの Either<Throwable, T> に変換する
   */
  fun toEither(): Either<Throwable, T> {
    return when (this) {
      is Success -> value.right()
      is Failure -> error.left()
    }
  }

  /**
   * React Native の Promise に結果を渡す
   */
  fun resolvePromise(promise: Promise) {
    when (this) {
      is Success -> promise.resolve(value)
      is Failure -> promise.reject(error.code, error.message, error)
    }
  }
}

/**
 * Either<Throwable, T> を PedometerResult<T> に変換する拡張関数
 */
fun <T> Either<Throwable, T>.toPedometerResult(): PedometerResult<T> {
  return fold(
    { error -> PedometerResult.failure(error) },
    { value -> PedometerResult.success(value) }
  )
}
