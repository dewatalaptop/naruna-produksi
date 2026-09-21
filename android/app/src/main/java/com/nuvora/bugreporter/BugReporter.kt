package com.nuvora.bugreporter

// Nuvora bug reporter for native Android apps — zero dependencies (only the
// Android SDK + org.json). Copy this file into the app's source tree, keep the
// package line or change it to the app's own, and call from Application.onCreate():
//
//   BugReporter.init(this, appId = "my-app", key = "<from register-app>", version = BuildConfig.VERSION_NAME)
//
// What it does:
//  * installs a default uncaught-exception handler; the crash is written to disk
//    FIRST (a dying process cannot be trusted to finish a network call) and sent on
//    the next launch, then the previous handler runs so Android's normal crash
//    behaviour is unchanged;
//  * BugReporter.report(throwable, note) for caught-but-serious failures;
//  * failed sends stay queued on disk (max 20) and are retried on the next init/report.
// The server redacts emails / tokens / long numbers again; still, never put personal
// data in `note` or `extra`.

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

object BugReporter {
    private const val DEFAULT_ENDPOINT = "https://us-central1-ai-app-builder-7bf8e.cloudfunctions.net/reportAppError"
    private const val QUEUE_FILE = "nuvora-bug-queue.json"
    private const val MAX_QUEUE = 20
    private const val MAX_PER_RUN = 10

    private var appId = ""
    private var key = ""
    private var version = ""
    private var endpoint = DEFAULT_ENDPOINT
    private var filesDir: File? = null
    private val sessionId = UUID.randomUUID().toString()
    private val breadcrumbs = ArrayDeque<String>()
    private var sentThisRun = 0
    private val lock = Any()

    fun init(context: Context, appId: String, key: String, version: String, endpoint: String = DEFAULT_ENDPOINT) {
        if (appId.isBlank() || key.isBlank()) return
        this.appId = appId
        this.key = key
        this.version = version
        this.endpoint = endpoint
        this.filesDir = context.applicationContext.filesDir

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                enqueue(build("crash", e, "Uncaught exception in thread ${t.name}", null))
            } catch (ignored: Throwable) {
                // reporting must never mask the real crash
            }
            previous?.uncaughtException(t, e)
        }
        flushAsync()
    }

    /** A short trail of what the user just did, attached to the next report. */
    fun breadcrumb(text: String) {
        synchronized(lock) {
            breadcrumbs.addLast(text.take(140))
            while (breadcrumbs.size > 30) breadcrumbs.removeFirst()
        }
    }

    /** Report a caught exception that still means something is broken. */
    fun report(throwable: Throwable, note: String? = null, extra: Map<String, String> = emptyMap()) {
        if (appId.isBlank() || sentThisRun >= MAX_PER_RUN) return
        sentThisRun++
        enqueue(build("error", throwable, throwable.message ?: throwable.javaClass.simpleName, note, extra))
        flushAsync()
    }

    /** A person typed a problem report (e.g. from a Help > "Laporkan masalah" screen). */
    fun reportManual(note: String, extra: Map<String, String> = emptyMap()) {
        if (appId.isBlank()) return
        enqueue(build("manual", null, "Laporan dari pengguna", note, extra))
        flushAsync()
    }

    private fun build(kind: String, t: Throwable?, message: String, note: String?, extra: Map<String, String> = emptyMap()): JSONObject {
        val stack = t?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString().take(4000)
        } ?: ""
        val crumbs = synchronized(lock) { JSONArray(breadcrumbs.toList()) }
        val context = JSONObject().apply {
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", Build.VERSION.SDK_INT)
            extra.entries.take(15).forEach { put(it.key, it.value) }
        }
        return JSONObject().apply {
            put("appId", appId)
            put("key", key)
            put("kind", kind)
            put("platform", "android")
            put("version", version)
            put("message", (if (t != null) "${t.javaClass.simpleName}: " else "") + message.take(300))
            put("stack", stack)
            put("userNote", note ?: "")
            put("breadcrumbs", crumbs)
            put("context", context)
            put("sessionId", sessionId)
        }
    }

    // --- disk queue --------------------------------------------------------------

    private fun queueFile(): File? = filesDir?.let { File(it, QUEUE_FILE) }

    private fun readQueue(): JSONArray = try {
        val f = queueFile()
        if (f != null && f.exists()) JSONArray(f.readText()) else JSONArray()
    } catch (ignored: Throwable) {
        JSONArray()
    }

    private fun writeQueue(q: JSONArray) {
        try {
            val trimmed = JSONArray()
            val start = maxOf(0, q.length() - MAX_QUEUE)
            for (i in start until q.length()) trimmed.put(q.get(i))
            queueFile()?.writeText(trimmed.toString())
        } catch (ignored: Throwable) {
            // disk full / unwritable: drop the report rather than crash the reporter
        }
    }

    private fun enqueue(report: JSONObject) {
        synchronized(lock) {
            val q = readQueue()
            q.put(report)
            writeQueue(q)
        }
    }

    private fun flushAsync() {
        thread(name = "nuvora-bug-flush", isDaemon = true) {
            try {
                while (true) {
                    val next: JSONObject = synchronized(lock) {
                        val q = readQueue()
                        if (q.length() == 0) return@thread
                        q.getJSONObject(0)
                    }
                    val outcome = post(next)
                    if (outcome == Outcome.RETRY_LATER) return@thread
                    synchronized(lock) {
                        val q = readQueue()
                        val rest = JSONArray()
                        for (i in 1 until q.length()) rest.put(q.get(i))
                        writeQueue(rest)
                    }
                }
            } catch (ignored: Throwable) {
                // never let reporting take the app down
            }
        }
    }

    private enum class Outcome { DONE, RETRY_LATER }

    private fun post(body: JSONObject): Outcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            // 5xx: try again later. Anything else (2xx ok, 4xx = bad key/app) will
            // never change on retry, so drop it instead of blocking the queue.
            if (code >= 500) Outcome.RETRY_LATER else Outcome.DONE
        } catch (ignored: Throwable) {
            Outcome.RETRY_LATER // offline / DNS / timeout
        } finally {
            conn?.disconnect()
        }
    }
}
