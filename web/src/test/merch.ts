import { http, HttpResponse } from 'msw';

import type { components } from '../api/schema';
import { startSession } from '../auth/session';
import { apiUrl, server } from './server';

type Product = components['schemas']['ProductResponse'];
type Variant = components['schemas']['ProductVariantResponse'];
type Batch = components['schemas']['BatchResponse'];

export const ADMIN_PERMISSIONS = [
  'band:read',
  'member:read',
  'finance:read',
  'finance:write',
  'finance:manage',
  'merch:read',
  'merch:manage',
];

export const MEMBER_PERMISSIONS = [
  'band:read',
  'member:read',
  'finance:read',
  'finance:write',
  'merch:read',
];

export function variant(overrides: Partial<Variant> & { id: string }): Variant {
  return {
    productId: 'p-shirt',
    effectivePriceCents: 6000,
    lowStockThreshold: 0,
    quantityOnHand: 0,
    lowStock: false,
    archived: false,
    version: 0,
    ...overrides,
  };
}

export function product(overrides: Partial<Product> & { id: string; name: string }): Product {
  return {
    currency: 'BRL',
    defaultPriceCents: 6000,
    archived: false,
    version: 0,
    variants: [],
    createdAt: '2026-11-01T12:00:00Z',
    ...overrides,
  };
}

/** The mockup's merch table: a black T-shirt in M, G and GG, and a patch. */
export const CATALOG: Product[] = [
  product({
    id: 'p-shirt',
    name: 'Camiseta preta',
    variants: [
      variant({
        id: 'v-m',
        size: 'M',
        quantityOnHand: 12,
        lowStockThreshold: 5,
        averageUnitCostCents: 2500,
      }),
      // Like the API, variants without a batch yet have a null average cost.
      variant({
        id: 'v-g',
        size: 'G',
        quantityOnHand: 3,
        lowStockThreshold: 5,
        lowStock: true,
        averageUnitCostCents: null as unknown as number,
      }),
      variant({ id: 'v-gg', size: 'GG', quantityOnHand: 0, lowStockThreshold: 5, lowStock: true }),
    ],
  }),
  product({
    id: 'p-patch',
    name: 'Patch',
    defaultPriceCents: 1500,
    variants: [
      variant({
        id: 'v-patch',
        productId: 'p-patch',
        effectivePriceCents: 1500,
        quantityOnHand: 20,
      }),
    ],
  }),
];

export const MEMBERS = [
  { id: 'a-ana', displayName: 'Ana', userId: 'u-ana', status: 'ACTIVE' },
  { id: 'b-bia', displayName: 'Bia', status: 'ACTIVE' },
  { id: 'c-caio', displayName: 'Caio', status: 'ACTIVE' },
].map((member) => ({ ...member, linkedToAccount: false, joinedAt: '2026-01-01T00:00:00Z' }));

export const CATEGORIES = [
  { id: 'cat-gear', name: 'Equipamento', defaultKey: 'GEAR', archived: false },
  { id: 'cat-merch', name: 'Merch', defaultKey: 'MERCH', archived: false },
];

/** A band host session with the merch endpoints a page reads; override with server.use(...). */
export function merchApi({
  permissions = ADMIN_PERMISSIONS,
  catalog = CATALOG,
  batches = [] as Batch[],
} = {}) {
  server.use(
    http.get(apiUrl('/me'), () =>
      HttpResponse.json({
        id: 'u-ana',
        email: 'ana@example.com',
        displayName: 'Ana',
        emailVerified: true,
        band: { id: 'b', slug: 'burst', role: 'ADMIN', permissions },
      }),
    ),
    http.get(apiUrl('/band'), () =>
      HttpResponse.json({
        id: 'b',
        slug: 'burst',
        name: 'The Burst',
        currency: 'BRL',
        timezone: 'America/Sao_Paulo',
      }),
    ),
    http.get(apiUrl('/members'), () => HttpResponse.json(MEMBERS)),
    http.get(apiUrl('/expense-categories'), () => HttpResponse.json(CATEGORIES)),
    http.get(apiUrl('/products'), () => HttpResponse.json(catalog)),
    http.get(apiUrl('/batches'), () =>
      HttpResponse.json({
        items: batches,
        page: 0,
        size: 20,
        totalItems: batches.length,
        totalPages: batches.length ? 1 : 0,
      }),
    ),
  );
  startSession('token');
}
