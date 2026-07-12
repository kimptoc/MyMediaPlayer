package com.example.mymediaplayer.shared

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.awaitStringOrNull(onNotSuccessful: ((Response) -> Unit)? = null): String? =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            onNotSuccessful?.invoke(it)
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        val text = it.body?.string()
                        if (text.isNullOrBlank()) {
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        if (continuation.isActive) continuation.resume(text)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
