package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the adapter for a configured provider. Adding a tracker means adding one
 * {@link IssueTrackerProvider} bean — nothing here or in the service layer changes.
 */
@Component
public class IssueTrackerProviderRegistry {

    private final Map<IssueTrackerProviderType, IssueTrackerProvider> providers =
            new EnumMap<>(IssueTrackerProviderType.class);

    public IssueTrackerProviderRegistry(List<IssueTrackerProvider> discovered) {
        for (IssueTrackerProvider provider : discovered) {
            providers.put(provider.type(), provider);
        }
    }

    /** Providers with a working adapter, for the config form's provider dropdown. */
    public Set<IssueTrackerProviderType> supported() {
        return providers.keySet();
    }

    public IssueTrackerProvider require(IssueTrackerProviderType type) {
        IssueTrackerProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No issue tracker adapter available for " + type);
        }
        return provider;
    }
}
