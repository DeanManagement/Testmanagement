/**
 * The user manual, loaded from `assets/manual/{lang}.json` rather than the translation bundle.
 *
 * <p>It is long-form prose, and the ngx-translate catalogues are fetched on every page load — a
 * manual in there would be paid for by everyone who never opens it. This is fetched once, only when
 * the help page is visited.
 *
 * <p>Content is structured blocks rather than markdown or HTML. That avoids a markdown dependency,
 * and more importantly avoids putting `innerHTML` anywhere near text that will one day be edited by
 * someone who is not thinking about script injection.
 */

/** Who a chapter is for. A reader sees a chapter when they hold at least one of its audiences. */
export type ManualAudience = 'EVERYONE' | 'TESTER' | 'PROJECT_ADMIN' | 'SYSTEM_ADMIN';

export interface ManualBlock {
  type: 'p' | 'ul' | 'steps' | 'note' | 'code';
  /** For `p`, `note` and `code`. */
  text?: string;
  /** For `ul` and `steps`. */
  items?: string[];
}

export interface ManualSection {
  heading: string;
  blocks: ManualBlock[];
}

export interface ManualChapter {
  id: string;
  title: string;
  summary: string;
  audiences: ManualAudience[];
  sections: ManualSection[];
}

export interface Manual {
  title: string;
  chapters: ManualChapter[];
}

/** What the signed-in user can do, from `GET /api/me/capabilities`. */
export interface MyCapabilities {
  systemAdmin: boolean;
  projectRoles: ('ADMIN' | 'TESTER' | 'VIEWER')[];
  highestRole?: 'ADMIN' | 'TESTER' | 'VIEWER';
  projectMemberships: number;
}

/**
 * Which chapters this reader should see.
 *
 * <p>A chapter is shown when the reader holds one of its audiences. EVERYONE always matches;
 * TESTER matches a tester or a project admin, since an admin can do everything a tester can;
 * PROJECT_ADMIN matches a project admin; SYSTEM_ADMIN matches only an instance admin.
 *
 * <p>Roles are taken across all projects rather than one: the manual is not scoped to a project,
 * and someone who can author on any project needs the authoring chapter.
 */
export function chaptersFor(manual: Manual, capabilities: MyCapabilities | null): ManualChapter[] {
  const roles = capabilities?.projectRoles ?? [];
  const isProjectAdmin = roles.includes('ADMIN');
  const canWrite = isProjectAdmin || roles.includes('TESTER');

  return manual.chapters.filter((chapter) =>
    chapter.audiences.some((audience) => {
      switch (audience) {
        case 'EVERYONE':
          return true;
        case 'TESTER':
          return canWrite;
        case 'PROJECT_ADMIN':
          return isProjectAdmin;
        case 'SYSTEM_ADMIN':
          return capabilities?.systemAdmin === true;
        default:
          return false;
      }
    })
  );
}
