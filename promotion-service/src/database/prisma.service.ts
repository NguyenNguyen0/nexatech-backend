import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';

const MAX_RETRIES = 10;
const RETRY_DELAY_MS = 3000;

@Injectable()
export class PrismaService extends PrismaClient implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(PrismaService.name);

  async onModuleInit() {
    await this.connectWithRetry();
  }

  async onModuleDestroy() {
    await this.$disconnect();
    this.logger.log('Disconnected from database');
  }

  private async connectWithRetry(attempt = 1): Promise<void> {
    try {
      await this.$connect();
      this.logger.log('Connected to database successfully');
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      if (attempt >= MAX_RETRIES) {
        this.logger.error(
          `Failed to connect to database after ${MAX_RETRIES} attempts: ${message}`,
        );
        // Không throw — service vẫn start, health/ready endpoint sẽ báo trạng thái
        return;
      }
      this.logger.warn(
        `Database connection attempt ${attempt}/${MAX_RETRIES} failed: ${message}. Retrying in ${RETRY_DELAY_MS}ms…`,
      );
      await new Promise((r) => setTimeout(r, RETRY_DELAY_MS));
      return this.connectWithRetry(attempt + 1);
    }
  }
}
