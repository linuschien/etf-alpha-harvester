---
name: vitest-msw-tester
description: Generates deterministic Vitest + MSW unit tests for @json-render/react page components. Enforces userEvent over fireEvent, store-state assertions, executeBehavior spy verification, and MSW intercept checks as mandatory test patterns.
---

# Skill: Vitest + MSW Page Tester

## ℹ️ Objective
Generate `*.page.test.tsx` files for every page rendered by `<Renderer spec={spec} registry={componentRegistry} />`. Tests must verify **real user interactions** — not just DOM presence — by asserting store state, behavior handler calls, and API interception.

---

## 🏗️ Architecture Reference

Before writing tests, understand the data-flow architecture:

| Concept | How it works in tests |
|---|---|
| **Page rendering** | `<JSONUIProvider store={store} handlers={handlers}>` wraps the page |
| **Table data** | DataTable reads `$bindState: "/data/listXxx"` from **store** — set `store.set('/data/listXxx', rows)` to populate rows; GraphQL hooks are NOT auto-called by the Renderer |
| **Modal open** | `openModal` handler spy sets `store.set('/modals/<id>', true)` |
| **Modal visibility** | Component uses `visible: { "$state": "/modals/<id>" }` — set store BEFORE `renderPage()` to pre-open |
| **Submit / Delete** | `executeBehavior` vi.fn() spy — assert `toHaveBeenCalledWith({ ref: '...' })` |
| **Modal auto-close** | `adapt()` in component-registry intercepts `press` events for 儲存/取消/確認刪除 and resets `/modals/*` to false |
| **User interaction** | Always `userEvent.setup()` + `await user.click()` — NEVER `fireEvent` |

> ⚠️ **Key Finding**: The Renderer is purely declarative. API hooks in `src/hooks/` are NOT auto-wired to the store. To show data in DataTable for tests, manually call `store.set('/data/listXxx', mockRows)`.

---

## 📋 Mandatory Test Harness

Every `*.page.test.tsx` MUST use this harness:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event'; // NOT fireEvent
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { JSONUIProvider, createStateStore } from '@json-render/react';
import { componentRegistry } from '@/json-render/component-registry';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { server } from '@/mocks/server'; // globally started in test/setup.ts
import { graphql, http, HttpResponse } from 'msw';
import FooPage from './foo.page';

const store = createStateStore({ modals: {}, form: {}, data: {} });
const executeBehavior = vi.fn();
const openModal = vi.fn((p: any) => { if (p?.id) store.set(`/modals/${p.id}`, true); });
const navigate = vi.fn();
const testHandlers = { navigate, openModal, executeBehavior };

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <JSONUIProvider registry={componentRegistry} store={store} handlers={testHandlers as any}>
        <FooPage />
      </JSONUIProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  store.set('/modals', {});
  store.set('/form', {});
  store.set('/data', {});
  vi.clearAllMocks();
});
```

---

## 🧱 Mocking & MSW Architecture

To prevent huge monolithic mock files and maintain type-safe test environments, we enforce a modular, domain-driven mock architecture:

1. **Shared Fixtures (`src/mocks/fixtures.ts`)**:
   - All global seed data arrays (e.g. `mockSemesters`, `mockClasses`, `mockGradeRecords`) must be declared here.
   - Explicitly type these arrays as `any[]` to allow compiler-safe, flexible dynamic mutations (e.g., storing nulls, dynamically adding file data) without type-narrowing issues.
   - Export helper functions (`resetMock...` and `setMock...`) and mutate arrays in-place using `splice(...)` to preserve the same array references across imported scopes.
2. **Modular Domain Handlers (`src/mocks/handlers/`)**:
   - Interceptors must be separated by domain into modular handler files (e.g. `semester-handlers.ts`, `class-handlers.ts`, `student-handlers.ts`, `grade-handlers.ts`).
3. **Consolidated Aggregator (`src/mocks/handlers.ts`)**:
   - `handlers.ts` serves as a clean routing entrypoint that aggregates domain handlers and re-exports all seed data and reset helpers to maintain **100% backward compatibility** with existing unit tests.
4. **Local Overrides in Tests**:
   - For standard workflows, rely on the global MSW setup.
   - For edge cases (e.g., asserting empty states, validation failure dialogs), use `server.use()` dynamically inside a test block to override global handlers.
   - Use compile-safe optional chaining (`r?.score`) when asserting on query results (e.g., `mockGradeRecords.find(...)`) to prevent `possibly 'undefined'` TypeScript compiler errors.

---

## ✅ Four Mandatory Test Patterns

Every page test file MUST cover all four patterns:

### Pattern 1 — Render
```tsx
it('renders page heading', async () => {
  renderPage();
  expect(await screen.findByRole('heading')).toBeInTheDocument();
});
```

### Pattern 2 — Query (store-based table data)
```tsx
const MOCK_ROWS = [{ id: '1', name: '範例資料', ... }];

it('renders rows when store data is populated', async () => {
  store.set('/data/listFoo', MOCK_ROWS);
  renderPage();
  expect(await screen.findByText('範例資料')).toBeInTheDocument();
});

it('shows empty state when no store data', async () => {
  renderPage();
  expect(await screen.findByText('(沒有資料)')).toBeInTheDocument();
});
```

### Pattern 3 — Modal Open + executeBehavior
```tsx
// Open modal via button click
it('opens form modal when create button is clicked', async () => {
  const user = userEvent.setup();
  renderPage();
  await user.click(await screen.findByRole('button', { name: /建立.*/i }));
  expect(openModal).toHaveBeenCalledWith(expect.objectContaining({ id: 'foo-form-modal' }));
  expect(store.get('/modals/foo-form-modal')).toBe(true);
});

// Submit button calls executeBehavior
it('calls executeBehavior on 儲存', async () => {
  store.set('/modals/foo-form-modal', true);
  const user = userEvent.setup();
  renderPage();
  await user.click(await screen.findByRole('button', { name: /儲存/i }));
  expect(executeBehavior).toHaveBeenCalledWith(
    expect.objectContaining({ ref: 'CreateFoo' })
  );
});

// Modal auto-closes after save/cancel/confirm
it('closes modal after 儲存', async () => {
  store.set('/modals/foo-form-modal', true);
  const user = userEvent.setup();
  renderPage();
  await user.click(await screen.findByRole('button', { name: /儲存/i }));
  await waitFor(() => expect(store.get('/modals/foo-form-modal')).toBe(false));
});
```

### Pattern 4 — Row Actions (need store data to render rows)
```tsx
it('opens edit modal from row action', async () => {
  store.set('/data/listFoo', MOCK_ROWS); // populate DataTable first
  const user = userEvent.setup();
  renderPage();
  await screen.findByText('範例資料');   // wait for row to appear
  await user.click(screen.getByRole('button', { name: /編輯/i }));
  expect(store.get('/modals/foo-form-modal')).toBe(true);
});
```

---

## 🖥️ Commands

```bash
# Run single page test file
cd engineers/03-implementations/frontend
npx vitest run src/pages/<ui_id>.page.test.tsx

# Run all tests
npx vitest run

# Coverage report (must be ≥ 70% line/branch/function)
npx vitest run --coverage
```

---

## ⚠️ Rules

1. **`userEvent` only** — never `fireEvent`. Use `userEvent.setup()` + `await user.click()`.
2. **Store-based table data** — `store.set('/data/listXxx', rows)` to populate DataTable; do NOT rely on GraphQL auto-wiring.
3. **Pre-open modals** — call `store.set('/modals/<id>', true)` BEFORE `renderPage()` to test modal content.
4. **Assert store state** — after every modal-open interaction, assert `store.get('/modals/<id>') === true`.
5. **Assert executeBehavior** — every mutating button (儲存 / 刪除 / 確認) must have a spy assertion.
6. **Assert auto-close** — use `waitFor(() => expect(store.get('/modals/<id>')).toBe(false))` after save/cancel/confirm.
7. **Coverage DoD ≥ 70%** — `npx vitest run --coverage` must pass before the Phase is complete.
