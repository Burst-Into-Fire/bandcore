import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Controller, useFieldArray, useForm, type Path } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem, withFormPaths } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { productSchema } from '../../auth/schemas';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { FormDialog } from '../../components/FormDialog';
import { MoneyInput } from '../../components/MoneyInput';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { CURRENCIES, type Currency } from '../../lib/money';
import type { Product } from './types';

type ProductInput = z.input<typeof productSchema>;
type ProductValues = z.output<typeof productSchema>;

const PRICE = 'defaultPriceCents';

const EMPTY_VARIANT = { size: '', color: '', sku: '', priceCents: 0, lowStockThreshold: 5 };

/**
 * Registers a product with its variants, or edits a product's own fields when {@code product}
 * is given (its variants are changed one by one in the list). Full screen on phones.
 */
export function ProductForm({
  product,
  defaultCurrency,
  onDone,
}: {
  product?: Product;
  defaultCurrency: Currency;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const firstField = useRef<HTMLInputElement | null>(null);
  const form = useForm<ProductInput, unknown, ProductValues>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: product?.name ?? '',
      description: product?.description ?? '',
      currency: product?.currency ?? defaultCurrency,
      defaultPriceCents: product?.defaultPriceCents ?? 0,
      variants: [],
    },
  });
  const variants = useFieldArray({ control: form.control, name: 'variants' });
  const { errors } = form.formState;

  useEffect(() => {
    firstField.current?.focus();
  }, []);

  const save = useMutation({
    mutationFn: async (values: ProductValues) => {
      const fields = {
        name: values.name,
        description: values.description,
        currency: values.currency,
        defaultPriceCents: values.defaultPriceCents,
      };
      if (product) {
        return unwrap(
          await api.PATCH('/products/{productId}', {
            params: { path: { productId: product.id } },
            body: { ...fields, version: product.version },
          }),
        );
      }
      return unwrap(
        await api.POST('/products', {
          body: {
            ...fields,
            variants: values.variants.map((variant) => ({
              size: variant.size,
              color: variant.color,
              sku: variant.sku,
              lowStockThreshold: variant.lowStockThreshold,
              ...(variant.priceCents > 0 ? { priceCents: variant.priceCents } : {}),
            })),
          },
        }),
      );
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      onDone();
    },
    onError: (error) => {
      const fields: Path<ProductInput>[] = ['name', 'description', 'currency', PRICE];
      variants.fields.forEach((_, index) => {
        fields.push(`variants.${index}.size`, `variants.${index}.sku`);
      });
      applyProblem(withFormPaths(error), form.setError, fields);
    },
  });

  const { ref: nameRef, ...name } = form.register('name');

  return (
    <FormDialog
      title={product ? t('merch.editTitle') : t('merch.newTitle')}
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <TextField
        label={t('merch.name')}
        placeholder={t('merch.namePlaceholder')}
        maxLength={120}
        error={translateKey(t, errors.name?.message)}
        ref={(element) => {
          nameRef(element);
          firstField.current = element;
        }}
        {...name}
      />
      <TextField
        label={t('merch.description')}
        maxLength={500}
        error={translateKey(t, errors.description?.message)}
        {...form.register('description')}
      />
      <div className="grid grid-cols-[1fr_auto] gap-4">
        <Controller
          control={form.control}
          name={PRICE}
          render={({ field }) => (
            <MoneyInput
              label={t('merch.price')}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
            />
          )}
        />
        <SelectField
          label={t('merch.currency')}
          options={CURRENCIES.map((code) => ({ value: code, label: code }))}
          error={translateKey(t, errors.currency?.message)}
          {...form.register('currency')}
        />
      </div>
      {product ? null : (
        <fieldset className="flex flex-col gap-3">
          <legend className="text-card-title text-muted mb-1">{t('merch.variants')}</legend>
          <p className="text-caption text-muted">{t('merch.variantsHint')}</p>
          {variants.fields.map((field, index) => (
            <div
              key={field.id}
              role="group"
              aria-label={t('merch.variantNumber', { number: index + 1 })}
              className="border-border flex flex-col gap-3 rounded-lg border p-3"
            >
              <div className="grid gap-3 sm:grid-cols-3">
                <TextField
                  label={t('merch.size')}
                  maxLength={20}
                  error={translateKey(t, errors.variants?.[index]?.size?.message)}
                  {...form.register(`variants.${index}.size`)}
                />
                <TextField
                  label={t('merch.color')}
                  maxLength={40}
                  error={translateKey(t, errors.variants?.[index]?.color?.message)}
                  {...form.register(`variants.${index}.color`)}
                />
                <TextField
                  label={t('merch.sku')}
                  maxLength={40}
                  error={translateKey(t, errors.variants?.[index]?.sku?.message)}
                  {...form.register(`variants.${index}.sku`)}
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Controller
                  control={form.control}
                  name={`variants.${index}.priceCents`}
                  render={({ field: price }) => (
                    <MoneyInput
                      label={t('merch.ownPrice')}
                      value={price.value}
                      onChange={price.onChange}
                      onBlur={price.onBlur}
                    />
                  )}
                />
                <TextField
                  type="number"
                  inputMode="numeric"
                  min={0}
                  label={t('merch.lowStockThreshold')}
                  error={translateKey(t, errors.variants?.[index]?.lowStockThreshold?.message)}
                  {...form.register(`variants.${index}.lowStockThreshold`, { valueAsNumber: true })}
                />
              </div>
              <p className="text-caption text-muted">{t('merch.ownPriceHint')}</p>
              <button
                type="button"
                className={`${textLink} self-start`}
                onClick={() => variants.remove(index)}
              >
                {t('merch.removeVariant', { number: index + 1 })}
              </button>
            </div>
          ))}
          <button
            type="button"
            className={`${secondaryButton} self-start`}
            onClick={() => variants.append({ ...EMPTY_VARIANT })}
          >
            {t('merch.addVariant')}
          </button>
        </fieldset>
      )}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={save.isPending}>
          {save.isPending ? t('merch.saving') : t('merch.save')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('merch.cancel')}
        </button>
      </div>
    </FormDialog>
  );
}
