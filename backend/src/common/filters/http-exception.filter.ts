import {
  ExceptionFilter,
  Catch,
  ArgumentsHost,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Request, Response } from 'express';

/**
 * T12 – Filtro global de excepciones para respuestas de error estandarizadas.
 *
 * Contrato de respuesta:
 * ```json
 * {
 *   "statusCode": number,
 *   "message":    string | string[],
 *   "error":      string,
 *   "code":       string,
 *   "path":       string,
 *   "requestId":  string,
 *   "timestamp":  string
 * }
 * ```
 *
 * - `code` permite al cliente Android mapear errores a pantallas/mensajes específicos.
 * - `requestId` facilita trazabilidad end-to-end (inyectado por RequestIdMiddleware).
 * - Errores no controlados (no HttpException) devuelven 500 sin exponer stack traces.
 */
@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(GlobalExceptionFilter.name);

  /** Códigos internos por defecto según statusCode HTTP */
  private static readonly DEFAULT_CODES: Record<number, string> = {
    400: 'VALIDATION_ERROR',
    401: 'AUTH_INVALID_CREDENTIALS',
    403: 'AUTH_FORBIDDEN',
    404: 'RESOURCE_NOT_FOUND',
    409: 'RESOURCE_CONFLICT',
    500: 'AUTH_UNEXPECTED_ERROR',
  };

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();

    const requestId: string =
      (request as any).requestId ||
      (request.headers['x-request-id'] as string) ||
      'unknown';
    const path: string = request.originalUrl || request.url;

    let statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
    let message: string | string[] = 'Ocurrió un error inesperado. Intenta nuevamente.';
    let error = 'Internal Server Error';
    let code = 'AUTH_UNEXPECTED_ERROR';
    let details: unknown = undefined;

    if (exception instanceof HttpException) {
      statusCode = exception.getStatus();
      const exceptionResponse = exception.getResponse();

      if (typeof exceptionResponse === 'string') {
        message = exceptionResponse;
      } else if (typeof exceptionResponse === 'object') {
        const res = exceptionResponse as Record<string, unknown>;
        message = (res.message as string | string[]) || message;
        error = (res.error as string) || error;

        // Si la excepción incluye un code explícito, usarlo
        if (res.code && typeof res.code === 'string') {
          code = res.code;
        }

        // Capturar details (validaciones DTO)
        if (res.details) {
          details = res.details;
        }
      }

      // Si no se proporcionó code explícito, asignar por defecto según statusCode
      if (
        typeof (exception.getResponse() as any)?.code !== 'string'
      ) {
        code =
          GlobalExceptionFilter.DEFAULT_CODES[statusCode] ||
          'UNKNOWN_ERROR';
      }
    } else {
      // Error no controlado → log completo internamente, respuesta genérica al cliente
      this.logger.error(
        `[${requestId}] Unhandled exception at ${path}`,
        exception instanceof Error ? exception.stack : String(exception),
      );
    }

    const body: Record<string, unknown> = {
      statusCode,
      message,
      error,
      code,
      path,
      requestId,
      timestamp: new Date().toISOString(),
    };

    // Incluir details solo si existe (errores de validación)
    if (details !== undefined) {
      body.details = details;
    }

    response.status(statusCode).json(body);
  }
}
