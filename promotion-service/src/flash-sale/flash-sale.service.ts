import { Injectable } from '@nestjs/common';
import { PrismaService } from '../database/prisma.service';

@Injectable()
export class FlashSaleService {
  constructor(private readonly prisma: PrismaService) {}

  async decrementStockById(id: string, quantity: number = 1) {
    const now = new Date();
    const result = await this.prisma.flashSale.updateMany({
      where: {
        id,
        remainingStock: { gte: quantity },
        promotion: {
          status: 'ACTIVE',
          startTime: { lte: now },
          endTime: { gte: now },
        },
      },
      data: {
        remainingStock: { decrement: quantity },
        soldCount: { increment: quantity },
      },
    });
    return { updated: result.count };
  }

  async decrementStock(productId: string, quantity: number = 1) {
    const now = new Date();
    const result = await this.prisma.flashSale.updateMany({
      where: {
        productId,
        remainingStock: { gte: quantity },
        promotion: {
          status: 'ACTIVE',
          startTime: { lte: now },
          endTime: { gte: now },
        },
      },
      data: {
        remainingStock: { decrement: quantity },
        soldCount: { increment: quantity },
      },
    });
    return { updated: result.count };
  }

  async findActive() {
    const now = new Date();

    const items = await this.prisma.flashSale.findMany({
      where: {
        promotion: {
          status: 'ACTIVE',
          startTime: { lte: now },
          endTime: { gte: now },
        },
      },
      include: {
        promotion: {
          select: {
            code: true,
            name: true,
            description: true,
            discountPercent: true,
            startTime: true,
            endTime: true,
            status: true,
          },
        },
      },
      orderBy: { createdAt: 'desc' },
    });

    return items.map((fs) => ({
      id: fs.id,
      promotionId: fs.promotionId,
      productId: fs.productId,
      productName: fs.productName ?? undefined,
      productSlug: fs.productSlug ?? undefined,
      productThumbnail: fs.productThumbnail ?? undefined,
      originalPrice: Number(fs.originalPrice),
      salePrice: Number(fs.salePrice),
      discountPercent: Number(fs.promotion.discountPercent ?? 0),
      totalStock: fs.totalStock,
      remainingStock: fs.remainingStock,
      soldCount: fs.soldCount,
      soldPercent:
        fs.totalStock > 0
          ? Math.round((fs.soldCount / fs.totalStock) * 100)
          : 0,
      startTime: fs.promotion.startTime.toISOString(),
      endTime: fs.promotion.endTime.toISOString(),
      status: fs.promotion.status as string,
      promotion: {
        code: fs.promotion.code,
        name: fs.promotion.name,
        description: fs.promotion.description ?? undefined,
      },
    }));
  }
}
