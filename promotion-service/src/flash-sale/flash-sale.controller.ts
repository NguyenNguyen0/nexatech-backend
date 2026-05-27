import { Controller, Get, Patch, Param, Body, ParseIntPipe, DefaultValuePipe } from '@nestjs/common';
import { FlashSaleService } from './flash-sale.service';

@Controller('api/v1/flash-sales')
export class FlashSaleController {
  constructor(private readonly flashSaleService: FlashSaleService) {}

  @Get()
  async getActive() {
    const items = await this.flashSaleService.findActive();
    return { items, total: items.length };
  }

  /** Decrement remainingStock for a concrete flash sale after confirmed payment */
  @Patch(':id/sell')
  async sellByFlashSale(
    @Param('id') id: string,
    @Body('quantity', new DefaultValuePipe(1), ParseIntPipe) quantity: number,
  ) {
    return this.flashSaleService.decrementStockById(id, quantity);
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
