import type { components } from '../../api/schema';

export type Member = components['schemas']['MemberResponse'];
export type Membership = components['schemas']['MembershipResponse'];
export type Invitation = components['schemas']['InvitationResponse'];
export type Role = Membership['role'];

export const ROLES: readonly Role[] = ['ADMIN', 'MEMBER', 'READ_ONLY'];
