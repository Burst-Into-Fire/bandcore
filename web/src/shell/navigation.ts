import type { ComponentType } from 'react';

import type { Permission } from '../auth/useBandAccess';
import {
  ExpensesIcon,
  MembersIcon,
  MerchIcon,
  SettingsIcon,
  SummaryIcon,
} from '../components/icons';

/** Sections of a band, in menu order. Items with a permission are hidden from users without it. */
export const SECTIONS: readonly {
  path: string;
  labelKey: `nav.${'summary' | 'expenses' | 'merch' | 'members' | 'settings'}`;
  /** Shorter label for the bottom navigation, where each item has about 72 px at 360 px. */
  shortLabelKey?: 'nav.settingsShort';
  icon: ComponentType<{ className?: string }>;
  permission?: Permission;
}[] = [
  { path: '/', labelKey: 'nav.summary', icon: SummaryIcon },
  { path: '/expenses', labelKey: 'nav.expenses', icon: ExpensesIcon },
  { path: '/merch', labelKey: 'nav.merch', icon: MerchIcon },
  { path: '/members', labelKey: 'nav.members', icon: MembersIcon },
  {
    path: '/settings',
    labelKey: 'nav.settings',
    shortLabelKey: 'nav.settingsShort',
    icon: SettingsIcon,
    permission: 'band:manage',
  },
];
