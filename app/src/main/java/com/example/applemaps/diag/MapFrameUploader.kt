package com.example.applemaps.diag

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

internal data class MapFrameMetadata(
    val session: String,
    val frameId: String,
    val burstId: String,
    val phase: String,
    val direction: String,
    val threshold: Double,
    val requestedElapsedMs: Long,
    val callbackElapsedMs: Long,
    val requestedZoom: Double,
    val callbackZoom: Double,
    val width: Int = 0,
    val height: Int = 0,
    val sha256: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("session", session)
        .put("frameId", frameId)
        .put("burstId", burstId)
        .put("phase", phase)
        .put("direction", direction)
        .put("threshold", threshold)
        .put("requestedElapsedMs", requestedElapsedMs)
        .put("callbackElapsedMs", callbackElapsedMs)
        .put("requestedZoom", requestedZoom)
        .put("callbackZoom", callbackZoom)
        .put("width", width)
        .put("height", height)
        .put("sha256", sha256)

    companion object {
        fun fromJson(json: JSONObject): MapFrameMetadata = MapFrameMetadata(
            session = json.getString("session"),
            frameId = json.getString("frameId"),
            burstId = json.getString("burstId"),
            phase = json.getString("phase"),
            direction = json.getString("direction"),
            threshold = json.getDouble("threshold"),
            requestedElapsedMs = json.getLong("requestedElapsedMs"),
            callbackElapsedMs = json.getLong("callbackElapsedMs"),
            requestedZoom = json.getDouble("requestedZoom"),
            callbackZoom = json.getDouble("callbackZoom"),
            width = json.getInt("width"),
            height = json.getInt("height"),
            sha256 = json.getString("sha256"),
        )
    }
}

/**
 * Bounded, process-wide binary uploader for debug map-crossing frames.
 *
 * Snapshot callbacks enqueue at most two lossless compression jobs. A worker writes PNG and metadata atomically to
 * a capped app-private spool, while a separate worker uploads the oldest pair only through a validated default
 * [Network]. Failed frames survive process restart; overflow prunes the oldest pairs. No compression, disk, hashing,
 * or network work runs on the UI or renderer callback thread.
 */
internal object MapFrameUploader {
    private const val ENDPOINT = "[removed]"
    private const val MAX_PENDING_COMPRESSIONS = 2
    private const val MAX_FRAME_BYTES = 12L * 1024 * 1024
    private const val MAX_SPOOL_BYTES = 32L * 1024 * 1024
    private const val MAX_SPOOL_FRAMES = 32
    private const val MAX_RETRY_MS = 30_000L

    private val initialized = AtomicBoolean(false)
    private val drainScheduled = AtomicBoolean(false)
    private val spoolLock = Any()
    private val compressionExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_PENDING_COMPRESSIONS),
        { runnable -> Thread(runnable, "map-frame-compress").apply { isDaemon = true } },
    )
    private val uploadExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "map-frame-upload").apply { isDaemon = true }
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var validatedNetwork: Network? = null
    private var consecutiveFailures = 0
    private var retryAfterElapsedMs = 0L

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val app = context.applicationContext
        appContext = app
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        connectivity.activeNetwork?.let { network ->
            if (isValidated(connectivity.getNetworkCapabilities(network))) validatedNetwork = network
        }
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val usable = isValidated(capabilities)
                validatedNetwork = if (usable) network else null
                if (usable) scheduleDrain(0L)
            }

            override fun onLost(network: Network) {
                if (validatedNetwork == network) validatedNetwork = null
            }
        })
        synchronized(spoolLock) { pruneSpool(spoolDir(app)) }
        DiagLog.log(
            "FRAME_UPLOAD", "event=init", "maxSpoolBytes=$MAX_SPOOL_BYTES",
            "maxSpoolFrames=$MAX_SPOOL_FRAMES", "maxPending=$MAX_PENDING_COMPRESSIONS",
        )
        scheduleDrain(0L)
    }

    fun canAcceptFrame(): Boolean = initialized.get() &&
        (compressionExecutor.activeCount == 0 || compressionExecutor.queue.remainingCapacity() > 0)

    fun enqueue(bitmap: Bitmap, metadata: MapFrameMetadata): Boolean {
        if (!initialized.get()) {
            bitmap.recycle()
            return false
        }
        return try {
            compressionExecutor.execute { compressAndSpool(bitmap, metadata) }
            true
        } catch (_: RejectedExecutionException) {
            bitmap.recycle()
            DiagLog.log("FRAME_UPLOAD", "event=drop", "reason=compressQueueFull", "frame=${metadata.frameId}")
            false
        }
    }

    private fun compressAndSpool(bitmap: Bitmap, metadata: MapFrameMetadata) {
        val context = appContext
        if (context == null) {
            bitmap.recycle()
            return
        }
        val directory = spoolDir(context)
        val safeBase = safeFilePart("${metadata.session}_${metadata.frameId}")
        val png = File(directory, "$safeBase.png")
        val json = File(directory, "$safeBase.json")
        val pngTemp = File(directory, ".$safeBase.png.tmp")
        val jsonTemp = File(directory, ".$safeBase.json.tmp")
        val width = bitmap.width
        val height = bitmap.height
        val startedMs = SystemClock.elapsedRealtime()
        try {
            directory.mkdirs()
            val compressed = FileOutputStream(pngTemp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!compressed || pngTemp.length() <= 0L || pngTemp.length() > MAX_FRAME_BYTES) {
                DiagLog.log(
                    "FRAME_UPLOAD", "event=drop", "reason=${if (compressed) "frameTooLarge" else "compressFailed"}",
                    "frame=${metadata.frameId}", "bytes=${pngTemp.length()}",
                )
                return
            }
            val completed = metadata.copy(width = width, height = height, sha256 = sha256(pngTemp))
            jsonTemp.writeText(completed.toJson().toString())
            if (!jsonTemp.renameTo(json) || !pngTemp.renameTo(png)) {
                DiagLog.log("FRAME_UPLOAD", "event=drop", "reason=atomicRenameFailed", "frame=${metadata.frameId}")
                return
            }
            synchronized(spoolLock) { pruneSpool(directory) }
            DiagLog.log(
                "FRAME_UPLOAD", "event=spooled", "frame=${metadata.frameId}", "bytes=${png.length()}",
                "size=${width}x$height", "compressMs=${SystemClock.elapsedRealtime() - startedMs}",
                "sha256=${completed.sha256}",
            )
            scheduleDrain(0L)
        } catch (error: Exception) {
            DiagLog.log("FRAME_UPLOAD", "event=error", "stage=compress", "kind=${error.javaClass.simpleName}")
        } finally {
            bitmap.recycle()
            pngTemp.delete()
            jsonTemp.delete()
        }
    }

    private fun scheduleDrain(delayMs: Long) {
        if (appContext == null || !drainScheduled.compareAndSet(false, true)) return
        uploadExecutor.schedule({
            drainScheduled.set(false)
            drain()
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun drain() {
        val context = appContext ?: return
        val network = validatedNetwork ?: return
        val now = SystemClock.elapsedRealtime()
        if (now < retryAfterElapsedMs) {
            scheduleDrain(retryAfterElapsedMs - now)
            return
        }
        val pair = synchronized(spoolLock) { oldestCompletePair(spoolDir(context)) } ?: return
        val metadata = try {
            MapFrameMetadata.fromJson(JSONObject(pair.second.readText()))
        } catch (error: Exception) {
            pair.first.delete()
            pair.second.delete()
            DiagLog.log("FRAME_UPLOAD", "event=drop", "reason=badMetadata", "kind=${error.javaClass.simpleName}")
            scheduleDrain(0L)
            return
        }
        if (post(network, pair.first, metadata)) {
            val uploadedBytes = pair.first.length()
            synchronized(spoolLock) {
                pair.first.delete()
                pair.second.delete()
            }
            consecutiveFailures = 0
            retryAfterElapsedMs = 0L
            DiagLog.log("FRAME_UPLOAD", "event=uploaded", "frame=${metadata.frameId}", "bytes=$uploadedBytes")
            scheduleDrain(0L)
        } else {
            consecutiveFailures = min(consecutiveFailures + 1, 5)
            val retryMs = min(MAX_RETRY_MS, 1_000L shl consecutiveFailures)
            retryAfterElapsedMs = SystemClock.elapsedRealtime() + retryMs
            DiagLog.log("FRAME_UPLOAD", "event=retry", "frame=${metadata.frameId}", "delayMs=$retryMs")
            scheduleDrain(retryMs)
        }
    }

    private fun post(network: Network, png: File, metadata: MapFrameMetadata): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = network.openConnection(URL(ENDPOINT)) as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(png.length())
            connection.setRequestProperty("Content-Type", "image/png")
            connection.setRequestProperty("X-Device", header(Build.MODEL ?: "?"))
            connection.setRequestProperty("X-Session", header(metadata.session))
            connection.setRequestProperty("X-Frame-Id", header(metadata.frameId))
            connection.setRequestProperty("X-Burst-Id", header(metadata.burstId))
            connection.setRequestProperty("X-Phase", header(metadata.phase))
            connection.setRequestProperty("X-Direction", header(metadata.direction))
            connection.setRequestProperty("X-Threshold", metadata.threshold.toString())
            connection.setRequestProperty("X-Requested-Ms", metadata.requestedElapsedMs.toString())
            connection.setRequestProperty("X-Callback-Ms", metadata.callbackElapsedMs.toString())
            connection.setRequestProperty("X-Request-Zoom", metadata.requestedZoom.toString())
            connection.setRequestProperty("X-Callback-Zoom", metadata.callbackZoom.toString())
            connection.setRequestProperty("X-Width", metadata.width.toString())
            connection.setRequestProperty("X-Height", metadata.height.toString())
            connection.setRequestProperty("X-SHA256", metadata.sha256)
            connection.outputStream.use { output -> FileInputStream(png).use { it.copyTo(output) } }
            val code = connection.responseCode
            (connection.inputStream.takeIf { code in 200..299 } ?: connection.errorStream)?.close()
            code in 200..299
        } catch (error: Exception) {
            DiagLog.log("FRAME_UPLOAD", "event=error", "stage=upload", "kind=${error.javaClass.simpleName}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun spoolDir(context: Context): File = File(context.filesDir, "diag/map-frames")

    private fun oldestCompletePair(directory: File): Pair<File, File>? = directory.listFiles()
        ?.filter { it.extension == "png" && File(directory, "${it.nameWithoutExtension}.json").isFile }
        ?.minByOrNull(File::lastModified)
        ?.let { it to File(directory, "${it.nameWithoutExtension}.json") }

    private fun pruneSpool(directory: File) {
        directory.mkdirs()
        directory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach(File::delete)
        var frames = directory.listFiles()?.filter { it.extension == "png" }?.sortedBy(File::lastModified).orEmpty()
        var bytes = frames.sumOf(File::length)
        var count = frames.size
        for (png in frames) {
            if (bytes <= MAX_SPOOL_BYTES && count <= MAX_SPOOL_FRAMES) break
            bytes -= png.length()
            count--
            File(directory, "${png.nameWithoutExtension}.json").delete()
            png.delete()
            DiagLog.log("FRAME_UPLOAD", "event=pruned", "file=${png.name}")
        }
        val pngNames = directory.listFiles()?.filter { it.extension == "png" }?.map { it.nameWithoutExtension }?.toSet().orEmpty()
        directory.listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension !in pngNames }?.forEach(File::delete)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isValidated(capabilities: NetworkCapabilities?): Boolean = capabilities != null &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun safeFilePart(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(180)
    private fun header(value: String): String = value.replace('\n', '_').replace('\r', '_').take(180)
}
