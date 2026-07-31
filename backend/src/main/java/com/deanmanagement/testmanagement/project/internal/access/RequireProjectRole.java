package com.deanmanagement.testmanagement.project.internal.access;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the minimum {@link ProjectRole} the caller must hold on the project addressed by the
 * request. Enforced by {@link ProjectRoleAspect}: the project id is read from a path variable
 * (default {@code projectId}) and the caller's membership/role is checked via
 * {@link ProjectAccessService}. System admins always pass.
 *
 * <p>Default value is {@link ProjectRole#VIEWER} — i.e. project membership is required to read.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireProjectRole {

    /** Minimum role required. Defaults to VIEWER (membership-only). */
    ProjectRole value() default ProjectRole.VIEWER;

    /** Name of the path variable holding the project UUID. Defaults to {@code projectId}. */
    String pathVariable() default "projectId";
}
