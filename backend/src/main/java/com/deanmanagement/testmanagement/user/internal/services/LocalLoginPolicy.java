package com.deanmanagement.testmanagement.user.internal.services;

/**
 * Whether password sign-in is currently permitted instance-wide.
 *
 * <p>An interface rather than a direct dependency on the SSO module: authentication is the lower
 * layer and must keep working whether or not SSO is configured, so it asks a small question through
 * a seam instead of importing the feature that answers it.
 */
public interface LocalLoginPolicy {

    boolean isLocalLoginEnabled();
}
