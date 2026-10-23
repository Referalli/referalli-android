package com.referalli.android

import android.content.Context
import android.content.SharedPreferences
import com.referalli.KeyValueStorage

/** Persists the install id and referral in a private SharedPreferences file. */
class SharedPreferencesStorage @JvmOverloads constructor(
    context: Context,
    name: String = "referalli",
) : KeyValueStorage {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}
