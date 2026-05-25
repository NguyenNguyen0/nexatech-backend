import { registerAs } from '@nestjs/config';

export default registerAs('kafka', () => ({
  brokers: process.env.KAFKA_BROKERS?.split(',') ?? ['localhost:9092'],
  groupId: process.env.KAFKA_GROUP_ID,
}));
