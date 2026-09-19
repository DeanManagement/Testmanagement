import { TranslateService } from '@ngx-translate/core';

/** What a sentence is about: who did what to which object. */
export interface ActivityFact {
  actor: string;
  action: string;
  entityType: string;
  entityName: string | null;
}

/**
 * One activity or notification as a whole sentence (bug report cd1412a5, point 4). Built from
 * fragments, the German read "Anna hat gelöscht Testlauf …" and "Anna schloss ab Testlauf …":
 * German puts the verb last and the object needs its article, so each action has its own template
 * with the actor and the object as placeholders.
 */
export function activitySentence(translate: TranslateService, fact: ActivityFact): string {
  const withName = (type: string) => fact.entityName
    ? translate.instant('activity.named', { type, name: fact.entityName })
    : type;
  return translate.instant('activity.sentences.' + fact.action, {
    user: fact.actor,
    // As the sentence's object, e.g. German "den Testlauf «Smoke»".
    object: withName(translate.instant('activity.objects.' + fact.entityType)),
    // Without an article, e.g. "Testlauf «Smoke»", for sentences that do not take the object.
    bare: withName(translate.instant('activity.entityTypes.' + fact.entityType)),
  });
}
