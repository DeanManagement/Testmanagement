package com.deanmanagement.testmanagement.user.internal.sso;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitHub publishes no discovery document, so these URLs are guessed from a base address rather than
 * fetched. The guess has to be right: it is only exercised during a live login, after the user has
 * already approved the app, where a wrong path looks like a permissions failure.
 */
class GitHubEndpointsTest {

    @Test
    void dotCom_usesTheSeparateApiHost() {
        GitHubEndpoints endpoints = GitHubEndpoints.forBaseUrl("https://github.com");

        assertThat(endpoints.authorizationUri()).isEqualTo("https://github.com/login/oauth/authorize");
        assertThat(endpoints.tokenUri()).isEqualTo("https://github.com/login/oauth/access_token");
        assertThat(endpoints.userInfoUri()).isEqualTo("https://api.github.com/user");
        assertThat(endpoints.userEmailsUri()).isEqualTo("https://api.github.com/user/emails");
    }

    @Test
    void enterpriseServer_servesTheApiFromItsOwnHostUnderApiV3() {
        GitHubEndpoints endpoints = GitHubEndpoints.forBaseUrl("https://ghe.example.com");

        assertThat(endpoints.authorizationUri()).isEqualTo("https://ghe.example.com/login/oauth/authorize");
        assertThat(endpoints.tokenUri()).isEqualTo("https://ghe.example.com/login/oauth/access_token");
        assertThat(endpoints.userInfoUri()).isEqualTo("https://ghe.example.com/api/v3/user");
        assertThat(endpoints.userEmailsUri()).isEqualTo("https://ghe.example.com/api/v3/user/emails");
    }

    @Test
    void trailingSlashDoesNotDoubleUp() {
        assertThat(GitHubEndpoints.forBaseUrl("https://ghe.example.com/").userInfoUri())
                .isEqualTo("https://ghe.example.com/api/v3/user");
    }

    @Test
    void aLookalikeHostIsNotTreatedAsDotCom() {
        // Matching on the raw string would send an access token minted by github.com.evil.test
        // to the real api.github.com, and the identity it returned would belong to someone else.
        GitHubEndpoints endpoints = GitHubEndpoints.forBaseUrl("https://github.com.evil.test");

        assertThat(endpoints.userInfoUri()).isEqualTo("https://github.com.evil.test/api/v3/user");
    }

    @Test
    void wwwDotComIsStillDotCom() {
        assertThat(GitHubEndpoints.forBaseUrl("https://www.github.com").userInfoUri())
                .isEqualTo("https://api.github.com/user");
    }
}
