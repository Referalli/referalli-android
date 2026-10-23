package com.referalli.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.referalli.Referalli
import com.referalli.ReferalliReferral
import com.referalli.ReferalliTracker
import com.referalli.TrackerTransport
import com.referalli.UrlConnectionTrackerTransport
import java.util.concurrent.Executor

/**
 * Android wiring for the Referalli tracker: SharedPreferences storage, Play Install Referrer,
 * a fresh-install check and main-thread callbacks.
 *
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Referalli.configure(this, publishableKey = "tk_…", programId = "prg_…")
 *     }
 * }
 * // In the launcher Activity (onCreate and onNewIntent):
 * Referalli.handleIntent(intent)
 * Referalli.getReferral { referral, _ -> referral?.destination?.let(::route) }
 * // After signup:
 * Referalli.trackSignup(user.id) { result, error -> … }
 * ```
 */
object ReferalliAndroid {
    /** Installs older than this skip first-open matching (an existing user updating the app). */
    const val FRESH_INSTALL_WINDOW_MS: Long = 24L * 60 * 60 * 1000

    @JvmStatic
    @JvmOverloads
    fun configure(
        context: Context,
        publishableKey: String,
        programId: String? = null,
        baseUrl: String = ReferalliTracker.DEFAULT_BASE_URL,
        deferredResolution: Boolean = true,
        useInstallReferrer: Boolean = true,
        transport: TrackerTransport = UrlConnectionTrackerTransport(),
    ): ReferalliTracker {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        return Referalli.configure(
            publishableKey = publishableKey,
            programId = programId,
            baseUrl = baseUrl,
            storage = SharedPreferencesStorage(app),
            transport = transport,
            deferredResolution = deferredResolution,
            installReferrer = if (useInstallReferrer) PlayInstallReferrerProvider(app) else null,
            freshInstall = { isFreshInstall(app) },
            callbackExecutor = Executor { main.post(it) },
        )
    }

    /** Captures a referral from the Intent's data URI (App Links / deep links). */
    @JvmStatic
    fun handleIntent(intent: Intent?): ReferalliReferral? {
        val data = intent?.data ?: return null
        return Referalli.handleDeepLink(data.toString())
    }

    @JvmStatic
    @JvmOverloads
    fun isFreshInstall(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(context.packageName, 0)
        }
        nowMillis - info.firstInstallTime < FRESH_INSTALL_WINDOW_MS
    } catch (_: Exception) {
        true
    }
}

/** `Referalli.configure(context, "tk_…")`: the Android configuration of the shared tracker. */
@JvmSynthetic
fun Referalli.configure(
    context: Context,
    publishableKey: String,
    programId: String? = null,
    deferredResolution: Boolean = true,
    useInstallReferrer: Boolean = true,
): ReferalliTracker = ReferalliAndroid.configure(
    context, publishableKey, programId,
    deferredResolution = deferredResolution, useInstallReferrer = useInstallReferrer,
)

/** `Referalli.handleIntent(intent)`: capture an App Link / deep link. */
@JvmSynthetic
fun Referalli.handleIntent(intent: Intent?): ReferalliReferral? = ReferalliAndroid.handleIntent(intent)
