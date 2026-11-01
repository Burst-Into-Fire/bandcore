import { request as httpRequest } from 'node:http';

import { WEB_PORT } from './env.js';

export interface ApiResponse {
  status: number;
  headers: Record<string, string | string[] | undefined>;
  body: string;
}

/**
 * Calls the API through the web proxy with an explicit Host header, like a browser on that
 * subdomain. Node does not resolve *.localhost, so it connects to 127.0.0.1.
 */
export function api(
  host: string,
  method: string,
  path: string,
  { body, token }: { body?: unknown; token?: string } = {},
): Promise<ApiResponse> {
  const payload = body === undefined ? undefined : JSON.stringify(body);
  const headers: Record<string, string> = {
    Host: host,
    // CSRF: unsafe requests without a bearer token must send it (same as the web app).
    'X-Requested-With': 'XMLHttpRequest',
  };
  if (payload !== undefined) {
    headers['Content-Type'] = 'application/json';
    headers['Content-Length'] = Buffer.byteLength(payload).toString();
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return new Promise((resolve, reject) => {
    const request = httpRequest(
      { host: '127.0.0.1', port: WEB_PORT, method, path: `/api${path}`, headers },
      (response) => {
        let data = '';
        response.setEncoding('utf8');
        response.on('data', (chunk: string) => (data += chunk));
        response.on('end', () =>
          resolve({ status: response.statusCode ?? 0, headers: response.headers, body: data }),
        );
      },
    );
    request.on('error', reject);
    if (payload !== undefined) {
      request.write(payload);
    }
    request.end();
  });
}

export async function expectStatus(response: Promise<ApiResponse>, status: number): Promise<ApiResponse> {
  const result = await response;
  if (result.status !== status) {
    throw new Error(`Expected ${status}, got ${result.status}: ${result.body}`);
  }
  return result;
}
