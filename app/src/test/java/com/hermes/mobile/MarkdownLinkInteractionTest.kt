package com.hermes.mobile

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import com.hermes.mobile.data.safeExternalUrl
import com.hermes.mobile.ui.LINK_TAG
import com.hermes.mobile.ui.interactiveLinks
import com.hermes.mobile.ui.markdownLinkDestination
import com.hermes.mobile.ui.trimBareLink
import org.junit.Assert.*
import org.junit.Test

class MarkdownLinkInteractionTest {
    @Test fun nativeLinkInvokesTheSuppliedAction() {
        var opened: String? = null
        val text = buildAnnotatedString {
            append("Belgeler")
            addStringAnnotation(LINK_TAG, "https://example.com/docs", 0, length)
        }
        val link = interactiveLinks(text) { opened = it }.getLinkAnnotations(0, text.length).single().item
        assertEquals("https://example.com/docs", (link as LinkAnnotation.Url).url)
        link.linkInteractionListener!!.onClick(link)
        assertEquals(link.url, opened)
    }

    @Test fun unsafeAndEmptyLinksNeverGetAnAction() {
        listOf("javascript:alert(1)", "https://", "https://user:pass@example.com", "https://exa mple.com").forEach { url ->
            val text = buildAnnotatedString { append("link"); addStringAnnotation(LINK_TAG, url, 0, length) }
            assertTrue(url, interactiveLinks(text) { fail("must not open") }.getLinkAnnotations(0, 4).isEmpty())
            assertNull(url, safeExternalUrl(url))
        }
    }

    @Test fun markdownPreservesParenthesizedUrlsAndOptionalTitles() {
        val target = "https://example.com/wiki/Android_(platform)"
        assertEquals(target, markdownLinkDestination("($target)", 0)!!.second)
        assertEquals(target, markdownLinkDestination("(<$target> \"Android\")", 0)!!.second)
        assertEquals(target, markdownLinkDestination("(https://example.com/wiki/Android_\\(platform\\))", 0)!!.second)
        assertNull(markdownLinkDestination("(https://example.com/incomplete", 0))
        assertNull(markdownLinkDestination("(https://example.com invalid title)", 0))
    }

    @Test fun sentencePunctuationDoesNotBreakBareUrls() {
        assertEquals("https://example.com/wiki/Android_(platform)", trimBareLink("https://example.com/wiki/Android_(platform))."))
        assertEquals("https://example.com/file?id=1", trimBareLink("https://example.com/file?id=1,"))
    }
}
