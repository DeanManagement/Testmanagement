import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { BugReport, BugReportQuery, ChangeBugStatusRequest, CreateBugReportRequest, UpdateBugReportRequest } from '../../shared/models/bug-report.model';
import { PageMetadata } from '../../shared/models/page.model';

export const BugReportActions = createActionGroup({
  source: 'BugReports',
  events: {
    'Load Bug Reports': props<{ projectId: string; query: BugReportQuery }>(),
    'Load Bug Reports Success': props<{ bugReports: BugReport[]; page: PageMetadata }>(),
    'Load Bug Reports Failure': props<{ error: string }>(),

    'Load Bug Report': props<{ projectId: string; id: string }>(),
    'Load Bug Report Success': props<{ bugReport: BugReport }>(),
    'Load Bug Report Failure': props<{ error: string }>(),

    'Load Bug Reports By Test Result': props<{ projectId: string; testResultId: string }>(),
    'Load Bug Reports By Test Result Success': props<{ testResultId: string; bugReports: BugReport[] }>(),
    'Load Bug Reports By Test Result Failure': props<{ error: string }>(),

    /** files: attachments queued in the form (PRD-051), uploaded once the bug exists. */
    'Create Bug Report': props<{ projectId: string; request: CreateBugReportRequest; files?: File[] }>(),
    /** failedUploads: names of queued files the server refused; the bug itself was saved. */
    'Create Bug Report Success': props<{ bugReport: BugReport; failedUploads?: string[] }>(),
    'Create Bug Report Failure': props<{ error: string }>(),

    'Update Bug Report': props<{ projectId: string; id: string; request: UpdateBugReportRequest }>(),
    'Update Bug Report Success': props<{ bugReport: BugReport }>(),
    'Update Bug Report Failure': props<{ error: string }>(),

    'Change Bug Report Status': props<{ projectId: string; id: string; request: ChangeBugStatusRequest }>(),
    'Change Bug Report Status Success': props<{ bugReport: BugReport }>(),
    'Change Bug Report Status Failure': props<{ error: string }>(),

    'Delete Bug Report': props<{ projectId: string; id: string }>(),
    'Delete Bug Report Success': props<{ id: string }>(),
    'Delete Bug Report Failure': props<{ error: string }>(),

    'Load My Bug Reports': emptyProps(),
    'Load My Bug Reports Success': props<{ bugReports: BugReport[] }>(),
    'Load My Bug Reports Failure': props<{ error: string }>(),

    'Clear Bug Reports': emptyProps(),
  },
});
