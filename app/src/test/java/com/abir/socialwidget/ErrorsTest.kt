package com.abir.socialwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorsTest {
    @Test fun no_connection_becomes_plain_text() {
        assertEquals(NO_INTERNET, friendlyError(UnknownHostException("graph.facebook.com"), "x"))
        assertEquals(NO_INTERNET, friendlyError(ConnectException("failed to connect"), "x"))
        assertEquals(NO_INTERNET, friendlyError(SocketTimeoutException("timeout"), "x"))
        assertEquals(NO_INTERNET, friendlyError(NoRouteToHostException("no route"), "x"))
    }

    @Test fun real_service_errors_keep_their_own_message() {
        assertEquals("YouTube channel not found", friendlyError(IOException("YouTube channel not found"), "x"))
        assertEquals("Error validating access token", friendlyError(IOException("Error validating access token"), "x"))
    }

    @Test fun missing_message_uses_the_fallback() {
        assertEquals("Facebook error", friendlyError(IOException(), "Facebook error"))
        assertEquals("Facebook error", friendlyError(IOException("  "), "Facebook error"))
    }

    @Test fun only_the_no_connection_text_counts_as_offline() {
        assertTrue(isOfflineError(NO_INTERNET))
        assertFalse(isOfflineError("YouTube channel not found"))
        assertFalse(isOfflineError(""))
    }
}
