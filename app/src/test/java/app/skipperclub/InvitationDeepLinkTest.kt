package app.skipperclub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvitationDeepLinkTest {

    @Test
    fun parsesInvitationCodeFromLocalizedRegisterPath() {
        assertEquals("ABC12345", parseInvitationCode("/pl/register", "ABC12345"))
    }

    @Test
    fun parsesInvitationCodeWithoutLocalePrefix() {
        assertEquals("ABC12345", parseInvitationCode("/register", "ABC12345"))
    }

    @Test
    fun returnsEmptyCodeForRegisterPathWithoutInvitationParam() {
        assertEquals("", parseInvitationCode("/pl/register", null))
    }

    @Test
    fun returnsEmptyCodeForRegisterPathWithBlankInvitationParam() {
        assertEquals("", parseInvitationCode("/pl/register", "  "))
    }

    @Test
    fun returnsNullForUnrelatedPath() {
        assertNull(parseInvitationCode("/pl/password-reset", "ABC12345"))
    }

    @Test
    fun returnsNullForNullPath() {
        assertNull(parseInvitationCode(null, "ABC12345"))
    }
}
