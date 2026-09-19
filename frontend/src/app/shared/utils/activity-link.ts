import { AuditLink } from '../models/activity.model';

/**
 * The page an activity entry links to (PRD-046). Requirements have no page of their own, so they
 * lead to the list. Null for anything else.
 */
export function activityRoute(projectId: string, link: AuditLink | null): string[] | null {
  if (!link) return null;
  const project = ['/projects', projectId];
  switch (link.type) {
    case 'TEST_CASE': return [...project, 'test-cases', link.id];
    case 'TEST_SUITE': return [...project, 'test-suites', link.id];
    case 'TEST_RUN': return [...project, 'test-runs', link.id];
    case 'TEST_PLAN': return [...project, 'test-plans', link.id];
    case 'BUG_REPORT': return [...project, 'bug-reports', link.id];
    case 'EXPLORATORY_SESSION': return [...project, 'exploratory-sessions', link.id];
    case 'SHARED_STEP': return [...project, 'shared-steps', link.id];
    case 'REQUIREMENT': return [...project, 'requirements'];
    default: return null;
  }
}
