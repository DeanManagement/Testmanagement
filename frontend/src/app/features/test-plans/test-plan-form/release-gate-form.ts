import { ReleaseGate } from '../../../shared/models/readiness.model';

type Threshold = Exclude<keyof ReleaseGate, 'coverageOnScope'>;

/** What the four inputs hold: a number, or null (or, from a cleared native input, an empty string). */
export type GateFormValue = { [K in Threshold]: number | string | null | undefined } & {
  coverageOnScope?: boolean | null;
};

/** An emptied input means "criterion off", which the API spells null. */
export function toGate(value: GateFormValue): ReleaseGate {
  const read = (v: number | string | null | undefined): number | null =>
    v === null || v === undefined || v === '' ? null : Number(v);
  return {
    minPassRate: read(value.minPassRate),
    maxBlockerBugs: read(value.maxBlockerBugs),
    minCoverage: read(value.minCoverage),
    maxFlaky: read(value.maxFlaky),
    coverageOnScope: !!value.coverageOnScope,
  };
}

export function hasCriteria(gate: ReleaseGate | null | undefined): boolean {
  return !!gate && [gate.minPassRate, gate.maxBlockerBugs, gate.minCoverage, gate.maxFlaky]
    .some((threshold) => threshold !== null && threshold !== undefined);
}
