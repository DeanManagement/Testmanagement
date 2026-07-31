/** One row of data a parameterized test case runs against (PRD-015). */
export interface ParameterSet {
  id: string;
  name: string;
  values: Record<string, string>;
  orderIndex: number;
}

export interface SaveParameterSetRequest {
  name: string;
  values: Record<string, string>;
  orderIndex?: number;
}
