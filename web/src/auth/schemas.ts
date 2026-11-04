import { z } from 'zod';

import { isWellFormedSlug } from '../lib/slug';

/**
 * Client-side copies of the API rules, for immediate feedback. The API stays the authority (it
 * also rejects common passwords and reserved slugs). Messages are i18n keys.
 */

const characters = (value: string) => [...value].length;

export const emailSchema = z
  .string()
  .trim()
  .min(1, 'validation.required')
  .max(254, 'validation.emailInvalid')
  .pipe(z.email('validation.emailInvalid'));

/** 12 to 128 characters (code points, like the API), no composition rules. */
export const newPasswordSchema = z
  .string()
  .refine((value) => characters(value) >= 12, 'validation.passwordTooShort')
  .refine((value) => characters(value) <= 128, 'validation.passwordTooLong');

const nameSchema = z
  .string()
  .trim()
  .min(1, 'validation.required')
  .max(80, 'validation.nameTooLong');

export const signupSchema = z.object({
  displayName: nameSchema,
  email: emailSchema,
  password: newPasswordSchema,
});

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'validation.required'),
  password: z.string().min(1, 'validation.required'),
});

export const resendSchema = z.object({ email: emailSchema });

export const forgotPasswordSchema = z.object({ email: emailSchema });

export const resetPasswordSchema = z.object({ password: newPasswordSchema });

export const memberSchema = z.object({
  displayName: nameSchema,
  instrument: z.string().trim().max(40, 'validation.instrumentTooLong'),
});

export const invitationSchema = z.object({
  email: emailSchema,
  role: z.enum(['ADMIN', 'MEMBER', 'READ_ONLY']),
  memberId: z.string(),
});

export const bandSettingsSchema = z.object({
  name: nameSchema,
  timezone: z.string().min(1, 'validation.required'),
  currency: z.enum(['BRL', 'USD']),
});

export const createBandSchema = z.object({
  name: nameSchema,
  slug: z.string().trim().toLowerCase().refine(isWellFormedSlug, 'validation.slugInvalid'),
});

export const expenseSchema = z
  .object({
    description: z
      .string()
      .trim()
      .min(1, 'validation.required')
      .max(120, 'validation.descriptionTooLong'),
    categoryId: z.string().min(1, 'validation.required'),
    amountCents: z.number().int().positive('validation.amountRequired'),
    currency: z.enum(['BRL', 'USD']),
    expenseDate: z.string().min(1, 'validation.required'),
    status: z.enum(['PAID', 'PLANNED']),
    paidByMemberId: z.string(),
    notes: z.string().trim().max(500, 'validation.notesTooLong'),
  })
  .refine((values) => values.status === 'PLANNED' || values.paidByMemberId !== '', {
    path: ['paidByMemberId'],
    message: 'errors.PAYER_REQUIRED',
  });

export const categorySchema = z.object({
  name: z.string().trim().min(1, 'validation.required').max(40, 'validation.categoryNameTooLong'),
  useColor: z.boolean(),
  color: z.string(),
});

export const settlementSchema = z
  .object({
    fromMemberId: z.string().min(1, 'validation.required'),
    toMemberId: z.string().min(1, 'validation.required'),
    currency: z.enum(['BRL', 'USD']),
    amountCents: z.number().int().positive('validation.amountRequired'),
    paidOn: z.string().min(1, 'validation.required'),
    note: z.string().trim().max(500, 'validation.notesTooLong'),
  })
  .refine((values) => values.fromMemberId === '' || values.fromMemberId !== values.toMemberId, {
    path: ['toMemberId'],
    message: 'errors.SETTLEMENT_SAME_MEMBER',
  });

/** Merch quantities are whole units; an empty number field reads as NaN, which zod refuses. */
const units = z.number({ error: 'validation.wholeNumber' }).int('validation.wholeNumber');

export const variantSchema = z.object({
  size: z.string().trim().max(20, 'validation.sizeTooLong'),
  color: z.string().trim().max(40, 'validation.colorTooLong'),
  sku: z.string().trim().max(40, 'validation.skuTooLong'),
  /** 0 sells at the product's price. */
  priceCents: z.number().int().nonnegative(),
  lowStockThreshold: units.min(0, 'validation.wholeNumber').max(100_000, 'validation.wholeNumber'),
});

export const productSchema = z.object({
  name: z.string().trim().min(1, 'validation.required').max(120, 'validation.productNameTooLong'),
  description: z.string().trim().max(500, 'validation.notesTooLong'),
  currency: z.enum(['BRL', 'USD']),
  defaultPriceCents: z.number().int().nonnegative(),
  variants: z.array(variantSchema).max(50),
});

export const batchSchema = z
  .object({
    supplier: z.string().trim().max(120, 'validation.descriptionTooLong'),
    orderedOn: z.string(),
    receivedOn: z.string().min(1, 'validation.required'),
    currency: z.enum(['BRL', 'USD']),
    costMode: z.enum(['PER_ITEM', 'ALLOCATED']),
    totalCostCents: z.number().int().nonnegative(),
    notes: z.string().trim().max(500, 'validation.notesTooLong'),
    items: z
      .array(
        z.object({
          variantId: z.string().min(1, 'validation.variantRequired'),
          quantity: z
            .number({ error: 'validation.quantityRequired' })
            .int('validation.quantityRequired')
            .min(1, 'validation.quantityRequired')
            .max(100_000, 'validation.quantityRequired'),
          unitCostCents: z.number().int().nonnegative(),
        }),
      )
      .min(1)
      .max(100),
    createExpense: z.boolean(),
    expense: z.object({
      description: z.string().trim().max(120, 'validation.descriptionTooLong'),
      categoryId: z.string(),
      status: z.enum(['PAID', 'PLANNED']),
      paidByMemberId: z.string(),
    }),
  })
  .superRefine((values, context) => {
    if (!values.createExpense) {
      return;
    }
    if (values.expense.description === '') {
      context.addIssue({
        code: 'custom',
        path: ['expense', 'description'],
        message: 'validation.required',
      });
    }
    if (values.expense.categoryId === '') {
      context.addIssue({
        code: 'custom',
        path: ['expense', 'categoryId'],
        message: 'validation.required',
      });
    }
    if (values.expense.status === 'PAID' && values.expense.paidByMemberId === '') {
      context.addIssue({
        code: 'custom',
        path: ['expense', 'paidByMemberId'],
        message: 'errors.PAYER_REQUIRED',
      });
    }
  });

export const adjustmentSchema = z
  .object({
    mode: z.enum(['COUNT', 'DELTA']),
    quantity: units,
    reason: z.enum(['COUNT', 'DAMAGED', 'LOST', 'GIFT', 'OTHER']),
    note: z.string().trim().max(500, 'validation.notesTooLong'),
  })
  .superRefine((values, context) => {
    if (values.mode === 'COUNT' && values.quantity < 0) {
      context.addIssue({ code: 'custom', path: ['quantity'], message: 'validation.wholeNumber' });
    }
    if (values.mode === 'DELTA' && values.quantity === 0) {
      context.addIssue({ code: 'custom', path: ['quantity'], message: 'validation.deltaRequired' });
    }
    if (values.reason === 'OTHER' && values.note === '') {
      context.addIssue({ code: 'custom', path: ['note'], message: 'validation.noteRequired' });
    }
  });
