import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';

import appConfig from './config/app.config';
import redisConfig from './config/redis.config';
import kafkaConfig from './config/kafka.config';
import { validate } from './config/env.validation';

import { DatabaseModule } from './database/database.module';
import { HealthModule } from './health/health.module';
import { PromotionModule } from './promotion/promotion.module';
import { FlashSaleModule } from './flash-sale/flash-sale.module';
import { MessagingModule } from './messaging/messaging.module';
import { SchedulerModule } from './scheduler/scheduler.module';
import { OutboxModule } from './outbox/outbox.module';
import { DiscoveryModule } from './discovery/discovery.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [appConfig, redisConfig, kafkaConfig],
      validate,
    }),
    ScheduleModule.forRoot(),
    DatabaseModule,
    HealthModule,
    PromotionModule,
    FlashSaleModule,
    MessagingModule,
    SchedulerModule,
    OutboxModule,
    DiscoveryModule,
  ],
})
export class AppModule {}
