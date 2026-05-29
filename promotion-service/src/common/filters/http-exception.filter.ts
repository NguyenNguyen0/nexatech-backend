import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';

@Catch()
export class HttpExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger('HttpExceptionFilter');
  private readonly servicePrefix = 'PRO';

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse();
    const request = ctx.getRequest();
    const correlationId = this.resolveCorrelationId(request);
    const userId = this.resolveUserId(request);

    let status = HttpStatus.INTERNAL_SERVER_ERROR;
    let message = 'Internal server error';
    let error = 'INTERNAL_ERROR';

    // --- Prisma: table/schema missing (P2021) ---
    if (exception instanceof Prisma.PrismaClientKnownRequestError) {
      if (exception.code === 'P2021') {
        status = HttpStatus.SERVICE_UNAVAILABLE;
        error = 'DATABASE_SCHEMA_NOT_READY';
        message = 'Promotion database schema is not ready. Run prisma migrate deploy.';
      } else {
        status = HttpStatus.BAD_REQUEST;
        error = 'DATABASE_REQUEST_ERROR';
        message = exception.message;
      }
      this.logger.error(
        `[${this.buildTag(status)}] ${request.method} ${request.url} correlationId=${correlationId} userId=${userId} → Prisma ${exception.code}: ${message}`,
      );
      response.status(status).send({
        success: false,
        tag: this.buildTag(status),
        error,
        message,
        correlationId,
        timestamp: new Date().toISOString(),
      });
      return;
    }

    // --- Prisma: DB unreachable / connection dropped ---
    if (
      exception instanceof Prisma.PrismaClientInitializationError ||
      exception instanceof Prisma.PrismaClientRustPanicError
    ) {
      status = HttpStatus.SERVICE_UNAVAILABLE;
      error = 'DATABASE_UNAVAILABLE';
      message = 'Database is temporarily unavailable. Please try again shortly.';
      this.logger.error(
        `[${this.buildTag(status)}] ${request.method} ${request.url} correlationId=${correlationId} userId=${userId} → ${error}: ${
          (exception as Error).message
        }`,
        (exception as Error).stack,
      );
      response.status(status).send({
        success: false,
        tag: this.buildTag(status),
        error,
        message,
        correlationId,
        timestamp: new Date().toISOString(),
      });
      return;
    }

    // --- NestJS HttpException (4xx / 5xx từ controllers) ---
    if (exception instanceof HttpException) {
      status = exception.getStatus();
      const body = exception.getResponse();
      if (typeof body === 'object' && body !== null) {
        message = (body as any).message ?? message;
        error = (body as any).error ?? error;
      } else {
        message = body as string;
      }
    } else {
      // Unexpected / unhandled — log full stack
      const err = exception instanceof Error ? exception : new Error(String(exception));
      this.logger.error(
        `[${this.buildTag(status)}] ${request.method} ${request.url} correlationId=${correlationId} userId=${userId} - ${err.message}`,
        err.stack,
      );
    }

    if (status >= 500) {
      this.logger.error(
        `[${this.buildTag(status)}] ${request.method} ${request.url} correlationId=${correlationId} userId=${userId} → ${error}: ${
          Array.isArray(message) ? message.join(', ') : message
        }`,
      );
    } else if (status >= 400) {
      this.logger.warn(
        `[${this.buildTag(status)}] ${request.method} ${request.url} correlationId=${correlationId} userId=${userId} → ${error}: ${
          Array.isArray(message) ? message.join(', ') : message
        }`,
      );
    }

    response.status(status).send({
      success: false,
      tag: this.buildTag(status),
      error,
      message,
      correlationId,
      timestamp: new Date().toISOString(),
    });
  }

  private buildTag(status: number): string {
    return `${this.servicePrefix}-${status}`;
  }

  private resolveCorrelationId(request: any): string {
    const value = request?.headers?.['x-correlation-id'];
    return typeof value === 'string' && value.trim().length > 0 ? value : 'unknown';
  }

  private resolveUserId(request: any): string {
    const value = request?.headers?.['x-user-id'];
    return typeof value === 'string' && value.trim().length > 0 ? value : 'anonymous';
  }
}
