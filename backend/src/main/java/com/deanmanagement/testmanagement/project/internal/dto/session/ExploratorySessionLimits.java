package com.deanmanagement.testmanagement.project.internal.dto.session;

/** Bounds shared by the DTOs and the V58 check constraint (PRD-034 §3.2). */
public final class ExploratorySessionLimits {

    public static final int MIN_TIMEBOX = 5;
    public static final int MAX_TIMEBOX = 480;

    private ExploratorySessionLimits() {
    }
}
