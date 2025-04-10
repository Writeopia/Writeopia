package io.writeopia.common.utils

sealed interface ResultData<T> {
    class Idle<T> : ResultData<T>

    class Loading<T> : ResultData<T>

    data class Complete<T>(val data: T) : ResultData<T>

    data class InProgress<T>(val data: T) : ResultData<T>

    data class Error<T>(val exception: Exception? = null) : ResultData<T>
}

fun <T, R> ResultData<T>.map(fn: (T) -> R): ResultData<R> =
    when (this) {
        is ResultData.Complete -> ResultData.Complete(fn(this.data))
        is ResultData.Error -> ResultData.Error(this.exception)
        is ResultData.Idle -> ResultData.Idle()
        is ResultData.Loading -> ResultData.Loading()
        is ResultData.InProgress -> ResultData.InProgress(fn(this.data))
    }

fun ResultData<Boolean>.toBoolean(): Boolean =
    this is ResultData.Complete && data
