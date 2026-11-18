package io.github.pierorivera1.dinogym.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinksTest {
    private val id = "q3V9xY_-AbCdEfGhIjKlMn"

    @Test
    fun findsTheIdInALink() {
        assertEquals(id, ShareLinks.idIn("https://d1x2y3z4.cloudfront.net/r/$id"))
        assertEquals(id, ShareLinks.idIn("https://d1x2y3z4.cloudfront.net/r/$id/"))
    }

    @Test
    fun findsTheIdInSharedText() {
        assertEquals(id, ShareLinks.idIn("PULL on DinoGym: https://d1x2y3z4.cloudfront.net/r/$id\nTry it!"))
        assertEquals(id, ShareLinks.idIn("Mira esto (https://d1.cloudfront.net/r/$id)."))
    }

    @Test
    fun refusesWhatIsNotALink() {
        assertNull(ShareLinks.idIn(null))
        assertNull(ShareLinks.idIn(""))
        assertNull(ShareLinks.idIn("just text"))
        assertNull(ShareLinks.idIn(id))
        // Too short, too long, other characters, another path.
        assertNull(ShareLinks.idIn("https://d1.cloudfront.net/r/short"))
        assertNull(ShareLinks.idIn("https://d1.cloudfront.net/r/${id}X"))
        assertNull(ShareLinks.idIn("https://d1.cloudfront.net/x/$id"))
        assertNull(ShareLinks.idIn("ftp://d1.cloudfront.net/r/$id"))
    }
}
