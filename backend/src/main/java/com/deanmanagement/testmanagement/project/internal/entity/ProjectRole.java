package com.deanmanagement.testmanagement.project.internal.entity;

/**
 * Project membership roles, ordered by privilege. A lower {@code rank} means more privilege,
 * so {@link #satisfies(ProjectRole)} answers "does this role meet at least the required role?".
 */
public enum ProjectRole {
    ADMIN(0),
    TESTER(1),
    VIEWER(2);

    private final int rank;

    ProjectRole(int rank) {
        this.rank = rank;
    }

    /**
     * @return {@code true} if this role is at least as privileged as {@code required}.
     *         e.g. {@code ADMIN.satisfies(TESTER)} is {@code true}; {@code VIEWER.satisfies(TESTER)} is {@code false}.
     */
    public boolean satisfies(ProjectRole required) {
        return this.rank <= required.rank;
    }
}
