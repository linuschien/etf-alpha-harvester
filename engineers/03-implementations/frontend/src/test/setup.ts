import '@testing-library/jest-dom/vitest';
import { beforeAll, afterEach, afterAll } from 'vitest';
import { server } from '../mocks/server';

if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {};
}
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}

const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (elt: Element) => {
  const style = originalGetComputedStyle(elt);
  return new Proxy(style, {
    get(target, prop) {
      if (prop === 'transform' || prop === 'webkitTransform' || prop === 'mozTransform') {
        return (target as any)[prop] || 'none';
      }
      const val = (target as any)[prop];
      return typeof val === 'function' ? val.bind(target) : val;
    },
  });
};

beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
