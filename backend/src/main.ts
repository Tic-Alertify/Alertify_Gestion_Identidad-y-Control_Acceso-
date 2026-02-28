import 'dotenv/config'; // Cargar variables de entorno ANTES de cualquier import
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { BadRequestException, ValidationPipe } from '@nestjs/common';
import { GlobalExceptionFilter } from './common/filters/http-exception.filter';
import { RequestIdMiddleware } from './common/middleware/request-id.middleware';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // T12: Middleware de request-id (antes de cualquier route)
  const requestIdMiddleware = new RequestIdMiddleware();
  app.use(requestIdMiddleware.use.bind(requestIdMiddleware));

  // Habilitar validación global con class-validator
  // T12: exceptionFactory personalizada para incluir code: VALIDATION_ERROR
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      exceptionFactory: (errors) => {
        const messages = errors.flatMap((err) =>
          Object.values(err.constraints || {}),
        );
        return new BadRequestException({
          message: messages,
          code: 'VALIDATION_ERROR',
        });
      },
    }),
  );

  // Filtro de excepciones global
  app.useGlobalFilters(new GlobalExceptionFilter());

  // Habilitar CORS para futuro frontend móvil
  app.enableCors();

  await app.listen(process.env.PORT ?? 3000);
  console.log(`Servidor Alertify ejecutándose en http://localhost:${process.env.PORT ?? 3000}`);
}
bootstrap();
