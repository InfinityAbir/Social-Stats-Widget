package com.abir.socialwidget

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Shown instead of a raw network exception when the phone simply has no connection. */
const val NO_INTERNET = "No internet connection"

/**
 * A message a creator can understand. A missing connection is not a problem with their keys, so it
 * gets its own plain text (and the app treats it as temporary); everything else keeps the service's
 * own explanation, such as "YouTube channel not found" or an expired-token message from Meta.
 */
fun friendlyError(e: Throwable, fallback: String): String = when (e) {
    is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> NO_INTERNET
    else -> e.message?.takeIf { it.isNotBlank() } ?: fallback
}

/** True for the temporary "no connection" state, which should not alarm anyone or trigger alerts. */
fun isOfflineError(error: String): Boolean = error == NO_INTERNET
