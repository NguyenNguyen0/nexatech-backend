import {
  ArrayMinSize,
  IsArray,
  IsEnum,
  IsISO8601,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  Min,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import { PromotionStatus } from '@prisma/client';

export class BulkFlashSaleProductDto {
  @IsString()
  productId: string;

  @IsString()
  productName: string;

  @IsOptional()
  @IsString()
  productSlug?: string;

  @IsOptional()
  @IsString()
  productThumbnail?: string;

  @Type(() => Number)
  @IsNumber()
  @Min(1)
  originalPrice: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  @Max(90)
  discountPercent?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  salePrice?: number;

  @Type(() => Number)
  @IsNumber()
  @Min(1)
  totalStock: number;
}

export class CreateBulkFlashSaleDto {
  @IsString()
  name: string;

  @IsString()
  codePrefix: string;

  @IsOptional()
  @IsString()
  description?: string;

  @Type(() => Number)
  @IsNumber()
  @Min(1)
  @Max(90)
  defaultDiscountPercent: number;

  @IsISO8601()
  startTime: string;

  @IsISO8601()
  endTime: string;

  @IsOptional()
  @IsEnum(PromotionStatus)
  status?: PromotionStatus;

  @IsArray()
  @ArrayMinSize(1)
  @ValidateNested({ each: true })
  @Type(() => BulkFlashSaleProductDto)
  products: BulkFlashSaleProductDto[];
}

export class UpdateFlashSaleCampaignStatusDto {
  @IsEnum(PromotionStatus)
  status: PromotionStatus;
}
