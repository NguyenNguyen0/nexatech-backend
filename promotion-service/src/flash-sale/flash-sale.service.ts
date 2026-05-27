import { BadRequestException, ConflictException, Injectable } from '@nestjs/common';
import { Prisma, PromotionStatus } from '@prisma/client';
import { PrismaService } from '../database/prisma.service';
import { CreateBulkFlashSaleDto } from './dto/admin-flash-sale.dto';

@Injectable()
export class FlashSaleService {
  constructor(private readonly prisma: PrismaService) {}

  private normalizeCodePrefix(input: string) {
    return input
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9-]+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '');
  }

  private extractCodePrefix(code: string) {
    return code.replace(/-[A-Z0-9]+-\d{3}$/i, '');
  }

  private toAdminResponse(fs: any) {
    const discountPercent = Number(fs.promotion.discountPercent ?? 0);
    const originalPrice = Number(fs.originalPrice);
    const salePrice = Number(fs.salePrice);

    return {
      id: fs.id,
      promotionId: fs.promotionId,
      code: fs.promotion.code,
      codePrefix: this.extractCodePrefix(fs.promotion.code),
      name: fs.promotion.name,
      description: fs.promotion.description ?? undefined,
      status: fs.promotion.status as string,
      discountPercent,
      productId: fs.productId,
      productName: fs.productName ?? undefined,
      productSlug: fs.productSlug ?? undefined,
      productThumbnail: fs.productThumbnail ?? undefined,
      originalPrice,
      salePrice,
      totalStock: fs.totalStock,
      remainingStock: fs.remainingStock,
      soldCount: fs.soldCount,
      soldPercent:
        fs.totalStock > 0
          ? Math.round((fs.soldCount / fs.totalStock) * 100)
          : 0,
      startTime: fs.promotion.startTime.toISOString(),
      endTime: fs.promotion.endTime.toISOString(),
      createdAt: fs.createdAt.toISOString(),
      updatedAt: fs.updatedAt.toISOString(),
    };
  }

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

  async findAllForAdmin(status?: string) {
    const normalizedStatus =
      status && Object.values(PromotionStatus).includes(status as PromotionStatus)
        ? (status as PromotionStatus)
        : undefined;

    const items = await this.prisma.flashSale.findMany({
      where: {
        promotion: {
          type: 'FLASH_SALE',
          ...(normalizedStatus ? { status: normalizedStatus } : {}),
        },
      },
      include: {
        promotion: true,
      },
      orderBy: { createdAt: 'desc' },
    });

    return {
      items: items.map((item) => this.toAdminResponse(item)),
      total: items.length,
    };
  }

  async createBulk(payload: CreateBulkFlashSaleDto) {
    const codePrefix = this.normalizeCodePrefix(payload.codePrefix);
    if (!codePrefix) {
      throw new BadRequestException('codePrefix is required');
    }

    const startTime = new Date(payload.startTime);
    const endTime = new Date(payload.endTime);
    if (Number.isNaN(startTime.getTime()) || Number.isNaN(endTime.getTime())) {
      throw new BadRequestException('Invalid startTime or endTime');
    }
    if (startTime >= endTime) {
      throw new BadRequestException('startTime must be before endTime');
    }

    const productIds = new Set<string>();
    for (const product of payload.products) {
      if (productIds.has(product.productId)) {
        throw new BadRequestException(`Duplicate productId: ${product.productId}`);
      }
      productIds.add(product.productId);
    }

    const overlapping = await this.prisma.flashSale.findFirst({
      where: {
        productId: { in: Array.from(productIds) },
        promotion: {
          type: 'FLASH_SALE',
          status: { in: ['DRAFT', 'ACTIVE', 'PAUSED'] },
          startTime: { lt: endTime },
          endTime: { gt: startTime },
        },
      },
      include: { promotion: true },
    });

    if (overlapping) {
      throw new ConflictException(
        `Product ${overlapping.productName ?? overlapping.productId} already has an overlapping flash sale`,
      );
    }

    const campaignSuffix = Date.now().toString(36).toUpperCase();
    const status = payload.status ?? PromotionStatus.DRAFT;

    const created = await this.prisma.$transaction(async (tx) => {
      const rows = [];

      for (const [index, product] of payload.products.entries()) {
        const discountPercent =
          product.discountPercent ?? payload.defaultDiscountPercent;
        const salePrice =
          product.salePrice ??
          Math.round((product.originalPrice * (100 - discountPercent)) / 100);

        if (salePrice >= product.originalPrice) {
          throw new BadRequestException(
            `salePrice must be lower than originalPrice for ${product.productName}`,
          );
        }

        const promotion = await tx.promotion.create({
          data: {
            code: `${codePrefix}-${campaignSuffix}-${String(index + 1).padStart(3, '0')}`,
            name: payload.name.trim(),
            description: payload.description?.trim() || undefined,
            type: 'FLASH_SALE',
            discountPercent: new Prisma.Decimal(discountPercent),
            startTime,
            endTime,
            status,
            flashSale: {
              create: {
                productId: product.productId,
                productName: product.productName,
                productSlug: product.productSlug,
                productThumbnail: product.productThumbnail,
                originalPrice: new Prisma.Decimal(product.originalPrice),
                salePrice: new Prisma.Decimal(salePrice),
                totalStock: product.totalStock,
                remainingStock: product.totalStock,
              },
            },
          },
          include: {
            flashSale: true,
          },
        });

        rows.push(
          await tx.flashSale.findUnique({
            where: { id: promotion.flashSale!.id },
            include: { promotion: true },
          }),
        );
      }

      return rows;
    });

    return {
      items: created.filter(Boolean).map((item) => this.toAdminResponse(item)),
      total: created.length,
    };
  }

  async deleteCampaign(payload: {
    codePrefix?: string;
    name?: string;
    startTime?: string;
    endTime?: string;
    status?: string;
  }) {
    const codePrefix = this.normalizeCodePrefix(payload.codePrefix ?? '');
    const name = payload.name?.trim();
    const status =
      payload.status && Object.values(PromotionStatus).includes(payload.status as PromotionStatus)
        ? (payload.status as PromotionStatus)
        : undefined;
    const startTime = new Date(payload.startTime ?? '');
    const endTime = new Date(payload.endTime ?? '');

    if (!codePrefix || !name || !status) {
      throw new BadRequestException('codePrefix, name and status are required');
    }
    if (Number.isNaN(startTime.getTime()) || Number.isNaN(endTime.getTime())) {
      throw new BadRequestException('Invalid startTime or endTime');
    }

    const rows = await this.prisma.flashSale.findMany({
      where: {
        promotion: {
          type: 'FLASH_SALE',
          name,
          status,
          startTime,
          endTime,
          OR: [
            { code: codePrefix },
            { code: { startsWith: `${codePrefix}-` } },
          ],
        },
      },
      include: {
        promotion: true,
      },
    });

    const campaignRows = rows.filter(
      (row) =>
        row.promotion.code === codePrefix ||
        this.extractCodePrefix(row.promotion.code) === codePrefix,
    );
    if (campaignRows.length === 0) {
      return { deleted: 0 };
    }

    const flashSaleIds = campaignRows.map((row) => row.id);
    const promotionIds = campaignRows.map((row) => row.promotionId);

    await this.prisma.$transaction(async (tx) => {
      await tx.flashSaleReservation.deleteMany({
        where: {
          flashSaleId: { in: flashSaleIds },
        },
      });
      await tx.promotionRedemption.deleteMany({
        where: {
          promotionId: { in: promotionIds },
        },
      });
      await tx.flashSale.deleteMany({
        where: {
          id: { in: flashSaleIds },
        },
      });
      await tx.promotion.deleteMany({
        where: {
          id: { in: promotionIds },
        },
      });
    });

    return { deleted: campaignRows.length };
  }

  async updateCampaignStatus(payload: {
    codePrefix?: string;
    name?: string;
    startTime?: string;
    endTime?: string;
    currentStatus?: string;
    status: PromotionStatus;
  }) {
    const codePrefix = this.normalizeCodePrefix(payload.codePrefix ?? '');
    const name = payload.name?.trim();
    const startTime = new Date(payload.startTime ?? '');
    const endTime = new Date(payload.endTime ?? '');
    const currentStatus =
      payload.currentStatus && Object.values(PromotionStatus).includes(payload.currentStatus as PromotionStatus)
        ? (payload.currentStatus as PromotionStatus)
        : undefined;

    if (!codePrefix || !name) {
      throw new BadRequestException('codePrefix and name are required');
    }
    if (Number.isNaN(startTime.getTime()) || Number.isNaN(endTime.getTime())) {
      throw new BadRequestException('Invalid startTime or endTime');
    }

    const rows = await this.prisma.flashSale.findMany({
      where: {
        promotion: {
          type: 'FLASH_SALE',
          name,
          startTime,
          endTime,
          ...(currentStatus ? { status: currentStatus } : {}),
          OR: [
            { code: codePrefix },
            { code: { startsWith: `${codePrefix}-` } },
          ],
        },
      },
      include: {
        promotion: true,
      },
    });

    const campaignRows = rows.filter(
      (row) =>
        row.promotion.code === codePrefix ||
        this.extractCodePrefix(row.promotion.code) === codePrefix,
    );
    if (campaignRows.length === 0) {
      return { updated: 0 };
    }

    const promotionIds = campaignRows.map((row) => row.promotionId);
    const result = await this.prisma.promotion.updateMany({
      where: {
        id: { in: promotionIds },
      },
      data: {
        status: payload.status,
      },
    });

    return { updated: result.count };
  }
}
