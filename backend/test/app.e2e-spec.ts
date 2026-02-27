import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, ValidationPipe } from '@nestjs/common';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from './../src/app.module';

describe('Auth (e2e)', () => {
  let app: INestApplication<App>;

  beforeEach(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );
    await app.init();
  });

  afterEach(async () => {
    await app.close();
  });

  it('POST /auth/login - credenciales inválidas retorna 401', () => {
    return request(app.getHttpServer())
      .post('/auth/login')
      .send({ email: 'no-existe@test.com', password: 'WrongPass1' })
      .expect(401);
  });

  it('POST /auth/registro - payload inválido retorna 400', () => {
    return request(app.getHttpServer())
      .post('/auth/registro')
      .send({ email: 'no-es-email', password: '123' })
      .expect(400);
  });
});
