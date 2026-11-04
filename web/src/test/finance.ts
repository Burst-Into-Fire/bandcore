import { http, HttpResponse } from 'msw';

import { apiUrl } from './server';

/**
 * A band with no expenses, balances or settlements yet: the finance endpoints that pages such
 * as Resumo call, for tests about something else.
 */
export const emptyFinanceHandlers = [
  http.get(apiUrl('/expenses'), () =>
    HttpResponse.json({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 }),
  ),
  http.get(apiUrl('/expense-categories'), () => HttpResponse.json([])),
  http.get(apiUrl('/balances'), () => HttpResponse.json({ currencies: [] })),
  http.get(apiUrl('/balances/settlement-plan'), () => HttpResponse.json({ currencies: [] })),
  http.get(apiUrl('/summary/finance'), ({ request }) =>
    HttpResponse.json({
      month: new URL(request.url).searchParams.get('month') ?? '2026-11',
      currencies: [],
      latest: [],
      upcoming: [],
    }),
  ),
];
