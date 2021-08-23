package com.ArjixWasTaken.cloudstream3.mvvm

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.bumptech.glide.load.HttpException
import com.ArjixWasTaken.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this, Observer { it?.let { t -> action(t) } })
}

fun <T> LifecycleOwner.observeDirectly(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this, Observer { it?.let { t -> action(t) } })
    val currentValue = liveData.value
    if (currentValue != null)
        action(currentValue)
}

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorCode: Int?,
        val errorResponse: Any?, //ResponseBody
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()
}

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
}

fun <T> normalSafeApiCall(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            logError(throwable)
            when (throwable) {
                is SocketTimeoutException -> {
                    Resource.Failure(true, null, null, "Please try again later.")
                }
                is HttpException -> {
                    Resource.Failure(false, throwable.statusCode, null, throwable.localizedMessage)
                }
                is UnknownHostException -> {
                    Resource.Failure(true, null, null, "Cannot connect to server, try again later.")
                }
                is ErrorLoadingException -> {
                    Resource.Failure(true, null, null, throwable.message ?: "Error loading, try again later.")
                }
                is NotImplementedError -> {
                    Resource.Failure(false, null, null, "This operation is not implemented.")
                }
                else -> {
                    val stackTraceMsg = throwable.localizedMessage + "\n\n" + throwable.stackTrace.joinToString(
                        separator = "\n"
                    ) {
                        "${it.fileName} ${it.lineNumber}"
                    }
                    Resource.Failure(false, null, null, stackTraceMsg) //
                }
            }
        }
    }
}