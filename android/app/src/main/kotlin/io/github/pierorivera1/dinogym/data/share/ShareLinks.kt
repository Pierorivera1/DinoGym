package io.github.pierorivera1.dinogym.data.share

/**
 * Finds a shared link's id (task 23, decision 102): in an App Link the app was opened with,
 * or in text shared to the app or pasted ("Try PULL! https://d1x2.cloudfront.net/r/<id>").
 * Any host is accepted: the id is looked up on this build's own API, so a link of another
 * environment simply is not found.
 */
object ShareLinks {
    private val LINK = Regex("""https?://[A-Za-z0-9.-]+(?::\d+)?/r/([A-Za-z0-9_-]{22})(?![A-Za-z0-9_-])""")

    /** The id of the first shared link in [text], or null when it holds none. */
    fun idIn(text: String?): String? = text?.let { LINK.find(it)?.groupValues?.get(1) }
}
