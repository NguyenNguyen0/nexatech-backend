import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { PrismaService } from '../database/prisma.service';

@Controller('health')
export class HealthController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  check() {
    return { status: 'ok', service: 'promotion-service' };
  }

  @Get('ready')
  async ready() {
    try {
      await this.prisma.$queryRaw`SELECT 1`;
      return { status: 'ok', service: 'promotion-service', database: 'ready' };
    } catch {
      throw new ServiceUnavailableException({
        error: 'DATABASE_SCHEMA_NOT_READY',
        message: 'Promotion database schema is not ready. Run prisma migrate deploy.',
      });
    }
  }
}
