import { Module } from '@nestjs/common';
import { AdminFlashSaleController, FlashSaleController } from './flash-sale.controller';
import { FlashSaleService } from './flash-sale.service';

@Module({
  controllers: [FlashSaleController, AdminFlashSaleController],
  providers: [FlashSaleService],
})
export class FlashSaleModule {}
