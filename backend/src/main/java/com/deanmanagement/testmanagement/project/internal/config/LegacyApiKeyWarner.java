package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PRD-021 §4.2: keys created before project scoping are global. They keep working for a
 * deprecation window but every start logs which ones should be re-created project-scoped.
 */
@Component
@RequiredArgsConstructor
public class LegacyApiKeyWarner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyApiKeyWarner.class);

    private final ApiKeyService apiKeyService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> globalKeys = apiKeyService.findGlobalKeyNames();
        if (!globalKeys.isEmpty()) {
            log.warn("DEPRECATED: {} global API key(s) without project scope: {}. "
                            + "Re-create them scoped to a project — global keys will be rejected "
                            + "in a future release.",
                    globalKeys.size(), globalKeys);
        }
    }
}
