package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.WebhookDelivery;
import com.deanmanagement.testmanagement.project.internal.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodically re-attempts pending webhook deliveries whose backoff window has elapsed. Persisting
 * the queue in the DB (rather than in-memory) means retries survive restarts.
 */
@Component
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryScheduler.class);
    private static final int BATCH_SIZE = 50;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDispatchService dispatchService;

    @Scheduled(fixedDelayString = "${app.webhooks.retry-poll-ms:30000}")
    public void retryDueDeliveries() {
        List<WebhookDelivery> due = deliveryRepository
                .findBySuccessIsNullAndNextAttemptAtLessThanEqual(Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) {
            return;
        }
        log.debug("Retrying {} due webhook deliveries", due.size());
        for (WebhookDelivery delivery : due) {
            try {
                dispatchService.deliver(delivery.getId());
            } catch (Exception e) {
                log.warn("Webhook retry failed for delivery {}: {}", delivery.getId(), e.getMessage());
            }
        }
    }
}
