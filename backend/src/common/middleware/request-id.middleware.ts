import { Injectable, NestMiddleware } from '@nestjs/common';
import { Request, Response, NextFunction } from 'express';
import { randomUUID } from 'crypto';

/**
 * T12 – Middleware que asigna un identificador único a cada request.
 *
 * Flujo:
 * 1. Si el cliente envía el header `x-request-id`, lo respeta.
 * 2. Si no existe, genera uno con `crypto.randomUUID()` (v4).
 * 3. Adjunta el id al objeto `req` para que esté disponible en
 *    el resto del pipeline (filtros, guards, interceptors).
 * 4. Devuelve el mismo id en el header de respuesta para trazabilidad.
 */
@Injectable()
export class RequestIdMiddleware implements NestMiddleware {
  use(req: Request, res: Response, next: NextFunction): void {
    const requestId =
      (req.headers['x-request-id'] as string) || randomUUID();

    // Adjuntar al request para que GlobalExceptionFilter pueda leerlo
    (req as any).requestId = requestId;

    // Devolver en response para trazabilidad end-to-end
    res.setHeader('x-request-id', requestId);

    next();
  }
}
