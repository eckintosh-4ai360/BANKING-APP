import { createBff, institutionRule } from '@banking/bff';

/** The CMS talks to the tenant API only; the platform API is unreachable through this app's proxy. */
export const getBff = createBff('cms', 'staff', institutionRule);
