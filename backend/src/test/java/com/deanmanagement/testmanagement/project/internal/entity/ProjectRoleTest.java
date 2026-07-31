package com.deanmanagement.testmanagement.project.internal.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRoleTest {

    @Test
    void adminSatisfiesEveryRole() {
        assertThat(ProjectRole.ADMIN.satisfies(ProjectRole.ADMIN)).isTrue();
        assertThat(ProjectRole.ADMIN.satisfies(ProjectRole.TESTER)).isTrue();
        assertThat(ProjectRole.ADMIN.satisfies(ProjectRole.VIEWER)).isTrue();
    }

    @Test
    void testerSatisfiesTesterAndViewerButNotAdmin() {
        assertThat(ProjectRole.TESTER.satisfies(ProjectRole.ADMIN)).isFalse();
        assertThat(ProjectRole.TESTER.satisfies(ProjectRole.TESTER)).isTrue();
        assertThat(ProjectRole.TESTER.satisfies(ProjectRole.VIEWER)).isTrue();
    }

    @Test
    void viewerSatisfiesOnlyViewer() {
        assertThat(ProjectRole.VIEWER.satisfies(ProjectRole.ADMIN)).isFalse();
        assertThat(ProjectRole.VIEWER.satisfies(ProjectRole.TESTER)).isFalse();
        assertThat(ProjectRole.VIEWER.satisfies(ProjectRole.VIEWER)).isTrue();
    }
}
