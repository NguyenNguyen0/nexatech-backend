import { CallHandler, ExecutionContext, Injectable, NestInterceptor } from '@nestjs/common';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  correlationId: string;
  timestamp: string;
}

@Injectable()
export class ResponseInterceptor<T> implements NestInterceptor<T, ApiResponse<T>> {
  intercept(context: ExecutionContext, next: CallHandler): Observable<ApiResponse<T>> {
    const request = context.switchToHttp().getRequest();
    const correlationId = request?.headers?.['x-correlation-id'] ?? 'unknown';

    return next.handle().pipe(
      map((data) => ({
        success: true,
        data,
        correlationId,
        timestamp: new Date().toISOString(),
      })),
    );
  }
}
