import { describe, expect, it } from 'vitest';

import { bandHostName, detectHost } from './host';
import { safeRedirect } from './navigation';
import { isWellFormedSlug, slugify } from './slug';

describe('detectHost', () => {
  it.each([
    ['app.localhost', { kind: 'central', baseDomain: 'localhost' }],
    ['app.bandcore.app', { kind: 'central', baseDomain: 'bandcore.app' }],
    ['burst.localhost', { kind: 'band', slug: 'burst', baseDomain: 'localhost' }],
    ['The-Burst.bandcore.app.', { kind: 'band', slug: 'the-burst', baseDomain: 'bandcore.app' }],
    ['localhost', { kind: 'central', baseDomain: 'localhost' }],
  ])('%s', (hostname, expected) => {
    expect(detectHost(hostname)).toEqual(expected);
  });

  it('builds band host names for previews', () => {
    expect(bandHostName('burst', 'bandcore.app')).toBe('burst.bandcore.app');
  });
});

describe('safeRedirect', () => {
  it.each([
    ['/bands', '/bands'],
    ['/bands/new?x=1', '/bands/new?x=1'],
    ['//evil.example', '/'],
    ['/\\evil.example', '/'],
    ['https://evil.example', '/'],
    ['javascript:alert(1)', '/'],
    [null, '/'],
  ])('%s -> %s', (value, expected) => {
    expect(safeRedirect(value, '/')).toBe(expected);
  });
});

describe('slugs', () => {
  it.each([
    ['Os Mutantes!', 'os-mutantes'],
    ['  Bárbara & Çia  ', 'barbara-cia'],
    ['---', ''],
  ])('slugify(%s) = %s', (name, expected) => {
    expect(slugify(name)).toBe(expected);
  });

  it.each([
    ['burst', true],
    ['the-burst', true],
    ['ab', false],
    ['the--burst', false],
    ['-burst', false],
    ['Burst', false],
    ['xn--brst', false],
  ])('isWellFormedSlug(%s) = %s', (slug, expected) => {
    expect(isWellFormedSlug(slug)).toBe(expected);
  });
});
