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

export const createBandSchema = z.object({
  name: nameSchema,
  slug: z.string().trim().toLowerCase().refine(isWellFormedSlug, 'validation.slugInvalid'),
});
