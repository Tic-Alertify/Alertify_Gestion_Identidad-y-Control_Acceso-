import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { ValidationPipe } from '@nestjs/common';
import { GlobalExceptionFilter } from './common/filters/http-exception.filter';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Habilitar validación global con class-validator
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  // Filtro de excepciones global
  app.useGlobalFilters(new GlobalExceptionFilter());

  // Habilitar CORS para futuro frontend móvil
  app.enableCors();

  await app.listen(process.env.PORT ?? 3000);
  console.log(`🚀 Servidor Alertify ejecutándose en http://localhost:${process.env.PORT ?? 3000}`);
}
bootstrap();
