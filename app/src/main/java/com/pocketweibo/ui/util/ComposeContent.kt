package com.pocketweibo.ui.util

/** Maximum number of characters allowed in a post body. */
const val COMPOSE_MAX_CHARS = 2000

/**
 * Clips [text] to [COMPOSE_MAX_CHARS].
 *
 * Used by the compose editor so a large clipboard paste keeps the maximum number of
 * characters that fit instead of rejecting the whole edit.
 */
fun clipComposeText(text: String): String = text.take(COMPOSE_MAX_CHARS)
