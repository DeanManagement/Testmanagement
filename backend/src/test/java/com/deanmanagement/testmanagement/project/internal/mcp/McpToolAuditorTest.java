package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TM41: what an agent sees when a tool fails. */
class McpToolAuditorTest {

    private final McpInvocationRecorder recorder = mock(McpInvocationRecorder.class);
    private final McpToolAuditor auditor = new McpToolAuditor(recorder, mock(McpCallerContext.class));
    private final ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    private McpTool tool;

    @McpTool(name = "sample_tool", description = "A tool for this test")
    void sampleTool() {
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        var method = McpToolAuditorTest.class.getDeclaredMethod("sampleTool");
        tool = method.getAnnotation(McpTool.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
    }

    @Test
    void anInternalErrorReachesTheAgentAsAGenericMessage() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("org.hibernate.TransientPropertyValueException"));

        assertThatThrownBy(() -> auditor.audit(joinPoint, tool))
                .hasMessage(McpToolAuditor.INTERNAL_ERROR_MESSAGE);
    }

    @Test
    void anInternalErrorIsRecordedWithItsRealMessage() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("org.hibernate.TransientPropertyValueException"));

        assertThatThrownBy(() -> auditor.audit(joinPoint, tool));

        verify(recorder).record(any(), eq("sample_tool"), any(), eq("ERROR"),
                contains("TransientPropertyValueException"), any(), any(), any(Long.class));
    }

    @Test
    void aDomainErrorKeepsItsMessage() throws Throwable {
        ResourceNotFoundException notFound = new ResourceNotFoundException("TestCase", UUID.randomUUID());
        when(joinPoint.proceed()).thenThrow(notFound);

        assertThatThrownBy(() -> auditor.audit(joinPoint, tool)).isSameAs(notFound);
    }

    @Test
    void aRefusalKeepsItsMessage() throws Throwable {
        McpToolException refusal = new McpToolException("Pass either testCaseId or resultId.");
        when(joinPoint.proceed()).thenThrow(refusal);

        assertThatThrownBy(() -> auditor.audit(joinPoint, tool)).isSameAs(refusal);
    }
}
