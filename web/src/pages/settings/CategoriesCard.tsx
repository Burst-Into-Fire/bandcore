import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { categorySchema } from '../../auth/schemas';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { Card, StatusBadge } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { ErrorState, LoadingState } from '../../components/states';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { errorMessage } from '../errorMessage';
import { categoryLabel, type Category } from '../expenses/types';

type CategoryInput = z.input<typeof categorySchema>;
type CategoryValues = z.output<typeof categorySchema>;

const DEFAULT_COLOR = '#ff6f61';

type Pending = { id: string; action: 'edit' | 'delete' } | null;

/** Action names (not UI text). */
const EDIT = 'edit';
const DELETE = 'delete';
const USE_COLOR = 'useColor';

/**
 * Expense categories (admins): add, rename, recolor, archive or bring back, and delete those no
 * expense uses. Archived categories stay on their expenses but are not offered for new ones.
 */
export function CategoriesCard() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [pending, setPending] = useState<Pending>(null);
  const categories = useQuery({
    queryKey: ['expense-categories'],
    queryFn: async () => unwrap(await api.GET('/expense-categories')),
  });
  const change = useMutation({
    mutationFn: async ({ category, action }: { category: Category; action: string }) => {
      const path = { params: { path: { categoryId: category.id } } };
      switch (action) {
        case 'archive':
          return unwrap(await api.POST('/expense-categories/{categoryId}/archive', path));
        case 'unarchive':
          return unwrap(await api.POST('/expense-categories/{categoryId}/unarchive', path));
        default:
          return unwrap(
            await api.DELETE('/expense-categories/{categoryId}', { ...path, parseAs: 'text' }),
          );
      }
    },
    onSuccess: async () => {
      setPending(null);
      await queryClient.invalidateQueries({ queryKey: ['expense-categories'] });
    },
  });

  return (
    <Card
      title={t('expenseCategories.title')}
      action={
        adding ? null : (
          <button type="button" className={textLink} onClick={() => setAdding(true)}>
            {t('expenseCategories.add')}
          </button>
        )
      }
    >
      <p className="text-muted -mt-2">{t('expenseCategories.subtitle')}</p>
      {adding ? <CategoryForm onDone={() => setAdding(false)} /> : null}
      {categories.isPending ? <LoadingState /> : null}
      {categories.isError ? (
        <ErrorState
          message={errorMessage(t, categories.error)}
          onRetry={() => void categories.refetch()}
        />
      ) : null}
      {change.isError ? <FormAlert message={errorMessage(t, change.error)} /> : null}
      {categories.data?.length === 0 ? (
        <p className="text-muted">{t('expenseCategories.empty')}</p>
      ) : null}
      <ul className="divide-border flex flex-col divide-y">
        {(categories.data ?? []).map((category) => {
          const name = categoryLabel(t, category);
          if (pending?.id === category.id && pending.action === 'edit') {
            return (
              <li key={category.id} className="py-2">
                <CategoryForm category={category} onDone={() => setPending(null)} />
              </li>
            );
          }
          return (
            <li key={category.id} className="flex flex-col gap-2 py-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="flex min-w-0 items-center gap-2">
                  <span
                    aria-hidden="true"
                    className="border-border size-3 shrink-0 rounded-full border"
                    style={category.color ? { backgroundColor: category.color } : undefined}
                  />
                  <span className={`truncate ${category.archived ? 'text-muted' : ''}`}>
                    {name}
                  </span>
                  {category.archived ? (
                    <StatusBadge tone="neutral">{t('expenseCategories.archived')}</StatusBadge>
                  ) : null}
                </span>
                <span className="flex flex-wrap gap-x-4 gap-y-1">
                  <button
                    type="button"
                    className={textLink}
                    aria-label={`${t('expenseCategories.edit')}: ${name}`}
                    onClick={() => setPending({ id: category.id, action: EDIT })}
                  >
                    {t('expenseCategories.edit')}
                  </button>
                  <button
                    type="button"
                    className={textLink}
                    disabled={change.isPending}
                    aria-label={`${
                      category.archived
                        ? t('expenseCategories.unarchive')
                        : t('expenseCategories.archive')
                    }: ${name}`}
                    onClick={() =>
                      change.mutate({
                        category,
                        action: category.archived ? 'unarchive' : 'archive',
                      })
                    }
                  >
                    {category.archived
                      ? t('expenseCategories.unarchive')
                      : t('expenseCategories.archive')}
                  </button>
                  <button
                    type="button"
                    className={textLink}
                    aria-label={`${t('expenseCategories.delete')}: ${name}`}
                    onClick={() => setPending({ id: category.id, action: DELETE })}
                  >
                    {t('expenseCategories.delete')}
                  </button>
                </span>
              </div>
              {pending?.id === category.id && pending.action === 'delete' ? (
                <Confirm
                  question={t('expenseCategories.confirmDelete', { name })}
                  pending={change.isPending}
                  onConfirm={() => change.mutate({ category, action: DELETE })}
                  onCancel={() => setPending(null)}
                />
              ) : null}
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function CategoryForm({ category, onDone }: { category?: Category; onDone: () => void }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const form = useForm<CategoryInput, unknown, CategoryValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: {
      name: category ? categoryLabel(t, category) : '',
      useColor: Boolean(category?.color),
      color: category?.color ?? DEFAULT_COLOR,
    },
  });
  const useColor = useWatch({ control: form.control, name: USE_COLOR });
  const save = useMutation({
    mutationFn: async (values: CategoryValues) => {
      const color = values.useColor ? values.color : '';
      if (category) {
        const renamed = values.name !== categoryLabel(t, category);
        return unwrap(
          await api.PATCH('/expense-categories/{categoryId}', {
            params: { path: { categoryId: category.id } },
            // A default category keeps its translation unless the name really changes.
            body: { ...(renamed ? { name: values.name } : {}), color },
          }),
        );
      }
      return unwrap(
        await api.POST('/expense-categories', {
          body: { name: values.name, ...(color ? { color } : {}) },
        }),
      );
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['expense-categories'] });
      onDone();
    },
    onError: (error) => applyProblem(error, form.setError, ['name']),
  });
  const { errors } = form.formState;

  return (
    <form
      noValidate
      aria-label={
        category
          ? t('expenseCategories.editTitle', { name: categoryLabel(t, category) })
          : t('expenseCategories.addTitle')
      }
      className="bg-surface-raised flex flex-col gap-3 rounded-lg p-3"
      onSubmit={form.handleSubmit((values) => save.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <TextField
        label={t('expenseCategories.name')}
        maxLength={40}
        error={translateKey(t, errors.name?.message)}
        {...form.register('name')}
      />
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-2">
          <input type="checkbox" className="accent-accent size-5" {...form.register('useColor')} />
          {t('expenseCategories.useColor')}
        </label>
        {useColor ? (
          <label className="flex items-center gap-2">
            <span className="sr-only">{t('expenseCategories.color')}</span>
            <input
              type="color"
              className="border-border h-9 w-12 rounded border bg-transparent"
              {...form.register('color')}
            />
          </label>
        ) : null}
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={save.isPending}>
          {save.isPending ? t('expenseCategories.saving') : t('expenseCategories.save')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('expenseCategories.cancel')}
        </button>
      </div>
    </form>
  );
}
