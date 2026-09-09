import kotlin.test.Test
import kotlin.test.assertEquals

class KdocUpdaterTest {
    @Test
    fun `update single line KDoc`() {
        "/** Hello world */" shouldBeUpdatedTo """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """
    }

    @Test
    fun `update multi line KDoc`() {
        """
            /**
             * Hello world
             */
        """ shouldBeUpdatedTo """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """
    }

    @Test
    fun `update KDoc with param section`() {
        """
            /**
             * Hello world
             * @param a The first param
             */
        """ shouldBeUpdatedTo """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """
    }

    @Test
    fun `update KDoc with param section separated with a blank line`() {
        """
            /**
             * Hello world
             *
             * @param a The first param
             */
        """ shouldBeUpdatedTo """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """
    }

    @Test
    fun `remove feedback link from suppressed KDoc`() {
        """
            /**
             * @suppress **This is unstable API and it is subject to change.**
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """ shouldBeUpdatedTo """
            /**
             * @suppress **This is unstable API and it is subject to change.**
             */
        """
    }

    @Test
    fun `do not add leading blank line when KDoc starts with tag`() {
        """
            /**
             * @param value parameter description.
             */
        """ shouldBeUpdatedTo """
            /**
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param value parameter description.
             */
        """
    }

    @Test
    fun `move existing feedback link before KDoc tags without trailing blank line`() {
        """
            /**
             * Generates an EC key pair for OIDC tests.
             *
             * @param keyId key ID written to token headers.
             * @return generated test keys.
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """ shouldBeUpdatedTo """
            /**
             * Generates an EC key pair for OIDC tests.
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param keyId key ID written to token headers.
             * @return generated test keys.
             */
        """
    }

    @Test
    fun `do not treat inline annotation as KDoc tag`() {
        """
            /**
             * Apple's delegate is
             * declared `@property(nonatomic, weak)`.
             */
        """ shouldBeUpdatedTo """
            /**
             * Apple's delegate is
             * declared `@property(nonatomic, weak)`.
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """
    }

    @Test
    fun `update KDoc existing link`() {
        """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.foo)
             *
             * @param a The first param
             */
        """ shouldBeUpdatedTo """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """
    }

    @Test
    fun `remove feedback link from KDoc`() {
        """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """ shouldBeSanitizedTo """
            /**
             * Hello world
             *
             * @param a The first param
             */
        """
    }

    @Test
    fun `remove leading blank lines without feedback link`() {
        """
            /**
             *
             *
             * Hello world
             */
        """ shouldBeSanitizedTo """
            /**
             * Hello world
             */
        """
    }

    @Test
    fun `remove trailing blank lines without feedback link`() {
        """
            /**
             * Hello world
             *
             *
             */
        """ shouldBeSanitizedTo """
            /**
             * Hello world
             */
        """
    }

    @Test
    fun `remove trailing feedback link from KDoc`() {
        """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """ shouldBeSanitizedTo """
            /**
             * Hello world
             */
        """
    }

    private infix fun String.shouldBeUpdatedTo(expected: String) {
        val result = updateKDocWithLink(trimIndent(), TEST_FEEDBACK_LINK, TEST_FULLY_QUALIFIED_NAME)
        assertEquals(expected.trimIndent(), result)
    }

    private infix fun String.shouldBeSanitizedTo(expected: String) {
        val result = removeFeedbackLinksFromKDoc(trimIndent())
        assertEquals(expected.trimIndent(), result)
    }
}

private const val TEST_FEEDBACK_LINK = "https://example.com"
private const val TEST_FULLY_QUALIFIED_NAME = "a.b.c"
