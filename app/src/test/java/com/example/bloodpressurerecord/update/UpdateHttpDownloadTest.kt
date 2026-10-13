package com.example.bloodpressurerecord.update

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateHttpDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val abcHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test(timeout = 25_000) fun `default fifteen second stall switches silent connected source and verifies digest`() = runBlocking {
        LocalServer { socket, _, release -> release.await(24, TimeUnit.SECONDS) }.use { silent ->
            LocalServer { socket, _, _ -> socket.respond("abc".toByteArray()) }.use { working ->
                val file = temporary.newFile()
                val sources = mutableListOf<String>()
                var verified = false
                val start = System.nanoTime()
                engine().download(listOf(silent.url, working.url), file, 3,
                    onSource = { sources += it }, verifyFile = {
                        assertEquals(abcHash, UpdateDownloadSafety.sha256(it))
                        verified = true
                    })
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                assertTrue("Switched too early: $elapsed ms", elapsed >= 14_000)
                assertTrue("Failed to switch within stall timeout + 5 s: $elapsed ms", elapsed < 20_000)
                assertEquals(listOf(silent.url, working.url), sources)
                assertTrue(verified)
                assertEquals("abc", file.readText())
                assertTrue(silent.accepted.await(1, TimeUnit.SECONDS))
                silent.assertHealthy()
                working.assertHealthy()
            }
        }
    }

    @Test(timeout = 8_000) fun `body stall automatically resumes byte range on the next source`() = runBlocking {
        val bytes = "abcdef".toByteArray()
        LocalServer { socket, _, release ->
            socket.headers("200 OK", listOf("Content-Length: 6"))
            socket.getOutputStream().apply { write(bytes, 0, 3); flush() }
            release.await(7, TimeUnit.SECONDS)
        }.use { stalled ->
            LocalServer { socket, request, _ ->
                assertEquals("bytes=3-", request["range"])
                socket.respond(bytes.copyOfRange(3, 6), "206 Partial Content", listOf("Content-Range: bytes 3-5/6"))
            }.use { working ->
                val file = temporary.newFile()
                val sources = mutableListOf<String>()
                engine(300).download(listOf(stalled.url, working.url), file, bytes.size.toLong(),
                    onSource = { sources += it }, verifyFile = { assertArrayEquals(bytes, it.readBytes()) })
                assertEquals(listOf(stalled.url, working.url), sources)
                assertArrayEquals(bytes, file.readBytes())
                working.assertHealthy()
            }
        }
    }

    @Test(timeout = 8_000) fun `partial header trickle cannot defeat the stall watchdog`() = runBlocking {
        LocalServer { socket, _, release ->
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nX-Incomplete: ".toByteArray())
            output.flush()
            while (!release.await(60, TimeUnit.MILLISECONDS)) {
                try { output.write('a'.code); output.flush() } catch (_: java.io.IOException) { break }
            }
        }.use { trickle ->
            LocalServer { socket, _, _ -> socket.respond("abc".toByteArray()) }.use { working ->
                val file = temporary.newFile()
                val start = System.nanoTime()
                engine(500).download(listOf(trickle.url, working.url), file, 3,
                    verifyFile = { assertEquals(abcHash, UpdateDownloadSafety.sha256(it)) })
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000)
                assertEquals("abc", file.readText())
            }
        }
    }

    @Test(timeout = 8_000) fun `ignored range starts a clean complete file`() = runBlocking {
        LocalServer { socket, request, _ ->
            assertEquals("bytes=1-", request["range"])
            socket.respond("abc".toByteArray())
        }.use { server ->
            val file = temporary.newFile().apply { writeText("x") }
            engine(300).download(listOf(server.url), file, 3,
                verifyFile = { assertEquals(abcHash, UpdateDownloadSafety.sha256(it)) })
            assertEquals("abc", file.readText())
            server.assertHealthy()
        }
    }

    @Test(timeout = 8_000) fun `digest failure clears partial and verifies the next candidate`() = runBlocking {
        LocalServer { socket, _, _ -> socket.respond("abd".toByteArray()) }.use { bad ->
            LocalServer { socket, request, _ ->
                assertNull(request["range"])
                socket.respond("abc".toByteArray())
            }.use { good ->
                val file = temporary.newFile()
                var verifications = 0
                engine(300).download(listOf(bad.url, good.url), file, 3, verifyFile = {
                    verifications++
                    if (UpdateDownloadSafety.sha256(it) != abcHash) throw UpdateVerificationException("SHA-256 mismatch")
                })
                assertEquals(2, verifications)
                assertEquals(abcHash, UpdateDownloadSafety.sha256(file))
                good.assertHealthy()
            }
        }
    }

    @Test(timeout = 8_000) fun `only exhaustion fails and lists every attempted source`() = runBlocking {
        LocalServer { socket, _, _ -> socket.headers("503 Unavailable", listOf("Content-Length: 0")) }.use { first ->
            LocalServer { socket, _, _ -> socket.headers("502 Unavailable", listOf("Content-Length: 0")) }.use { second ->
                val file = temporary.newFile()
                try {
                    engine(300).download(listOf(first.url, second.url), file, 3,
                        verifyFile = { fail("Failed response must not be verified") })
                    fail("Expected all sources to fail")
                } catch (failure: UpdateSourcesExhaustedException) {
                    assertEquals(listOf(first.url, second.url), failure.attemptedUrls)
                    assertTrue(failure.message!!.contains(URL(first.url).authority))
                    assertTrue(failure.message!!.contains(URL(second.url).authority))
                    assertFalse(failure.verificationFailure)
                }
            }
        }
    }

    @Test(timeout = 8_000) fun `unsafe response headers are rejected before another candidate succeeds`() = runBlocking {
        val unsafe = listOf(
            "302 Found" to listOf("Location: https://evil.example/file.apk"),
            "200 OK" to listOf("Content-Length: 99"),
            "200 OK" to listOf("Content-Length: 3", "Content-Encoding: gzip"),
            "206 Partial Content" to listOf("Content-Length: 3", "Content-Range: bytes 1-3/4")
        )
        for ((status, headers) in unsafe) {
            LocalServer { socket, _, _ -> socket.headers(status, headers) }.use { bad ->
                LocalServer { socket, _, _ -> socket.respond("abc".toByteArray()) }.use { good ->
                    val file = temporary.newFile()
                    engine(300).download(listOf(bad.url, good.url), file, 3,
                        verifyFile = { assertEquals(abcHash, UpdateDownloadSafety.sha256(it)) })
                    assertEquals("abc", file.readText())
                }
            }
        }
    }

    @Test(timeout = 8_000) fun `oversized body is rejected and never passed to verifier`() = runBlocking {
        LocalServer { socket, _, _ ->
            socket.headers("200 OK", emptyList())
            socket.getOutputStream().apply { write("abcd".toByteArray()); flush() }
        }.use { server ->
            var verified = false
            val file = temporary.newFile()
            try {
                engine(300).download(listOf(server.url), file, 3, verifyFile = { verified = true })
                fail("Expected overflow rejection")
            } catch (failure: UpdateSourcesExhaustedException) {
                assertTrue(failure.verificationFailure)
                assertFalse(verified)
                assertFalse(file.exists())
            }
        }
    }

    @Test(timeout = 8_000) fun `cancellation does not advance to another candidate`() = runBlocking {
        val opened = mutableListOf<String>()
        val downloader = UpdateHttpDownload(openConnection = { url ->
            opened += url
            throw CancellationException("Paused")
        })
        try {
            downloader.download(listOf("https://first.example", "https://second.example"), temporary.newFile(), 3,
                verifyFile = { fail("Cancelled download must not be verified") })
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf("https://first.example"), opened)
        }
    }

    private fun engine(timeoutMillis: Int = 15_000) = UpdateHttpDownload(
        openConnection = { URL(it).openConnection() as HttpURLConnection }, stallTimeoutMillis = timeoutMillis)

    private class LocalServer(handler: (Socket, Map<String, String>, CountDownLatch) -> Unit) : Closeable {
        private val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        private val release = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        private val activeSocket = AtomicReference<Socket?>()
        val accepted = CountDownLatch(1)
        val url = "http://127.0.0.1:${server.localPort}/update.apk"
        private val thread = Thread {
            try {
                server.accept().use { socket ->
                    activeSocket.set(socket)
                    accepted.countDown()
                    socket.soTimeout = 3_000
                    val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    input.readLine()
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) headers[line.substring(0, colon).lowercase()] = line.substring(colon + 1).trim()
                    }
                    handler(socket, headers, release)
                }
            } catch (error: Throwable) {
                if (release.count > 0) failure.set(error)
            }
        }.apply { isDaemon = true; start() }

        fun assertHealthy() { failure.get()?.let { throw AssertionError("Local server failed", it) } }
        override fun close() {
            release.countDown()
            activeSocket.get()?.close()
            server.close()
            thread.join(1_000)
            assertHealthy()
        }
    }

    private companion object {
        fun Socket.headers(status: String, headers: List<String>) {
            getOutputStream().apply {
                write((listOf("HTTP/1.1 $status", "Connection: close") + headers + listOf("", ""))
                    .joinToString("\r\n").toByteArray(Charsets.US_ASCII))
                flush()
            }
        }

        fun Socket.respond(bytes: ByteArray, status: String = "200 OK", extraHeaders: List<String> = emptyList()) {
            headers(status, listOf("Content-Length: ${bytes.size}") + extraHeaders)
            getOutputStream().apply { write(bytes); flush() }
        }
    }
}
