import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { BugReport, CreateBugReportRequest, UpdateBugReportRequest } from '../../shared/models/bug-report.model';

export const BugReportActions = createActionGroup({
  source: 'BugReports',
  events: {
    'Load Bug Reports': props<{ projectId: string }>(),
    'Load Bug Reports Success': props<{ bugReports: BugReport[] }>(),
    'Load Bug Reports Failure': props<{ error: string }>(),

    'Load Bug Report': props<{ projectId: string; id: string }>(),
    'Load Bug Report Success': props<{ bugReport: BugReport }>(),
    'Load Bug Report Failure': props<{ error: string }>(),

    'Load Bug Reports By Test Result': props<{ projectId: string; testResultId: string }>(),
    'Load Bug Reports By Test Result Success': props<{ bugReports: BugReport[] }>(),
    'Load Bug Reports By Test Result Failure': props<{ error: string }>(),

    'Create Bug Report': props<{ projectId: string; request: CreateBugReportRequest }>(),
    'Create Bug Report Success': props<{ bugReport: BugReport }>(),
    'Create Bug Report Failure': props<{ error: string }>(),

    'Update Bug Report': props<{ projectId: string; id: string; request: UpdateBugReportRequest }>(),
    'Update Bug Report Success': props<{ bugReport: BugReport }>(),
    'Update Bug Report Failure': props<{ error: string }>(),

    'Delete Bug Report': props<{ projectId: string; id: string }>(),
    'Delete Bug Report Success': props<{ id: string }>(),
    'Delete Bug Report Failure': props<{ error: string }>(),

    'Clear Bug Reports': emptyProps(),
  },
});
