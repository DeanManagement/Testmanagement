package com.deanmanagement.testmanagement.project.internal.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers {@link WebhookEvent}s asynchronously, only after the originating transaction commits, so
 * a rolled-back operation never fires a webhook and a slow endpoint never blocks the request thread.
 */
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookDispatchService dispatchService;

    @Async("webhookExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWebhookEvent(WebhookEvent event) {
        dispatchService.dispatch(event.type(), event.projectId(), event.data());
    }
}
