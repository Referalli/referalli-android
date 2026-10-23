package com.referalli.android

import android.content.Context
import android.os.Looper
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.referalli.InstallReferrerProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads the Play Install Referrer (`referrer=` of the Play Store URL the user installed from).
 * Blocks the calling thread for up to [timeoutMs]; returns null on the main thread, on devices
 * without the Play Store, and when the service is unavailable.
 */
class PlayInstallReferrerProvider @JvmOverloads constructor(
    context: Context,
    private val timeoutMs: Long = 5_000,
) : InstallReferrerProvider {
    private val app = context.applicationContext

    override fun installReferrer(): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null // never block the UI thread
        val client = try {
            InstallReferrerClient.newBuilder(app).build()
        } catch (_: Exception) {
            return null
        }
        val result = AtomicReference<String?>(null)
        val done = CountDownLatch(1)
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            result.set(client.installReferrer.installReferrer)
                        }
                    } catch (_: Exception) {
                        // RemoteException / SecurityException: treat as unavailable.
                    } finally {
                        done.countDown()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    done.countDown()
                }
            })
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { client.endConnection() }
        }
        return result.get()
    }
}
