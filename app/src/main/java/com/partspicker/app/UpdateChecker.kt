package com.partspicker.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Looks at the latest GitHub release and compares its version with the installed one. */
object UpdateChecker {
    private const val REPO = "abhim5yu/partly-2"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Blocking network call — run it off the main thread. */
    fun check(context: Context): UpdateState {
        val installed = installedVersion(context)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PartsPicker-Android")
            }
            val code = conn.responseCode
            if (code == 404) return UpdateState.Failed("No releases published yet.")
            if (code !in 200..299) return UpdateState.Failed("GitHub returned error $code. Try again later.")

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val latest = json.optString("tag_name").removePrefix("v")
            if (latest.isEmpty()) return UpdateState.Failed("Couldn't read the latest version.")

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val url = assets.getJSONObject(i).optString("browser_download_url")
                    if (url.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = url
                        break
                    }
                }
            }
            val release = ReleaseInfo(latest, json.optString("html_url", RELEASES_PAGE), apkUrl)
            if (isNewer(latest, installed)) UpdateState.Available(release) else UpdateState.UpToDate(installed)
        } catch (e: Exception) {
            UpdateState.Failed("Couldn't reach GitHub. Check your connection.")
        } finally {
            conn?.disconnect()
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").split('.', '-').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

object UpdateNotifier {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, release: ReleaseInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when a new version of Parts Picker is available"
                }
            )
        }
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available")
            .setContentText("Parts Picker ${release.version} is ready to download.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}

/** Runs in the background (about every 12 hours) and posts a notification for a new release, once per version. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val storage = Storage(applicationContext)
        if (!storage.loadNotifyUpdates()) return Result.success()

        val result = withContext(Dispatchers.IO) { UpdateChecker.check(applicationContext) }
        if (result is UpdateState.Available && storage.loadLastNotifiedVersion() != result.release.version) {
            UpdateNotifier.show(applicationContext, result.release)
            storage.saveLastNotifiedVersion(result.release.version)
        }
        return Result.success()
    }
}

object UpdateScheduler {
    private const val WORK_NAME = "update-check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
