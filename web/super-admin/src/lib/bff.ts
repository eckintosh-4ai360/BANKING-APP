import { createBff, platformRule } from '@banking/bff';

/** The platform console reaches only the platform API; tenant customer data is not reachable through this app. */
export const getBff = createBff('admin', 'platform', platformRule);
