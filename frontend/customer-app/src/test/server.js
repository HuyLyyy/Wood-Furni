/**
 * MSW Node server — used by all customer-app tests.
 *
 * Tests import `server`, call `server.use(...)` for one-off overrides,
 * and `resetHandlers()` between cases (handled in setup.js).
 */
import { setupServer } from 'msw/node';
import { handlers } from './handlers.js';

export const server = setupServer(...handlers);
