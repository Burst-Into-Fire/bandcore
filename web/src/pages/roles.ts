import type { TFunction } from 'i18next';

import type { components } from '../api/schema';

type Role = components['schemas']['MyBandResponse']['role'];

export function roleLabel(t: TFunction, role: Role): string {
  switch (role) {
    case 'ADMIN':
      return t('bands.roles.ADMIN');
    case 'MEMBER':
      return t('bands.roles.MEMBER');
    case 'READ_ONLY':
      return t('bands.roles.READ_ONLY');
  }
}
