import { Attachment } from '../models/test-case.model';
import { groupFiles } from './step-files';

function file(id: string, owner: Partial<Attachment>): Attachment {
  return {
    id, testCaseId: null, bugReportId: null, fileName: `${id}.pdf`, contentType: 'application/pdf',
    sizeBytes: 1, sha256: id, createdAt: '2026-10-28T10:00:00Z', createdBy: null, ...owner,
  };
}

describe('groupFiles', () => {
  it('groups files by their step in upload order', () => {
    const groups = groupFiles([
      file('a', { testStepId: 's1' }), file('b', { testStepId: 's2' }), file('c', { testStepId: 's1' }),
    ], 'testStepId');

    expect(groups.get('s1')!.map((f) => f.id)).toEqual(['a', 'c']);
    expect(groups.get('s2')!.map((f) => f.id)).toEqual(['b']);
  });

  it('leaves out files of another kind of owner', () => {
    const groups = groupFiles([file('a', { testStepId: 's1' }), file('b', { stepResultId: 'r1' })], 'stepResultId');

    expect([...groups.keys()]).toEqual(['r1']);
  });
});
