package com.tokopad.app

import java.io.Closeable
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal object PrinterConnection {
    suspend fun <T : Closeable> use(
        secure: () -> T,
        insecure: () -> T,
        connect: (T) -> Unit,
        write: (T) -> Unit,
        connectTimeoutMillis: Long = 9_000,
        pause: suspend (Long) -> Unit = { delay(it) },
    ) {
        val socket = try {
            open(secure, connect, connectTimeoutMillis)
        } catch (secureError: IOException) {
            currentCoroutineContext().ensureActive()
            try {
                open(insecure, connect, connectTimeoutMillis)
            } catch (insecureError: IOException) {
                throw IOException(
                    "Cannot connect to printer using secure or insecure SPP. " +
                        "Secure: ${secureError.message}; insecure: ${insecureError.message}. " +
                        "Check printer power, range, and connections to other apps or phones.",
                    insecureError,
                ).apply { addSuppressed(secureError) }
            }
        }
        try {
            currentCoroutineContext().ensureActive()
            try {
                withTimeout(15_000) {
                    // ponytail: fixed warmup/drain for Classic SPP; use model-specific status ACKs if hardware tests require them.
                    pause(300)
                    currentCoroutineContext().ensureActive()
                    blocking(socket, write)
                    pause(500)
                    currentCoroutineContext().ensureActive()
                }
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw IOException(
                    "Printer write timed out; receipt may be partially printed. Check paper before retrying.",
                    error,
                )
            } catch (error: IOException) {
                throw IOException(
                    "Printer write failed; receipt may be partially printed. Check paper before retrying. ${error.message}",
                    error,
                )
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun <T : Closeable> open(create: () -> T, connect: (T) -> Unit, timeoutMillis: Long): T {
        currentCoroutineContext().ensureActive()
        val socket = create()
        try {
            try {
                withTimeout(timeoutMillis) { blocking(socket, connect) }
            } catch (error: TimeoutCancellationException) {
                // An outer timeout/cancellation must not start another connection attempt.
                currentCoroutineContext().ensureActive()
                throw IOException("SPP connection timed out after $timeoutMillis ms", error)
            }
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private suspend fun <T : Closeable> blocking(socket: T, operation: (T) -> Unit): Unit =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { socket.close() } }
            // A blocking vendor stack must not keep a cancelled coroutine or the process alive.
            thread(isDaemon = true, name = "printer-bluetooth") {
                try {
                    if (continuation.isActive) {
                        operation(socket)
                        continuation.resume(Unit)
                    }
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
}
