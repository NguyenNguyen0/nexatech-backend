import { plainToInstance } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsString, Max, Min, validateSync } from 'class-validator';

class EnvironmentVariables {
  @IsIn(['development', 'production', 'test'])
  NODE_ENV: string;

  @IsInt()
  @Min(1024)
  @Max(65535)
  PORT: number;

  @IsString()
  DATABASE_URL: string;

  @IsString()
  REDIS_HOST: string;

  @IsInt()
  @Min(1)
  @Max(65535)
  REDIS_PORT: number;

  @IsString()
  KAFKA_BROKERS: string;

  @IsString()
  KAFKA_GROUP_ID: string;

  @IsInt()
  @Min(30)
  RESERVATION_TTL_SECONDS: number;

  @IsOptional()
  @IsString()
  SERVICE_NAME?: string;

  @IsOptional()
  @IsString()
  EUREKA_ENABLED?: string;

  @IsOptional()
  @IsString()
  EUREKA_SERVER_URL?: string;

  @IsOptional()
  @IsString()
  EUREKA_INSTANCE_HOSTNAME?: string;

  @IsOptional()
  @IsString()
  EUREKA_INSTANCE_IP_ADDRESS?: string;

  @IsOptional()
  @IsString()
  EUREKA_INSTANCE_PORT?: string;
}

export function validate(config: Record<string, unknown>) {
  const validated = plainToInstance(EnvironmentVariables, config, {
    enableImplicitConversion: true,
  });

  const errors = validateSync(validated, { skipMissingProperties: false });

  if (errors.length > 0) {
    throw new Error(`Config validation failed:\n${errors.toString()}`);
  }

  return validated;
}
