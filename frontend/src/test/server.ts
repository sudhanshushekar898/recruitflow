import { setupServer } from 'msw/node'

/** Network is mocked at the HTTP layer; tests add handlers per case and unhandled requests fail the test. */
export const server = setupServer()
