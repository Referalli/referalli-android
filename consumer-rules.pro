# referalli-kotlin also ships the server-side API client, whose default transport uses
# java.net.http (not on Android). Apps never load it through this SDK; tell R8 not to fail on it.
-dontwarn java.net.http.**
# OkHttpTrackerTransport is optional and compiled against OkHttp as compileOnly.
-dontwarn okhttp3.**
