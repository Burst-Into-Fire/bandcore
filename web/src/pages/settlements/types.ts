import type { components } from '../../api/schema';

export type Settlement = components['schemas']['SettlementResponse'];
export type Balances = components['schemas']['BalancesResponse'];
export type SettlementPlan = components['schemas']['SettlementPlanResponse'];

/** Everything that changes when money moves between members. */
export const MONEY_QUERIES = [
  ['balances'],
  ['settlement-plan'],
  ['settlements'],
  ['summary'],
  ['expenses'],
] as const;
