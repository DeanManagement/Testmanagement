package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Places a tool class's {@code @McpTool} methods in the group an API key must hold to use them
 * (PRD-027 §9). On a method it overrides the class, for the odd tool that belongs elsewhere.
 *
 * <p>Required on every tool class: {@link McpToolGroups} finds tools by it, and a tool it cannot
 * place is hidden from — and refused to — every restricted key, which is the safe way to be wrong.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@interface InToolGroup {

    McpToolGroup value();
}
