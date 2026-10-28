package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TM19: tracker HTML becomes plain text with its line structure, and no markup survives. */
class HtmlToTextTest {

    @Test
    void paragraphsBreaksAndListItemsBecomeLines() {
        assertThat(HtmlToText.convert("<div>First<br>second</div><p>Para</p><ol><li>one</li><li>two</li></ol>"))
                .isEqualTo("First\nsecond\n\nPara\n\n- one\n- two");
    }

    @Test
    void scriptsStylesAndCommentsAreDroppedWithTheirContent() {
        assertThat(HtmlToText.convert("<p>ok</p><script>alert(1)</script><style>p{}</style><!-- note -->"))
                .isEqualTo("ok");
    }

    @Test
    void tagsGoAndAttributesWithThem() {
        assertThat(HtmlToText.convert("<a href=\"javascript:x\" onclick=\"y\">link</a> <img src=x onerror=z>"))
                .isEqualTo("link");
    }

    @Test
    void entitiesAreDecodedOnceSoEscapedMarkupStaysText() {
        assertThat(HtmlToText.convert("a &lt;b&gt; &amp;lt; &#65;&#x42;&nbsp;c &unknown;"))
                .isEqualTo("a <b> &lt; AB c &unknown;");
    }

    @Test
    void sourceLineBreaksAndRepeatedSpacesCollapse() {
        assertThat(HtmlToText.convert("  one\n   two  ")).isEqualTo("one two");
    }

    @Test
    void blankOrEmptyMarkupIsNull() {
        assertThat(HtmlToText.convert(null)).isNull();
        assertThat(HtmlToText.convert("<p> </p><br>")).isNull();
    }
}
