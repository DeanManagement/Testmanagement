package com.deanmanagement.testmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No two handler methods may answer for the same request.
 *
 * <p>Spring does not detect this at startup — it builds the mapping table happily and only throws
 * {@code IllegalStateException: Ambiguous handler methods mapped for ...} when a request actually
 * arrives. So a duplicate endpoint starts clean, passes every service-level test, and then returns
 * 500 for one screen in production. That is exactly what happened to the project dashboard: it was
 * mapped by both {@code DashboardController} and a leftover method on {@code ProjectController},
 * and no test noticed because the tests exercised {@code DashboardService} rather than the URL.
 *
 * <p>Path variable <em>names</em> are normalised away before comparing, because that is what made
 * the collision invisible to the eye: {@code /api/projects/{projectId}/dashboard} and
 * {@code /api/projects/{id}/dashboard} are different strings and the same endpoint.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AmbiguousMappingTest {

    /**
     * By name: actuator contributes a second {@code RequestMappingHandlerMapping} of its own, and
     * its endpoints are not what this is guarding.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void noTwoHandlersAnswerForTheSameRequest() {
        Map<String, Set<String>> handlersByRequest = new LinkedHashMap<>();

        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            for (String key : requestKeys(info)) {
                handlersByRequest
                        .computeIfAbsent(key, k -> new TreeSet<>())
                        .add(method.getBeanType().getSimpleName() + "#" + method.getMethod().getName());
            }
        });

        Map<String, Set<String>> ambiguous = new LinkedHashMap<>();
        handlersByRequest.forEach((key, handlers) -> {
            if (handlers.size() > 1) {
                ambiguous.put(key, handlers);
            }
        });

        assertThat(ambiguous)
                .as("These requests map to more than one handler. Spring only fails on them when a "
                        + "request arrives, so each one is a 500 waiting for whoever opens that screen.")
                .isEmpty();
    }

    /**
     * One key per (path, method, content-type conditions) combination this mapping answers.
     *
     * <p>The conditions are part of the key because differing on them is legitimate: two handlers
     * for the same path and verb that consume different media types are content negotiation, not a
     * collision.
     */
    private static List<String> requestKeys(RequestMappingInfo info) {
        Set<String> patterns = info.getPathPatternsCondition() == null
                ? Set.of()
                : info.getPathPatternsCondition().getPatternValues();

        Set<String> methods = info.getMethodsCondition().getMethods().isEmpty()
                ? Set.of("ANY")
                : info.getMethodsCondition().getMethods().stream().map(Enum::name)
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        String conditions = info.getConsumesCondition() + "|" + info.getProducesCondition()
                + "|" + info.getParamsCondition() + "|" + info.getHeadersCondition();

        return patterns.stream()
                .map(AmbiguousMappingTest::normalisePathVariables)
                .flatMap(pattern -> methods.stream().map(m -> m + " " + pattern + " " + conditions))
                .toList();
    }

    /** {@code /api/projects/{projectId}/dashboard} and {@code .../{id}/dashboard} are one URL. */
    private static String normalisePathVariables(String pattern) {
        return pattern.replaceAll("\\{[^}]*}", "{}");
    }
}
