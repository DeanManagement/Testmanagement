package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the adapter for a configured build server. Adding a provider means adding one
 * {@link BuildServerProvider} bean — nothing here or in the service layer changes.
 */
@Component
public class BuildServerProviderRegistry {

    private final Map<BuildServerProviderType, BuildServerProvider> providers =
            new EnumMap<>(BuildServerProviderType.class);

    public BuildServerProviderRegistry(List<BuildServerProvider> discovered) {
        for (BuildServerProvider provider : discovered) {
            providers.put(provider.type(), provider);
        }
    }

    /** Providers with a working adapter, for the config form's provider dropdown. */
    public Set<BuildServerProviderType> supported() {
        return providers.keySet();
    }

    public BuildServerProvider require(BuildServerProviderType type) {
        BuildServerProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No build server adapter available for " + type);
        }
        return provider;
    }
}
