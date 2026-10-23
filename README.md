# Referalli for Android

Referral attribution for Android apps: App Links, Play Install Referrer, deferred deep links and
signup tracking for [Referalli](https://referalli.com). minSdk 21. No advertising id, no
`java.net.http`, no clipboard access.

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.referalli:referalli-android:0.1.0")
}
```

## Set up

```kotlin
import com.referalli.Referalli
import com.referalli.android.configure
import com.referalli.android.handleIntent

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Your tracking token (Settings → Tracking) is public and safe to ship in the app.
        Referalli.configure(this, publishableKey = "tk_…", programId = "prg_…")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Referalli.handleIntent(intent)          // opened from a referral link
        Referalli.getReferral { referral, _ ->  // main thread
            referral?.destination?.let { openDeepLinkedScreen(it) }
            referral?.code?.let { showInvitedBy(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Referalli.handleIntent(intent)
    }
}

// After the user signs up (customerRef is your user id, the same one your billing uses):
Referalli.trackSignup(user.id) { result, error ->
    if (error != null) Log.w("referalli", "signup not recorded", error)
    else Log.i("referalli", "attributed=${result?.attributed}")
}
```

From Java use `ReferalliAndroid.configure(context, "tk_…", "prg_…")` and
`ReferalliAndroid.handleIntent(intent)`; the callbacks are `ReferalliCallback<T>`.

## How the referral is found

`getReferral` returns the first of:

1. **A link.** A referral link (`https://your-link-domain/go/<token>/<code>`) redirects to the
   destination you set on the link with `?via=<code>&ref_click_id=<click>`. When that destination
   is an [App Link](https://developer.android.com/training/app-links) of your app, the app opens
   and `handleIntent` stores the click id. Opening a `/go/` URL directly in the app also works:
   the SDK records the click.
2. **The Play Install Referrer.** Point the link's destination at your Play listing with the
   referral inside `referrer`:
   `https://play.google.com/store/apps/details?id=com.example&referrer=via%3D<code>`.
   On first open the SDK reads `ref_click_id`, `via` or `ref` from it; with only a code it records
   the click through the API, so the signup still attributes deterministically.
3. **A deferred match** (first open only, within an hour of the click): the API matches this
   device's IP address to a recent click and returns its click id and destination. Turn it off
   with `deferredResolution = false`, then call `Referalli.resolveDeferred { … }` when your
   consent policy allows.

Only the first open after install looks up 2 and 3 (an install older than 24 hours when the SDK
first runs, i.e. an existing user updating, is skipped). A link opened later always replaces the
stored referral.

## Purchases

The app holds only the public tracking token, so it cannot create commissions. Send the
purchase to your backend, validate the Play receipt there, then report it with the server SDK
(`POST /v1/mobile/purchases` with a secret API key).

## Threading and transports

The `Referalli` facade runs network calls on a background thread and calls back on the main
thread. `ReferalliTracker` (from `Referalli.shared`) has blocking methods for coroutine code:

```kotlin
val referral = withContext(Dispatchers.IO) { Referalli.shared!!.getReferral() }
```

Requests use `HttpURLConnection`. To use your OkHttp client (OkHttp 4 or 5 must already be on your
classpath):

```kotlin
ReferalliAndroid.configure(this, "tk_…", transport = OkHttpTrackerTransport(okHttpClient))
```

## Privacy

The SDK stores a random install id and the captured referral in a private SharedPreferences
file (`referalli`). It sends the install id, the referral and the customer reference you pass to
`trackSignup` to Referalli. It does not read the advertising id, Android id or clipboard.
For the Play Data safety form, the SDK's share is *Device or other IDs* (the random install id)
and *User IDs* (your customer reference), collected for *App functionality* and *Analytics*,
processed by Referalli on your behalf, not used for advertising. The deferred match also uses the
IP address the request comes from.

## Requirements

- minSdk 21, compileSdk 35, Java 17 toolchain.
- Kotlin 2.4 or newer in the app if you call the Kotlin API (the core is compiled with Kotlin 2.4).
- `com.android.installreferrer:installreferrer` comes in transitively.

## Development

This module builds against `../sdk-kotlin` when it exists (the monorepo, or CI checking out
`referalli-kotlin` beside it); otherwise `com.referalli:referalli-kotlin` comes from Maven Central.
`gradle build` runs the unit tests and lint; `gradle assembleRelease` builds the AAR.

## License

MIT. See [LICENSE](LICENSE).
