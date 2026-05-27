import {
  Body,
  Controller,
  Delete,
  DefaultValuePipe,
  Get,
  Param,
  ParseIntPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { FlashSaleService } from './flash-sale.service';
import {
  CreateBulkFlashSaleDto,
  UpdateFlashSaleCampaignStatusDto,
} from './dto/admin-flash-sale.dto';

@Controller('api/v1/flash-sales')
export class FlashSaleController {
  constructor(private readonly flashSaleService: FlashSaleService) {}

  @Get()
  async getActive() {
    const items = await this.flashSaleService.findActive();
    return { items, total: items.length };
  }

  /** Decrement remainingStock after a confirmed sale */
  @Patch('by-product/:productId/sell')
  async sell(
    @Param('productId') productId: string,
    @Body('quantity', new DefaultValuePipe(1), ParseIntPipe) quantity: number,
  ) {
    return this.flashSaleService.decrementStock(productId, quantity);
  }
}

@Controller('api/v1/admin/flash-sales')
export class AdminFlashSaleController {
  constructor(private readonly flashSaleService: FlashSaleService) {}

  @Get()
  async list(@Query('status') status?: string) {
    return this.flashSaleService.findAllForAdmin(status);
  }

  @Post('bulk')
  async createBulk(@Body() payload: CreateBulkFlashSaleDto) {
    return this.flashSaleService.createBulk(payload);
  }

  @Patch('campaign/status')
  async updateCampaignStatus(
    @Query('codePrefix') codePrefix: string,
    @Query('name') name: string,
    @Query('startTime') startTime: string,
    @Query('endTime') endTime: string,
    @Query('currentStatus') currentStatus: string | undefined,
    @Body() payload: UpdateFlashSaleCampaignStatusDto,
  ) {
    return this.flashSaleService.updateCampaignStatus({
      codePrefix,
      name,
      startTime,
      endTime,
      currentStatus,
      status: payload.status,
    });
  }

  @Delete('campaign')
  async deleteCampaign(
    @Query('codePrefix') codePrefix: string,
    @Query('name') name: string,
    @Query('startTime') startTime: string,
    @Query('endTime') endTime: string,
    @Query('status') status: string,
  ) {
    return this.flashSaleService.deleteCampaign({
      codePrefix,
      name,
      startTime,
      endTime,
      status,
    });
  }
}
