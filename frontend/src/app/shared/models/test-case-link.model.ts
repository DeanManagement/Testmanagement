/** TM07: PRECONDITION means the source needs the target run first. */
export type TestCaseLinkType = 'PRECONDITION' | 'RELATES_TO';

/** A link seen from one case: OUTGOING when that case is the source. */
export interface TestCaseLink {
  id: string;
  linkType: TestCaseLinkType;
  direction: 'OUTGOING' | 'INCOMING';
  testCaseId: string;
  testCaseKey: string;
  testCaseTitle: string;
}

/** The cases to run before this one: its outgoing PRECONDITION links. */
export function preconditionLinks(links: TestCaseLink[]): TestCaseLink[] {
  return links.filter((link) => link.linkType === 'PRECONDITION' && link.direction === 'OUTGOING');
}
