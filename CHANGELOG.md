# Changelog

All notable changes to `com.referalli:referalli-android` are documented here.
This project follows [Semantic Versioning](https://semver.org/); before 1.0, minor versions may break.

## 0.2.0 - 2026-10-24

- Pairs with referalli-kotlin 0.2.0 (API 2026-11-01).
- Installable from JitPack (`com.github.Referalli:referalli-android`) until it is on Maven Central.

## 0.1.0 (unreleased)

- First release.
- `Referalli.configure(context, publishableKey)` wires the tracker with SharedPreferences storage,
  Play Install Referrer, a fresh-install check and main-thread callbacks.
- `Referalli.handleIntent(intent)` captures App Links and deep links (`via`, `ref`, `ref_click_id`,
  `/go/<token>/<code>`).
- First open after install resolves the referral from the Play Install Referrer, then from the
  API's deferred deep-link resolver.
- `Referalli.trackSignup(customerRef)` identifies the install and attributes the customer.
- `OkHttpTrackerTransport` for apps that want requests on their own OkHttp client.
- minSdk 21; no `java.net.http`.
