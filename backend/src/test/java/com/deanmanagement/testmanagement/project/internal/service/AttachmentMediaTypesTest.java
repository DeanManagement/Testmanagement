package com.deanmanagement.testmanagement.project.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PRD-044 §3.2: the declared type is a claim the first bytes have to back up. */
class AttachmentMediaTypesTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] GIF = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};
    private static final byte[] TEXT = "id,name\n1,Alice\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsEachAllowedTypeWithMatchingBytes() {
        assertThat(AttachmentMediaTypes.verify("image/png", "a.png", PNG)).isEqualTo("image/png");
        assertThat(AttachmentMediaTypes.verify("image/jpeg", "a.jpg", JPEG)).isEqualTo("image/jpeg");
        assertThat(AttachmentMediaTypes.verify("image/gif", "a.gif", GIF)).isEqualTo("image/gif");
        assertThat(AttachmentMediaTypes.verify("image/webp", "a.webp", WEBP)).isEqualTo("image/webp");
        assertThat(AttachmentMediaTypes.verify("application/pdf", "a.pdf", PDF)).isEqualTo("application/pdf");
        assertThat(AttachmentMediaTypes.verify("application/zip", "a.zip", ZIP)).isEqualTo("application/zip");
        assertThat(AttachmentMediaTypes.verify(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "a.xlsx", ZIP))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(AttachmentMediaTypes.verify("text/csv; charset=UTF-8", "a.csv", TEXT)).isEqualTo("text/csv");
        assertThat(AttachmentMediaTypes.verify("application/json", "a.json", "{}".getBytes())).isEqualTo("application/json");
    }

    @Test
    void rejectsAPngDeclaredAsPdf() {
        assertThatThrownBy(() -> AttachmentMediaTypes.verify("application/pdf", "a.pdf", PNG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsHtmlDeclaredAsPdf() {
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> AttachmentMediaTypes.verify("application/pdf", "a.pdf", html))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "text/html", "application/xhtml+xml", "text/javascript",
            "application/javascript", "application/x-msdownload"})
    void rejectsScriptableAndUnknownTypes(String type) {
        assertThatThrownBy(() -> AttachmentMediaTypes.verify(type, "a.bin", TEXT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTextWithNulBytes() {
        assertThatThrownBy(() -> AttachmentMediaTypes.verify("text/plain", "a.txt", new byte[]{'a', 0, 'b'}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"data.csv,text/csv", "data.json,application/json", "spec.PDF,application/pdf"})
    void resolvesOctetStreamFromTheExtension(String fileName, String expected) {
        byte[] data = expected.equals("application/pdf") ? PDF : TEXT;
        assertThat(AttachmentMediaTypes.verify("application/octet-stream", fileName, data)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"shot.png", "shot.svg", "page.html", "noextension"})
    void octetStreamCannotBecomeAnImageOrScript(String fileName) {
        assertThatThrownBy(() -> AttachmentMediaTypes.verify("application/octet-stream", fileName, PNG))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
