import 'reflect-metadata';
import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify';
import { ValidationPipe } from '@nestjs/common';

import { AppModule } from './app.module';
import { HttpExceptionFilter } from './common/filters/http-exception.filter';
import { ResponseInterceptor } from './common/interceptors/response.interceptor';

const logger = new Logger('Bootstrap');

async function bootstrap() {
  const app = await NestFactory.create<NestFastifyApplication>(
    AppModule,
    new FastifyAdapter({ logger: false }), // NestJS logger xử lý thay
  );

  // ─── CORS ─────────────────────────────────────────────────────────────────
  // API Gateway đã strip/dedupe CORS headers từ downstream.
  // enableCors() ở đây đảm bảo Fastify trả đúng headers khi service bị gọi
  // trực tiếp hoặc khi gateway forward lỗi 5xx về browser.
  app.enableCors({
    origin: (origin, cb) => {
      // Cho phép: không có origin (server-to-server), localhost bất kỳ port,
      // hoặc pattern từ env CORS_ALLOWED_ORIGINS (comma-separated)
      const allowedPatterns = (process.env.CORS_ALLOWED_ORIGINS ?? '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);

      if (
        !origin ||
        /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin) ||
        allowedPatterns.includes(origin)
      ) {
        cb(null, true);
      } else {
        logger.warn(`CORS blocked request from origin: ${origin}`);
        cb(null, false);
      }
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS', 'HEAD'],
    allowedHeaders: ['*'],
    exposedHeaders: ['Authorization', 'Content-Type'],
    maxAge: 3600,
  });

  // ─── Global pipes / filters / interceptors ────────────────────────────────
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      forbidNonWhitelisted: true,
    }),
  );
  app.useGlobalFilters(new HttpExceptionFilter());
  app.useGlobalInterceptors(new ResponseInterceptor());

  // ─── Graceful shutdown ────────────────────────────────────────────────────
  app.enableShutdownHooks();

  // ─── Unhandled rejection / exception guards ───────────────────────────────
  process.on('unhandledRejection', (reason) => {
    logger.error(
      `Unhandled Promise Rejection: ${reason instanceof Error ? reason.message : String(reason)}`,
      reason instanceof Error ? reason.stack : undefined,
    );
  });

  process.on('uncaughtException', (err) => {
    logger.error(`Uncaught Exception: ${err.message}`, err.stack);
    // Không exit — để NestJS shutdown hooks chạy nếu cần
  });

  // ─── Start ────────────────────────────────────────────────────────────────
  const port = parseInt(process.env.PORT ?? '8093', 10);
  await app.listen(port, '0.0.0.0');

  const env = process.env.NODE_ENV ?? 'development';
  logger.log(`Promotion Service started  [env=${env}] [port=${port}]`);
  logger.log(`Health:  http://localhost:${port}/health`);
  logger.log(`Ready:   http://localhost:${port}/health/ready`);
}

bootstrap().catch((err) => {
  // Fatal startup error — log rõ trước khi thoát
  new Logger('Bootstrap').error(`Fatal startup error: ${err?.message}`, err?.stack);
  process.exit(1);
});
