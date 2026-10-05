export interface ApiClientOptions {
  headers?: Record<string, string>;
}

export const api = {
  async get<T>(url: string, params?: Record<string, unknown>, options?: ApiClientOptions): Promise<T> {
    const urlObj = new URL(url, window.location.origin);
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) {
          urlObj.searchParams.append(k, String(v));
        }
      });
    }
    const res = await fetch(urlObj.toString(), {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...(options?.headers ?? {}),
      },
    });
    if (!res.ok) {
      throw new Error(`GET ${url} failed with status ${res.status}`);
    }
    return res.json();
  },

  async post<T>(url: string, body?: unknown, options?: ApiClientOptions): Promise<T> {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(options?.headers ?? {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
      throw new Error(`POST ${url} failed with status ${res.status}`);
    }
    return res.json();
  },

  async graphql<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
    const res = await fetch('/graphql', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ query, variables }),
    });
    if (!res.ok) {
      throw new Error(`GraphQL query failed with status ${res.status}`);
    }
    const json = await res.json();
    if (json.errors && json.errors.length > 0) {
      throw new Error(json.errors[0].message || 'GraphQL execution error');
    }
    return json.data;
  },
};
