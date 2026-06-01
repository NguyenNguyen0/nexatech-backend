import { registerAs } from '@nestjs/config';

export default registerAs('app', () => ({
  env: process.env.NODE_ENV,
  port: parseInt(process.env.PORT, 10),
  reservationTtlSeconds: parseInt(process.env.RESERVATION_TTL_SECONDS, 10),
  keycloakIssuerUri: process.env.KEYCLOAK_ISSUER_URI ?? 'http://localhost:9000/realms/nexatech',
  keycloakJwksUri:
    process.env.KEYCLOAK_JWKS_URI ??
    `${process.env.KEYCLOAK_ISSUER_URI ?? 'http://localhost:9000/realms/nexatech'}/protocol/openid-connect/certs`,
}));
