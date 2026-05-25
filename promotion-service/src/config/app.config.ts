import { registerAs } from '@nestjs/config';

export default registerAs('app', () => ({
  env: process.env.NODE_ENV,
  port: parseInt(process.env.PORT, 10),
  reservationTtlSeconds: parseInt(process.env.RESERVATION_TTL_SECONDS, 10),
}));
