package app.skipperclub.ui.main.messages

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The composer's length cap must count what the server counts. The API validates message text in
 * Unicode code points (runes), while Kotlin's `String.take` counts UTF-16 units — so an emoji costs
 * two toward the client's budget and one toward the server's, and a cut landing between a surrogate
 * pair produces a lone surrogate: `�` on screen, rejected on the wire.
 */
class MessageTruncationTest {

    @Test
    fun textWithinTheLimitIsUnchanged() {
        assertEquals("Ahoy", truncateToCodePoints("Ahoy", 10))
        assertEquals("Ahoy", truncateToCodePoints("Ahoy", 4))
    }

    @Test
    fun plainTextIsTruncatedToTheLimit() {
        assertEquals("Ahoy", truncateToCodePoints("Ahoy captain", 4))
    }

    @Test
    fun emojiCountAsOneCodePointEachNotTwo() {
        // "⛵⛵⛵" as surrogate pairs: 3 code points but 6 UTF-16 units. `take(3)` would cut it to
        // one and a half sailboats; the server would have accepted all three.
        val sails = "🚤🚤🚤"
        assertEquals(sails, truncateToCodePoints(sails, 3))
    }

    @Test
    fun truncationNeverSplitsASurrogatePair() {
        val sails = "🚤🚤🚤"
        val truncated = truncateToCodePoints(sails, 2)

        assertEquals("🚤🚤", truncated)
        assertEquals(2, truncated.codePointCount(0, truncated.length))
        assertEquals(
            "no dangling high surrogate",
            0,
            truncated.count { it.isHighSurrogate() && !it.isLowSurrogate() } % 2,
        )
    }

    @Test
    fun truncationAtTheBoundaryOfMixedTextKeepsTheEmojiIntact() {
        // 4 code points: 'H', 'i', ' ', sailboat. Cutting to 4 must keep the whole sailboat.
        val text = "Hi 🚤"
        assertEquals(text, truncateToCodePoints(text, 4))
        assertEquals("Hi ", truncateToCodePoints(text, 3))
    }
}
