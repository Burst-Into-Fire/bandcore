import type { TFunction } from 'i18next';

import type { components } from '../../api/schema';

export type Expense = components['schemas']['ExpenseResponse'];
export type Category = components['schemas']['CategoryResponse'];
export type Member = components['schemas']['MemberResponse'];
export type ExpenseStatus = Expense['status'];

/** Default categories keep their translation until an admin renames them. */
export function categoryLabel(t: TFunction, category: Category): string {
  return category.defaultKey
    ? t(`expenseCategories.defaults.${category.defaultKey}`)
    : category.name;
}

/** "2026-11-10" -> "10/11", read as a calendar day (no time zone shift). */
export function dayMonth(isoDate: string): string {
  const [, month, day] = isoDate.split('-');
  return `${day}/${month}`;
}

/** "2026-11-10" -> "Novembro de 2026". */
export function monthLabel(isoDate: string): string {
  const [year, month] = isoDate.split('-').map(Number);
  const text = new Intl.DateTimeFormat('pt-BR', {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, 1)));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** Expenses grouped by the month of their date, keeping the list's order. */
export function groupByMonth(expenses: readonly Expense[]): { month: string; items: Expense[] }[] {
  const groups: { month: string; items: Expense[] }[] = [];
  for (const expense of expenses) {
    const month = expense.expenseDate.slice(0, 7);
    const last = groups.at(-1);
    if (last?.month === month) {
      last.items.push(expense);
    } else {
      groups.push({ month, items: [expense] });
    }
  }
  return groups;
}
