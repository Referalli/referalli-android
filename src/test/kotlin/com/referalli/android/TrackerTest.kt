package com.referalli.android

import com.referalli.InMemoryStorage
import com.referalli.InstallReferrerProvider
import com.referalli.ReferalliSignupResult
import com.referalli.ReferalliTracker
import com.referalli.ReferalliTrackerException
import com.referalli.ReferralSource
import com.referalli.TrackerRequest
import com.referalli.TrackerResponse
import com.referalli.TrackerTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JVM unit tests of the tracker core (no Android framework needed). */
class TrackerTest {
    private class Stub(private val routes: Map<String, Pair<Int, String>>) : TrackerTransport {
        val requests = mutableListOf<TrackerRequest>()
        override fun send(request: TrackerRequest): TrackerResponse {
            synchronized(requests) { requests += request }
            val hit = routes.entries.firstOrNull { request.url.substringBefore('?').endsWith(it.key) }?.value
            return TrackerResponse(hit?.first ?: 404, hit?.second ?: "{}")
        }
        fun count(suffix: String) = requests.count { it.url.endsWith(suffix) }
        fun body(suffix: String) = requests.last { it.url.endsWith(suffix) }.body!!
    }

    private fun tracker(
        stub: Stub,
        programId: String? = "prg_1",
        referrer: String? = null,
        deferred: Boolean = true,
        fresh: Boolean = true,
    ) = ReferalliTracker(
        publishableKey = "tk_test",
        programId = programId,
        baseUrl = "https://api.example",
        storage = InMemoryStorage(),
        transport = stub,
        deferredResolution = deferred,
        installReferrer = referrer?.let { r -> InstallReferrerProvider { r } },
        freshInstall = { fresh },
    )

    @Test fun parsesAppLinks() {
        val r = ReferalliTracker.parseLink("https://app.example/welcome?via=dana&ref_click_id=clk_9")!!
        assertEquals("dana", r.code)
        assertEquals("clk_9", r.clickId)
        assertEquals(ReferralSource.LINK, r.source)
        assertEquals("dana", ReferalliTracker.parseLink("https://referalli.com/go/tk_test/dana")!!.code)
        assertEquals("x y", ReferalliTracker.parseLink("myapp://open?ref=x%20y")!!.code)
        assertNull(ReferalliTracker.parseLink("https://app.example/settings?tab=1"))
        assertNull(ReferalliTracker.parseLink("not a url"))
    }

    @Test fun parsesInstallReferrerPlainAndEncoded() {
        assertEquals("clk_1", ReferalliTracker.parseInstallReferrer("ref_click_id=clk_1&via=dana")!!.clickId)
        assertEquals("dana", ReferalliTracker.parseInstallReferrer("via%3Ddana%26utm_source%3Dx")!!.code)
        assertNull(ReferalliTracker.parseInstallReferrer("utm_source=google-play&utm_medium=organic"))
        assertNull(ReferalliTracker.parseInstallReferrer(null))
    }

    @Test fun installReferrerWinsAndSkipsFingerprint() {
        val stub = Stub(mapOf("/visit" to (200 to """{"ok":true,"clickId":"clk_1"}""")))
        val r = tracker(stub, referrer = "ref_click_id=clk_1&via=dana").getReferral()!!
        assertEquals(ReferralSource.INSTALL_REFERRER, r.source)
        assertEquals("clk_1", r.clickId)
        assertEquals(0, stub.count("/mobile/resolve"))
    }

    @Test fun organicReferrerFallsBackToDeferredOnce() {
        val stub = Stub(mapOf(
            "/mobile/resolve" to (200 to """{"matched":true,"clickId":"clk_2","code":"dana","destination":"https://app.example/w","extra":{"a":[1,2]}}"""),
            "/visit" to (200 to """{"ok":true,"clickId":"clk_2"}"""),
        ))
        val t = tracker(stub, referrer = "utm_medium=organic")
        val first = t.getReferral()!!
        assertEquals(ReferralSource.DEFERRED, first.source)
        assertEquals("https://app.example/w", first.destination)
        assertEquals(first, t.getReferral())
        assertEquals(1, stub.count("/mobile/resolve"))
    }

    @Test fun unmatchedIsFinalButFailureRetries() {
        val unmatched = Stub(mapOf("/mobile/resolve" to (200 to """{"matched":false}""")))
        val t1 = tracker(unmatched)
        assertNull(t1.getReferral()); assertNull(t1.getReferral())
        assertEquals(1, unmatched.count("/mobile/resolve"))

        val down = Stub(mapOf("/mobile/resolve" to (503 to "{}")))
        val t2 = tracker(down)
        assertNull(t2.getReferral()); assertNull(t2.getReferral())
        assertEquals(2, down.count("/mobile/resolve"))
    }

    @Test fun staleInstallAndDisabledResolutionSkipNetwork() {
        val stub = Stub(emptyMap())
        assertNull(tracker(stub, fresh = false).getReferral())
        assertNull(tracker(stub, deferred = false).getReferral())
        assertTrue(stub.requests.isEmpty())
    }

    @Test fun deepLinkBeatsResolution() {
        val stub = Stub(emptyMap())
        val t = tracker(stub)
        t.handleDeepLink("https://app.example/?via=dana&ref_click_id=clk_7")
        assertEquals("clk_7", t.getReferral()!!.clickId)
        assertTrue(stub.requests.isEmpty())
    }

    @Test fun reportVisitFillsInClickIdForShortLinks() {
        val stub = Stub(mapOf("/visit" to (200 to """{"ok":true,"clickId":"clk_new"}""")))
        val t = tracker(stub)
        val captured = t.handleDeepLink("https://referalli.com/go/tk_test/dana")!!
        t.reportVisit(captured)
        assertEquals("clk_new", t.referral!!.clickId)
        assertTrue(stub.body("/visit").contains("\"code\":\"dana\""))
    }

    @Test fun trackSignupIdentifiesThenAttributes() {
        val stub = Stub(mapOf(
            "/mobile/resolve" to (200 to """{"matched":true,"clickId":"clk_1","code":"dana","destination":"x"}"""),
            "/visit" to (200 to """{"ok":true,"clickId":"clk_1"}"""),
            "/identify" to (200 to """{"ok":true}"""),
            "/mobile/install" to (200 to """{"attributed":true,"referralId":"ref_1","partnerId":"ptr_1"}"""),
        ))
        val t = tracker(stub)
        assertEquals(ReferalliSignupResult(true, "ref_1", "ptr_1"), t.trackSignup("cus_\"42\""))
        assertTrue(stub.body("/identify").contains("\"customerRef\":\"cus_\\\"42\\\"\""))
        val install = stub.body("/mobile/install")
        assertTrue(install.contains("\"programId\":\"prg_1\""))
        assertTrue(install.contains("\"clickId\":\"clk_1\""))
        assertTrue(install.contains("\"deviceId\":\"${t.anonId}\""))
    }

    @Test fun trackSignupWithoutProgramOnlyIdentifies() {
        val stub = Stub(mapOf(
            "/mobile/resolve" to (200 to """{"matched":false}"""),
            "/identify" to (200 to """{"ok":true}"""),
        ))
        assertFalse(tracker(stub, programId = null).trackSignup("cus_1").attributed)
        assertEquals(0, stub.count("/mobile/install"))
    }

    @Test fun httpErrorsCarryTheStatus() {
        val stub = Stub(mapOf("/mobile/resolve" to (200 to """{"matched":false}"""), "/identify" to (400 to "{}")))
        val e = assertFailsWith<ReferalliTrackerException> { tracker(stub).trackSignup("cus_1") }
        assertEquals(400, e.status)
    }

    @Test fun anonIdIsStableAndResetClearsState() {
        val storage = InMemoryStorage()
        val stub = Stub(emptyMap())
        val a = ReferalliTracker("tk", storage = storage, transport = stub)
        val id = a.anonId
        assertEquals(id, ReferalliTracker("tk", storage = storage, transport = stub).anonId)
        a.handleDeepLink("https://x.example/?via=dana")
        a.reset()
        assertNull(a.referral)
        assertTrue(a.anonId != id)
    }
}
