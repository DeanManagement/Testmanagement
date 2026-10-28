package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test-case attachments over MCP (TM13): base64 both ways, AttachmentService's rules in between. */
class McpTestCaseAttachmentToolsApiTest extends McpToolApiTestSupport {

    @Autowired private TestCaseAttachmentTools attachmentTools;
    @Autowired private AttachmentService attachmentService;

    @Test
    void anUploadedFileComesBackByteForByte() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        byte[] csv = "user,password\nadmin,secret\n".getBytes(StandardCharsets.UTF_8);

        McpDtos.Attachment stored = attachmentTools.addTestCaseAttachment(login.key(), "users.csv", "text/csv",
                Base64.getEncoder().encodeToString(csv));
        McpDtos.AttachmentContent fetched = attachmentTools.downloadTestCaseAttachment(login.key(), stored.id());

        assertThat(Base64.getDecoder().decode(fetched.contentBase64())).isEqualTo(csv);
        assertThat(testCaseTools.getTestCase(login.key()).attachments())
                .extracting(McpDtos.Attachment::id).containsExactly(stored.id());
    }

    @Test
    void bytesThatDoNotMatchTheTypeAreRefused() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        String html = Base64.getEncoder().encodeToString("<html><script>x</script>".getBytes());

        assertThatThrownBy(() -> attachmentTools.addTestCaseAttachment(login.key(), "x.png", "image/png", html))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFileOverTheDownloadCapIsRefused() {
        UUID userId = authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        byte[] big = new byte[(int) TestCaseAttachmentTools.MAX_DOWNLOAD_BYTES + 1];
        Arrays.fill(big, (byte) 'a');
        UUID id = attachmentService.upload(project.getId(), login.id(), "big.txt", "text/plain", big, userId).id();

        assertThatThrownBy(() -> attachmentTools.downloadTestCaseAttachment(login.key(), id))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void aDeletedAttachmentIsGone() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Login");
        McpDtos.Attachment stored = attachmentTools.addTestCaseAttachment(login.key(), "notes.txt", "text/plain",
                Base64.getEncoder().encodeToString("hello".getBytes()));

        attachmentTools.deleteTestCaseAttachment(login.key(), stored.id());

        assertThat(testCaseTools.getTestCase(login.key()).attachments()).isEmpty();
    }
}
