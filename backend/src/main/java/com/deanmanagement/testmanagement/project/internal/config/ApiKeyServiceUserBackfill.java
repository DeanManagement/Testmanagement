package com.deanmanagement.testmanagement.project.internal.config;

import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * PRD-025 §3.2: gives every pre-existing project-scoped API key the service user and project
 * membership it now needs, defaulting to {@code TESTER} — the access those keys effectively had
 * before, when {@link com.deanmanagement.testmanagement.project.internal.access.ProjectRoleAspect}
 * failed open for them.
 *
 * <p>Done here rather than in V50 because inserting users means generating UUIDs, which has no
 * vendor-neutral spelling across PostgreSQL and H2. Idempotent: a second boot is a no-op.
 *
 * <p>Also carries forward PRD-021 §4.2's warning about legacy project-less keys, which now says
 * they are rejected rather than deprecated.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class ApiKeyServiceUserBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyServiceUserBackfill.class);

    private final ApiKeyService apiKeyService;

    @Value("${app.api-keys.allow-legacy-global:false}")
    private boolean allowLegacyGlobalKeys;

    @Override
    public void run(ApplicationArguments args) {
        backfillServiceUsers();
        warnAboutLegacyKeys();
    }

    private void backfillServiceUsers() {
        List<UUID> pending = apiKeyService.findKeyIdsWithoutServiceUser();
        if (pending.isEmpty()) {
            return;
        }
        int created = 0;
        for (UUID keyId : pending) {
            try {
                if (apiKeyService.ensureServiceUser(keyId)) {
                    created++;
                }
            } catch (RuntimeException e) {
                // One bad key must not stop the application from starting. The filter rejects any
                // project-scoped key without a service user, so this one fails closed.
                log.error("Could not create a service user for API key {} — it will be rejected "
                        + "until re-created: {}", keyId, e.getMessage());
            }
        }
        log.info("PRD-025: created {} service account(s) for {} pre-existing API key(s), "
                + "each granted TESTER on its project.", created, pending.size());
    }

    private void warnAboutLegacyKeys() {
        List<String> globalKeys = apiKeyService.findGlobalKeyNames();
        if (globalKeys.isEmpty()) {
            return;
        }
        if (allowLegacyGlobalKeys) {
            log.warn("{} API key(s) without project scope are still accepted because "
                            + "app.api-keys.allow-legacy-global=true: {}. They hold no project role, so "
                            + "role checks cannot apply to them. Re-create them scoped to a project.",
                    globalKeys.size(), globalKeys);
        } else {
            log.error("{} API key(s) without project scope will be REJECTED: {}. Re-create them "
                            + "from the admin settings, or set app.api-keys.allow-legacy-global=true "
                            + "to keep them working while you migrate.",
                    globalKeys.size(), globalKeys);
        }
    }
}
