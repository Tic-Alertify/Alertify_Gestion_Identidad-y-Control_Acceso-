import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { PrismaClient } from '@prisma/client';
import { PrismaMssql } from '@prisma/adapter-mssql';

@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnModuleDestroy {
  constructor() {
    const adapter = new PrismaMssql({
      server: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT || '1433', 10),
      database: process.env.DB_NAME || 'AlertifyDB',
      user: process.env.DB_USER || '',
      password: process.env.DB_PASSWORD || '',
      options: {
        encrypt: process.env.DB_ENCRYPT === 'true',
        trustServerCertificate: process.env.DB_TRUST_SERVER_CERTIFICATE === 'true',
      },
    });
    super({ adapter });
  }

  async onModuleInit(): Promise<void> {
    await this.$connect();
  }

  async onModuleDestroy(): Promise<void> {
    await this.$disconnect();
  }
}
