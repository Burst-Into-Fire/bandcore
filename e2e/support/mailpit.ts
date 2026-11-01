import { MAILPIT_URL } from './env.js';

interface MessageSummary {
  ID: string;
  Subject: string;
}

/**
 * Waits for the latest email to `to` (optionally with a subject) in Mailpit and returns the
 * first link in its text body that contains `path`.
 */
export async function awaitLink(to: string, path: string, subject?: string): Promise<string> {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const search = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`);
    const { messages } = (await search.json()) as { messages: MessageSummary[] };
    const message = messages.find((candidate) => !subject || candidate.Subject === subject);
    if (message) {
      const detail = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${message.ID}`)).json()) as {
        Text: string;
      };
      const link = detail.Text.split(/\s+/).find((word) => word.includes(path));
      if (link) {
        return link;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`No email to ${to} with a link to ${path}`);
}

/** The token of a link such as `.../verify-email#token=...`. */
export function tokenOf(link: string): string {
  const token = new URL(link).hash.match(/token=([A-Za-z0-9_-]+)/)?.[1];
  if (!token) {
    throw new Error(`No token in ${link}`);
  }
  return token;
}
