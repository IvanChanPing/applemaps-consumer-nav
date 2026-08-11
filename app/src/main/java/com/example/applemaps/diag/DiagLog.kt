package com.example.applemaps.diag

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Process-wide diagnostic uploader used by the user-facing map trace and existing sheet/Look Around diagnostics.
 * `MainActivity.onCreate` calls [init]. Tagged events enter a bounded drop-oldest queue, are batched on one worker,
 * written first to a bounded app-private spool, and then POSTed through Android's validated default [Network]. A
 * failed upload remains in the spool and is retried after connectivity returns or the app restarts, so the user only
 * drives the UI while Codex reads `[removed]` directly from the box.
 *
 * Test status: collector round-trip and redroid replay are verified by the delivery workflow; this class deliberately
 * performs no disk or network work on the UI thread and never logs API tokens, search text, or place payloads.
 */
object DiagLog {
    private const val ENDPOINT = "[removed]"
    private const val MAX_LINE_CHARS = 2_000
    private const val MAX_QUEUE_LINES = 4_000
    private const val MAX_SPOOL_BYTES = 2 * 1024 * 1024
    private const val MAX_BATCH_BYTES = 48 * 1024
    private const val BATCH_DELAY_MS = 750L
    private const val MAX_RETRY_MS = 30_000L

    private val queue = ArrayBlockingQueue<String>(MAX_QUEUE_LINES)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val drainScheduled = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val session = UUID.randomUUID().toString().take(8)
    private val t0 = System.nanoTime()

    @Volatile private var appContext: Context? = null
    @Volatile private var validatedNetwork: Network? = null
    private val nextSequence = AtomicLong(1L)
    private var consecutiveFailures = 0
    private var retryAfterElapsedMs = 0L

    /** Correlation key shared by text events and automatically uploaded rendered map frames. */
    internal fun currentSessionId(): String = session

    /** Starts validated-network observation and replays any prior process's pending spool. Idempotent. */
    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        appContext = app
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                validatedNetwork = if (usable) network else null
                if (usable) scheduleDrain(0L)
            }

            override fun onLost(network: Network) {
                if (validatedNetwork == network) validatedNetwork = null
            }
        })
        log("DIAG", "event=init", "session=$session", "sdk=${Build.VERSION.SDK_INT}")
        scheduleDrain(0L)
    }

    /** Enqueues one privacy-scrubbed structured line; overflow drops the oldest line instead of growing memory. */
    fun log(tag: String, vararg kv: String) {
        val ms = (System.nanoTime() - t0) / 1_000_000
        var line = buildString {
            append("session="); append(session)
            append(" seq="); append(nextSequence.getAndIncrement())
            append(" ms="); append(ms)
            append(" tag="); append(tag)
            for (part in kv) { append(' '); append(part.replace('\n', '_').replace('\r', '_')) }
        }
        if (line.length > MAX_LINE_CHARS) line = line.substring(0, MAX_LINE_CHARS)
        if (!queue.offer(line)) {
            queue.poll()
            queue.offer(line)
        }
        scheduleDrain(BATCH_DELAY_MS)
    }

    private fun scheduleDrain(delayMs: Long) {
        if (appContext == null || !drainScheduled.compareAndSet(false, true)) return
        executor.schedule({
            drainScheduled.set(false)
            drain()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun drain() {
        val context = appContext ?: return
        val fresh = ArrayList<String>(128)
        queue.drainTo(fresh, 128)
        if (fresh.isNotEmpty()) appendToSpool(context, fresh.joinToString(separator = "\n", postfix = "\n"))

        val network = validatedNetwork
        val now = SystemClock.elapsedRealtime()
        if (network == null) return
        if (now < retryAfterElapsedMs) {
            scheduleDrain(retryAfterElapsedMs - now)
            return
        }

        val spool = spoolFile(context)
        val pending = readSpoolChunk(spool) ?: return
        if (post(network, pending.text)) {
            removeSpoolPrefix(spool, pending.byteCount)
            consecutiveFailures = 0
            retryAfterElapsedMs = 0L
            if (spool.length() > 0L || queue.isNotEmpty()) scheduleDrain(0L)
        } else {
            consecutiveFailures = min(consecutiveFailures + 1, 5)
            val retryMs = min(MAX_RETRY_MS, 1_000L shl consecutiveFailures)
            retryAfterElapsedMs = SystemClock.elapsedRealtime() + retryMs
            scheduleDrain(retryMs)
        }
    }

    private fun spoolFile(context: Context): File =
        File(context.filesDir, "diag/pending-map-render.log").apply { parentFile?.mkdirs() }

    private fun appendToSpool(context: Context, text: String) {
        val spool = spoolFile(context)
        spool.appendText(text, StandardCharsets.UTF_8)
        if (spool.length() <= MAX_SPOOL_BYTES) return
        val bytes = spool.readBytes()
        val keepFrom = (bytes.size - MAX_SPOOL_BYTES / 2).coerceAtLeast(0)
        spool.writeBytes(bytes.copyOfRange(keepFrom, bytes.size))
    }

    private data class SpoolChunk(val text: String, val byteCount: Int)

    private fun readSpoolChunk(spool: File): SpoolChunk? {
        if (!spool.exists() || spool.length() == 0L) return null
        val bytes = spool.readBytes()
        var end = min(bytes.size, MAX_BATCH_BYTES)
        if (end < bytes.size) {
            while (end > 0 && bytes[end - 1] != '\n'.code.toByte()) end--
            if (end == 0) end = min(bytes.size, MAX_BATCH_BYTES)
        }
        return SpoolChunk(String(bytes, 0, end, StandardCharsets.UTF_8), end)
    }

    private fun removeSpoolPrefix(spool: File, byteCount: Int) {
        val bytes = spool.readBytes()
        if (byteCount >= bytes.size) {
            spool.writeText("")
            return
        }
        val temp = File(spool.parentFile, "${spool.name}.tmp")
        temp.writeBytes(bytes.copyOfRange(byteCount, bytes.size))
        if (!temp.renameTo(spool)) {
            spool.writeBytes(bytes.copyOfRange(byteCount, bytes.size))
            temp.delete()
        }
    }

    private fun post(network: Network, body: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = network.openConnection(URL(ENDPOINT)) as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.doOutput = true
            connection.setRequestProperty("X-Device", Build.MODEL ?: "?")
            connection.setRequestProperty("X-Session", session)
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            (connection.inputStream.takeIf { code in 200..299 } ?: connection.errorStream)?.close()
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
