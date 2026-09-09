import kotlin.test.Test
import kotlin.test.assertEquals

class KdocUpdaterTest {

    @Test
    fun `update single line KDoc`() {

        val kdoc = """/** Hello world */ """
        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")

        assertEquals("""
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """.trimIndent(), result)
    }

    @Test
    fun `update multi line KDoc`() {
        val kdoc = """
            /**
             * Hello world
             */
        """.trimIndent()
        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")
        assertEquals("""
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """.trimIndent(), result)
    }

    @Test
    fun `update KDoc with param section`() {
        val kdoc = """
            /**
             * Hello world
             * @param a The first param
             */
        """.trimIndent()
        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")
        assertEquals("""
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """.trimIndent(), result)
    }

    @Test
    fun `update KDoc with param section separated with a blank line`() {
        val kdoc = """
            /**
             * Hello world
             *
             * @param a The first param
             */
        """.trimIndent()
        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")
        assertEquals("""
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """.trimIndent(), result)
    }

    @Test
    fun `remove feedback link from suppressed KDoc`() {
        val kdoc = """
            /**
             * @suppress **This is unstable API and it is subject to change.**
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """.trimIndent()

        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")

        assertEquals("""
            /**
             * @suppress **This is unstable API and it is subject to change.**
             */
        """.trimIndent(), result)
    }

    @Test
    fun `do not add leading blank line when KDoc starts with tag`() {
        val kdoc = """
            /**
             * @param value parameter description.
             */
        """.trimIndent()

        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")

        assertEquals("""
            /**
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param value parameter description.
             */
        """.trimIndent(), result)
    }

    @Test
    fun `move existing feedback link before KDoc tags without trailing blank line`() {
        val kdoc = """
            /**
             * Generates an EC key pair for OIDC tests.
             *
             * @param keyId key ID written to token headers.
             * @return generated test keys.
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """.trimIndent()

        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")

        assertEquals("""
            /**
             * Generates an EC key pair for OIDC tests.
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param keyId key ID written to token headers.
             * @return generated test keys.
             */
        """.trimIndent(), result)
    }

    @Test
    fun `do not treat inline annotation as KDoc tag`() {
        val kdoc = """
            /**
             * Apple's delegate is
             * declared `@property(nonatomic, weak)`.
             */
        """.trimIndent()

        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.c")

        assertEquals("""
            /**
             * Apple's delegate is
             * declared `@property(nonatomic, weak)`.
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """.trimIndent(), result)
    }

    @Test
    fun `remove feedback link inserted before inline annotation`() {
        val kdoc = """
            /**
             * Apple's delegate is
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * declared `@property(nonatomic, weak)`.
             */
        """.trimIndent()

        val result = removeFeedbackLinksFromKDoc(kdoc)

        assertEquals("""
            /**
             * Apple's delegate is
             * declared `@property(nonatomic, weak)`.
             */
        """.trimIndent(), result)
    }

    @Test
    fun `remove feedback link from KDoc`() {
        val kdoc = """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             *
             * @param a The first param
             */
        """.trimIndent()

        val result = removeFeedbackLinksFromKDoc(kdoc)

        assertEquals("""
            /**
             * Hello world
             *
             * @param a The first param
             */
        """.trimIndent(), result)
    }

    @Test
    fun `remove leading blank lines without feedback link`() {
        val kdoc = """
            /**
             *
             *
             * Hello world
             */
        """.trimIndent()

        val result = removeFeedbackLinksFromKDoc(kdoc)

        assertEquals("""
            /**
             * Hello world
             */
        """.trimIndent(), result)
    }

    @Test
    fun `remove trailing blank lines without feedback link`() {
        val kdoc = """
            /**
             * Hello world
             *
             *
             */
        """.trimIndent()

        val result = removeFeedbackLinksFromKDoc(kdoc)

        assertEquals("""
            /**
             * Hello world
             */
        """.trimIndent(), result)
    }

    @Test
    fun `remove trailing feedback link from KDoc`() {
        val kdoc = """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.c)
             */
        """.trimIndent()

        val result = removeFeedbackLinksFromKDoc(kdoc)

        assertEquals("""
            /**
             * Hello world
             */
        """.trimIndent(), result)
    }

    @Test
    fun `update KDoc existing link`() {
        val kdoc = """
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.foo)
             *
             * @param a The first param
             */
        """.trimIndent()
        val result = updateKDocWithLink(kdoc, "https://example.com", "a.b.bar")
        assertEquals("""
            /**
             * Hello world
             *
             * [Report a problem](https://example.com?fqname=a.b.bar)
             *
             * @param a The first param
             */
        """.trimIndent(), result)
    }
}

