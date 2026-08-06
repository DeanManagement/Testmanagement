package com.deanmanagement.testmanagement.user.internal.sso;

import java.net.URI;

/**
 * Where GitHub's OAuth endpoints live for a given instance.
 *
 * <p>GitHub publishes no discovery document, so these are derived rather than fetched. The one
 * asymmetry worth knowing: on github.com the API is a <em>separate host</em>
 * ({@code api.github.com}), while GitHub Enterprise Server serves it from the same host under
 * {@code /api/v3}. Assuming either shape universally breaks the other deployment, and the failure
 * is a 404 during the callback — after the user has already approved the app, which makes it look
 * like a permissions problem rather than a URL one.
 *
 * @param authorizationUri where the browser is sent to approve
 * @param tokenUri         where the code is exchanged
 * @param userInfoUri      {@code /user}: login, numeric id, display name
 * @param userEmailsUri    {@code /user/emails}: the verified addresses {@code /user} omits
 */
public record GitHubEndpoints(String authorizationUri, String tokenUri,
                              String userInfoUri, String userEmailsUri) {

    private static final String DOT_COM_HOST = "github.com";
    private static final String DOT_COM_API = "https://api.github.com";

    /**
     * @param baseUrl the instance root as the admin configured it, e.g. {@code https://github.com}
     *                or {@code https://ghe.example.com}
     */
    public static GitHubEndpoints forBaseUrl(String baseUrl) {
        String root = trimTrailingSlash(baseUrl.trim());
        String api = isDotCom(root) ? DOT_COM_API : root + "/api/v3";
        return new GitHubEndpoints(
                root + "/login/oauth/authorize",
                root + "/login/oauth/access_token",
                api + "/user",
                api + "/user/emails");
    }

    /**
     * Matches on the parsed host rather than the string, so {@code https://github.com.evil.test}
     * is not mistaken for github.com and pointed at the real API.
     */
    private static boolean isDotCom(String root) {
        try {
            String host = URI.create(root).getHost();
            return host != null
                    && (host.equalsIgnoreCase(DOT_COM_HOST) || host.equalsIgnoreCase("www." + DOT_COM_HOST));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
