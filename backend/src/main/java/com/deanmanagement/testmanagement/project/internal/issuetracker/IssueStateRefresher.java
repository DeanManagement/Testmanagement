package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueLink;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.repository.IssueLinkRepository;
import com.deanmanagement.testmanagement.project.internal.service.IssueTrackerConfigService;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Refreshes one project's stale issue links inside a transaction.
 *
 * <p>Separate from {@link IssueStatePoller} on purpose: {@code @Transactional} is applied by a
 * proxy, so a scheduled method calling a transactional method on {@code this} would silently run
 * without a transaction. Crossing a bean boundary is what makes the annotation take effect.
 */
@Component
@RequiredArgsConstructor
public class IssueStateRefresher {

    private static final Logger log = LoggerFactory.getLogger(IssueStateRefresher.class);
    private static final int MAX_TITLE_LENGTH = 500;

    private final IssueLinkRepository issueLinkRepository;
    private final IssueTrackerConfigService configService;
    private final IssueTrackerProviderRegistry providerRegistry;
    private final IssueTrackerProperties properties;

    /**
     * @return number of links refreshed; 0 means there was nothing stale or the provider failed on
     *         the first call.
     */
    @Transactional
    public int refreshProject(IssueTrackerConfig config) {
        Instant staleBefore = Instant.now().minusMillis(properties.pollMinAgeMs());
        List<IssueLink> links = issueLinkRepository.findStaleForActiveRuns(
                config.getProjectId(), staleBefore, PageRequest.of(0, properties.pollBatchSize()));
        if (links.isEmpty()) {
            return 0;
        }

        IssueTrackerProvider provider = providerRegistry.require(config.getProvider());
        IssueTrackerProvider.DecryptedConfig decrypted = configService.decrypt(config);
        int refreshed = 0;

        for (IssueLink link : links) {
            if (link.getProvider() != config.getProvider()) {
                // Left over from a previous provider; its state can no longer be resolved.
                continue;
            }
            try {
                Issue issue = provider.get(decrypted, link.getExternalId());
                link.setState(issue.state());
                if (issue.title() != null) {
                    link.setTitle(truncate(issue.title()));
                }
                link.setStateCheckedAt(Instant.now());
                issueLinkRepository.save(link);
                refreshed++;
            } catch (UpstreamServiceException e) {
                // Auth failure or rate limiting affects every link on this project equally, so the
                // batch stops here instead of repeating the same rejected call dozens of times.
                configService.recordError(config, e.getMessage());
                log.warn("Stopping issue poll for project {} after provider error: {}",
                        config.getProjectId(), e.getMessage());
                return refreshed;
            } catch (RuntimeException e) {
                // A single unresolvable link (bad reference, deleted issue) should not stop the batch;
                // stamping it as checked stops it monopolising the oldest-first ordering.
                log.warn("Could not refresh issue link {}: {}", link.getId(), e.getMessage());
                link.setStateCheckedAt(Instant.now());
                issueLinkRepository.save(link);
            }
        }
        return refreshed;
    }

    private static String truncate(String title) {
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
    }
}
