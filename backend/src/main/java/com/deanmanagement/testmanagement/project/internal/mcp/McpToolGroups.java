package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Which {@link McpToolGroup} each advertised tool belongs to, read from {@link InToolGroup}. */
@Component
public class McpToolGroups implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final Map<String, McpToolGroup> groupByToolName = new HashMap<>();

    McpToolGroups(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** After every singleton exists, so looking the tool beans up cannot instantiate them early. */
    @Override
    public void afterSingletonsInstantiated() {
        for (Object bean : applicationContext.getBeansWithAnnotation(InToolGroup.class).values()) {
            for (Method method : AopUtils.getTargetClass(bean).getDeclaredMethods()) {
                McpTool tool = method.getAnnotation(McpTool.class);
                if (tool != null) {
                    groupOf(method).ifPresent(group -> groupByToolName.put(tool.name(), group));
                }
            }
        }
    }

    /** Empty for a tool nobody placed in a group; callers must treat that as "restricted keys no". */
    Optional<McpToolGroup> groupOf(String toolName) {
        return Optional.ofNullable(groupByToolName.get(toolName));
    }

    static Optional<McpToolGroup> groupOf(Method toolMethod) {
        InToolGroup onMethod = AnnotationUtils.findAnnotation(toolMethod, InToolGroup.class);
        if (onMethod != null) {
            return Optional.of(onMethod.value());
        }
        InToolGroup onClass =
                AnnotationUtils.findAnnotation(toolMethod.getDeclaringClass(), InToolGroup.class);
        return Optional.ofNullable(onClass).map(InToolGroup::value);
    }
}
