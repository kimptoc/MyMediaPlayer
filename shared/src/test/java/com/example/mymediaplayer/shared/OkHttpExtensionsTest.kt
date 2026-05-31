package com.example.mymediaplayer.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpExtensionsTest {

    private class MockCall(
        private val response: Response? = null,
        private val exception: IOException? = null
    ) : Call {
        private var _isCanceled = false
        var enqueueCalled = false

        override fun cancel() {
            _isCanceled = true
        }

        override fun enqueue(responseCallback: Callback) {
            enqueueCalled = true
            if (exception != null) {
                responseCallback.onFailure(this, exception)
            } else if (response != null) {
                responseCallback.onResponse(this, response)
            }
        }

        override fun clone(): Call = this
        override fun execute(): Response = response ?: throw exception ?: IOException("Mock execute failed")
        override fun isCanceled(): Boolean = _isCanceled
        override fun isExecuted(): Boolean = enqueueCalled
        override fun request(): Request = Request.Builder().url("http://localhost").build()
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
    }

    private val request = Request.Builder().url("http://localhost").build()

    @Test
    fun awaitStringOrNull_successfulResponse() = runTest {
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Success Body".toResponseBody("text/plain".toMediaTypeOrNull()))
            .build()
        val call = MockCall(response = response)
        val result = call.awaitStringOrNull()
        assertEquals("Success Body", result)
    }

    @Test
    fun awaitStringOrNull_unsuccessfulResponse_returnsNull() = runTest {
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("Error Body".toResponseBody("text/plain".toMediaTypeOrNull()))
            .build()
        var callbackCalled = false
        val call = MockCall(response = response)
        val result = call.awaitStringOrNull {
            callbackCalled = true
            assertEquals(404, it.code)
        }
        assertNull(result)
        assertTrue(callbackCalled)
    }

    @Test
    fun awaitStringOrNull_emptyBody_returnsNull() = runTest {
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
            .build()
        val call = MockCall(response = response)
        val result = call.awaitStringOrNull()
        assertNull(result)
    }

    @Test
    fun awaitStringOrNull_failure_throwsException() = runTest {
        val exception = IOException("Network Error")
        val call = MockCall(exception = exception)
        var thrown: Throwable? = null
        try {
            call.awaitStringOrNull()
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is IOException)
        assertEquals("Network Error", thrown?.message)
    }

    @Test
    fun awaitStringOrNull_cancellation() = runTest {
        val call = MockCall(response = null, exception = null) // Hangs, allowing cancellation
        val job = launch {
            call.awaitStringOrNull()
        }
        runCurrent() // let the coroutine start and suspend
        job.cancel()
        job.join()
        assertTrue(call.isCanceled())
    }
}
