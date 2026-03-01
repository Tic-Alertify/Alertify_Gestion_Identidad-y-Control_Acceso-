import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { PrismaService } from '../prisma/prisma.service';

/**
 * T16 – Limpieza periódica de la tabla JWT_BLACKLIST.
 *
 * Borra registros cuyo `expires_at` ya pasó, ya que esos tokens
 * habrían sido rechazados por expiración JWT de todas formas.
 *
 * Programación por defecto: cada hora.
 */
@Injectable()
export class JwtBlacklistCleanupService {
  private readonly logger = new Logger(JwtBlacklistCleanupService.name);

  constructor(private readonly prisma: PrismaService) {}

  @Cron(CronExpression.EVERY_HOUR)
  async handleCleanup(): Promise<void> {
    const MAX_RETRIES = 2;
    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        const result = await this.prisma.jwtBlacklist.deleteMany({
          where: { expires_at: { lt: new Date() } },
        });
        if (result.count > 0) {
          this.logger.log(
            `Blacklist cleanup: ${result.count} entradas expiradas eliminadas`,
          );
        }
        return; // éxito → salir
      } catch (error) {
        const isTimeout =
          error &&
          typeof error === 'object' &&
          'code' in error &&
          (error as any).code === 'ETIMEOUT';

        if (isTimeout && attempt < MAX_RETRIES) {
          this.logger.warn(
            `Blacklist cleanup: ETIMEOUT (intento ${attempt}/${MAX_RETRIES}), reintentando...`,
          );
          await new Promise((r) => setTimeout(r, 3000));
          continue;
        }
        this.logger.error('Error en limpieza de JWT blacklist', error);
      }
    }
  }
}
