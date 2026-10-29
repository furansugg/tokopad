package com.tokopad.app

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterConnectionTest {
    private class Socket : Closeable {
        val closes = AtomicInteger()
        val writes = AtomicInteger()
        val closed = CountDownLatch(1)
        override fun close() {
            closes.incrementAndGet()
            closed.countDown()
        }
    }

    @Test fun secureSuccessWritesOnceAndCloses() = runBlocking {
        val socket = Socket()
        PrinterConnection.use(
            secure = { socket }, insecure = { error("Unexpected fallback") },
            connect = { assertEquals(0, it.writes.get()) }, write = { it.writes.incrementAndGet(); Unit },
        )
        assertEquals(1, socket.writes.get())
        assertEquals(1, socket.closes.get())
    }

    @Test fun fallbackClosesFailedSocketBeforeConnectingAndWritesOnlyOnce() = runBlocking {
        val secure = Socket()
        val insecure = Socket()
        PrinterConnection.use(
            secure = { secure }, insecure = {
                assertTrue(secure.closes.get() > 0)
                insecure
            },
            connect = { if (it === secure) throw IOException("Secure SPP refused") },
            write = { it.writes.incrementAndGet(); Unit },
        )
        assertEquals(0, secure.writes.get())
        assertEquals(1, insecure.writes.get())
        assertEquals(1, insecure.closes.get())
    }

    @Test fun bothConnectionFailuresRetainDiagnosticsAndClose() = runBlocking {
        val secure = Socket()
        val insecure = Socket()
        try {
            PrinterConnection.use(
                secure = { secure }, insecure = { insecure },
                connect = { throw IOException(if (it === secure) "secure refused" else "insecure refused") },
                write = { error("Must not write") },
            )
            error("Expected IOException")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("secure refused"))
            assertTrue(error.message!!.contains("insecure refused"))
            assertEquals(1, error.suppressed.size)
        }
        assertTrue(secure.closes.get() > 0)
        assertTrue(insecure.closes.get() > 0)
    }

    @Test fun partialWriteNeverReconnects() = runBlocking {
        val socket = Socket()
        val failure = IOException("Connection lost after bytes sent")
        try {
            PrinterConnection.use(
                secure = { socket }, insecure = { error("Would duplicate receipt") }, connect = {},
                write = { it.writes.incrementAndGet(); throw failure },
            )
            error("Expected IOException")
        } catch (error: IOException) {
            assertSame(failure, error.cause)
            assertTrue(error.message!!.contains("may be partially printed"))
        }
        assertEquals(1, socket.writes.get())
        assertEquals(1, socket.closes.get())
    }

    @Test fun connectionTimeoutClosesBeforeFallback() = runBlocking {
        val secure = Socket()
        val insecure = Socket()
        PrinterConnection.use(
            secure = { secure }, insecure = {
                assertTrue(secure.closes.get() > 0)
                insecure
            },
            connect = {
                if (it === secure) assertTrue(it.closed.await(2, TimeUnit.SECONDS))
            },
            write = { it.writes.incrementAndGet(); Unit }, connectTimeoutMillis = 100,
        )
        assertEquals(0, secure.writes.get())
        assertEquals(1, insecure.writes.get())
        assertEquals(1, insecure.closes.get())
    }

    @Test fun outerTimeoutPropagatesWithoutFallbackAndCloses() = runBlocking {
        val socket = Socket()
        try {
            withTimeout(100) {
                PrinterConnection.use(
                    secure = { socket }, insecure = { error("Unexpected fallback") },
                    connect = { assertTrue(it.closed.await(2, TimeUnit.SECONDS)) },
                    write = { error("Must not write") },
                )
            }
            error("Expected timeout")
        } catch (_: TimeoutCancellationException) {
            assertTrue(socket.closes.get() > 0)
        }
    }

    @Test fun cancellationWhileConnectingClosesWithoutFallback() = runBlocking {
        val socket = Socket()
        val started = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            PrinterConnection.use(
                secure = { socket }, insecure = { error("Unexpected fallback") },
                connect = { started.complete(Unit); assertTrue(it.closed.await(2, TimeUnit.SECONDS)) },
                write = { error("Must not write") },
            )
        }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(socket.closes.get() > 0)
        assertEquals(0, socket.writes.get())
    }

    @Test fun cancellationWhileWritingClosesWithoutRetry() = runBlocking {
        val socket = Socket()
        val started = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            PrinterConnection.use(
                secure = { socket }, insecure = { error("Would duplicate receipt") }, connect = {},
                write = {
                    it.writes.incrementAndGet()
                    started.complete(Unit)
                    assertTrue(it.closed.await(2, TimeUnit.SECONDS))
                },
            )
        }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(socket.closes.get() > 0)
        assertEquals(1, socket.writes.get())
    }

    @Test fun cancellationExceptionIsNotConnectionFailure() = runBlocking {
        val socket = Socket()
        val cancellation = CancellationException("Cancelled connect")
        try {
            PrinterConnection.use(
                secure = { socket }, insecure = { error("Unexpected fallback") },
                connect = { throw cancellation }, write = { error("Must not write") },
            )
            error("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
        assertEquals(1, socket.closes.get())
    }

    @Test fun connectionTestClosesWithoutSendingBytes() = runBlocking {
        val socket = Socket()
        PrinterConnection.use(
            secure = { socket }, insecure = { error("Unexpected fallback") }, connect = {}, write = {},
        )
        assertEquals(0, socket.writes.get())
        assertEquals(1, socket.closes.get())
    }

    @Test fun missingPermissionDoesNotTriggerFallback() = runBlocking {
        val socket = Socket()
        val denied = SecurityException("Bluetooth permission revoked")
        try {
            PrinterConnection.use(
                secure = { socket }, insecure = { error("Unexpected fallback") },
                connect = { throw denied }, write = { error("Must not write") },
            )
            error("Expected SecurityException")
        } catch (error: SecurityException) {
            assertSame(denied, error)
        }
        assertEquals(1, socket.closes.get())
    }
}
