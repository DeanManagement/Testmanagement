package com.deanmanagement.testmanagement.project.internal.access;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * Enforces {@link RequireProjectRole} on annotated controller methods. Resolves the project id from
 * the configured path variable and the caller from the security context, then delegates to
 * {@link ProjectAccessService}. Centralising the check here makes it impossible to silently forget
 * authorization on an annotated endpoint.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ProjectRoleAspect {

    private final ProjectAccessService access;

    @Before("@annotation(requireProjectRole)")
    public void enforce(RequireProjectRole requireProjectRole) {
        // Null means "no principal at all" (permit-all dev context) — the filter chain owns
        // authentication there. A principal that exists but does not resolve to a user throws
        // rather than falling through; see ProjectAccessService#resolvedCallerOrNull.
        UUID userId = access.resolvedCallerOrNull();
        if (userId == null) {
            return;
        }
        UUID projectId = resolveProjectId(requireProjectRole.pathVariable());
        if (projectId == null) {
            return;
        }
        access.requireRole(userId, projectId, requireProjectRole.value());
    }

    @SuppressWarnings("unchecked")
    private UUID resolveProjectId(String pathVariable) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object raw = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(raw instanceof Map<?, ?> vars)) {
            return null;
        }
        Object value = ((Map<String, String>) vars).get(pathVariable);
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }
}
