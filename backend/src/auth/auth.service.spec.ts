import { Test, TestingModule } from '@nestjs/testing';
import {
  ConflictException,
  InternalServerErrorException,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';
import { UsuariosService } from '../usuarios/usuarios.service';
import { PrismaService } from '../prisma/prisma.service';
import { RegistroDto } from './dto/registro.dto';

// ─── Mocks ───────────────────────────────────────────────────────────

const mockUsuariosService = {
  findByEmail: jest.fn(),
  findByUsername: jest.fn(),
  findByEmailWithRoles: jest.fn(),
  findByIdWithRoles: jest.fn(),
};

const txMock = {
  usuarios: { create: jest.fn() },
  auditLog: { create: jest.fn() },
};

const mockPrismaService = {
  $transaction: jest.fn().mockImplementation(async (cb) => cb(txMock)),
  usuarios: { update: jest.fn(), updateMany: jest.fn() },
  auditLog: { create: jest.fn() },
};

const mockJwtService = {
  sign: jest.fn().mockReturnValue('mock-token'),
  verify: jest.fn(),
  decode: jest.fn(),
};

const mockConfigService = {
  get: jest.fn().mockImplementation((key: string, defaultValue?: string) => {
    const values: Record<string, string> = {
      JWT_ACCESS_SECRET: 'test-access-secret',
      JWT_ACCESS_TTL: '15m',
      JWT_REFRESH_SECRET: 'test-refresh-secret',
      JWT_REFRESH_TTL: '7d',
      JWT_SECRET: 'test-secret',
    };
    return values[key] ?? defaultValue;
  }),
};

// ─── Tests ───────────────────────────────────────────────────────────

describe('AuthService - registro()', () => {
  let service: AuthService;

  const validDto: RegistroDto = {
    email: 'nuevo@test.com',
    username: 'nuevousr',
    password: 'Password1',
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: UsuariosService, useValue: mockUsuariosService },
        { provide: PrismaService, useValue: mockPrismaService },
        { provide: JwtService, useValue: mockJwtService },
        { provide: ConfigService, useValue: mockConfigService },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);

    // Spy hashPassword para evitar bcrypt real y acelerar tests
    jest.spyOn(service, 'hashPassword').mockResolvedValue('hashed-password');
  });

  // ─── 1. Email duplicado ──────────────────────────────────────────

  it('debe lanzar ConflictException si el email ya existe', async () => {
    mockUsuariosService.findByEmail.mockResolvedValue({
      id: 1,
      email: validDto.email,
    });

    await expect(service.registro(validDto)).rejects.toThrow(
      ConflictException,
    );
    await expect(service.registro(validDto)).rejects.toThrow(
      'El email ya está registrado',
    );

    expect(mockPrismaService.$transaction).not.toHaveBeenCalled();
  });

  // ─── 2. Username duplicado ───────────────────────────────────────

  it('debe lanzar ConflictException si el username ya existe', async () => {
    mockUsuariosService.findByEmail.mockResolvedValue(null);
    mockUsuariosService.findByUsername.mockResolvedValue({
      id: 2,
      username: validDto.username,
    });

    await expect(service.registro(validDto)).rejects.toThrow(
      ConflictException,
    );
    await expect(service.registro(validDto)).rejects.toThrow(
      'El nombre de usuario no está disponible',
    );

    expect(mockPrismaService.$transaction).not.toHaveBeenCalled();
  });

  // ─── 3. Registro exitoso ─────────────────────────────────────────

  it('debe registrar usuario exitosamente y devolver message + userId', async () => {
    mockUsuariosService.findByEmail.mockResolvedValue(null);
    mockUsuariosService.findByUsername.mockResolvedValue(null);
    txMock.usuarios.create.mockResolvedValue({ id: 123 });
    txMock.auditLog.create.mockResolvedValue({});

    const result = await service.registro(validDto);

    expect(result).toEqual({
      message: 'Usuario registrado exitosamente',
      userId: 123,
    });

    // Verifica que se hasheó la contraseña
    expect(service.hashPassword).toHaveBeenCalledWith(validDto.password);

    // Verifica la creación del usuario con datos correctos
    expect(txMock.usuarios.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        email: validDto.email,
        username: validDto.username,
        password_hash: 'hashed-password',
        estado: 'activo',
        user_roles: {
          create: { role_id: 1 },
        },
      }),
    });

    // Verifica audit log con acción correcta
    expect(txMock.auditLog.create).toHaveBeenCalledWith({
      data: {
        user_id: 123,
        action: 'REGISTRO_USUARIO',
      },
    });
  });

  // ─── 4. HttpException dentro del try se re-lanza ─────────────────

  it('debe re-lanzar HttpException si ocurre dentro de la transacción', async () => {
    mockUsuariosService.findByEmail.mockResolvedValue(null);
    mockUsuariosService.findByUsername.mockResolvedValue(null);

    const conflictError = new ConflictException('Constraint violation');
    txMock.usuarios.create.mockRejectedValue(conflictError);

    await expect(service.registro(validDto)).rejects.toThrow(
      ConflictException,
    );
    await expect(service.registro(validDto)).rejects.toThrow(
      'Constraint violation',
    );
  });

  // ─── 5. Error no-HTTP → InternalServerErrorException ─────────────

  it('debe lanzar InternalServerErrorException para errores no-HTTP', async () => {
    mockUsuariosService.findByEmail.mockResolvedValue(null);
    mockUsuariosService.findByUsername.mockResolvedValue(null);

    txMock.usuarios.create.mockRejectedValue(new Error('boom'));

    await expect(service.registro(validDto)).rejects.toThrow(
      InternalServerErrorException,
    );
    await expect(service.registro(validDto)).rejects.toThrow(
      'Error al registrar el usuario',
    );
  });
});

// ─── Tests: logout() ─────────────────────────────────────────────────

describe('AuthService - logout()', () => {
  let service: AuthService;

  beforeEach(async () => {
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: UsuariosService, useValue: mockUsuariosService },
        { provide: PrismaService, useValue: mockPrismaService },
        { provide: JwtService, useValue: mockJwtService },
        { provide: ConfigService, useValue: mockConfigService },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  // ─── a) Logout con refresh válido → 200 y hash queda null ────────

  it('debe invalidar sesión y devolver mensaje de éxito con token válido', async () => {
    mockJwtService.verify.mockReturnValue({ sub: 42, type: 'refresh' });
    mockPrismaService.usuarios.updateMany.mockResolvedValue({ count: 1 });

    const result = await service.logout('valid-refresh-token');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
    expect(mockJwtService.verify).toHaveBeenCalledWith('valid-refresh-token', {
      secret: 'test-refresh-secret',
    });
    expect(mockPrismaService.usuarios.updateMany).toHaveBeenCalledWith({
      where: { id: 42 },
      data: {
        refresh_token_hash: null,
        refresh_token_expires_at: null,
      },
    });
  });

  // ─── b) Logout con refresh inválido → 401 AUTH_REFRESH_INVALID ───

  it('debe lanzar UnauthorizedException si el token tiene firma inválida', async () => {
    mockJwtService.verify.mockImplementation(() => {
      throw new Error('invalid signature');
    });

    await expect(service.logout('bad-token')).rejects.toThrow(
      UnauthorizedException,
    );
    await expect(service.logout('bad-token')).rejects.toMatchObject({
      response: expect.objectContaining({
        code: 'AUTH_REFRESH_INVALID',
      }),
    });

    expect(mockPrismaService.usuarios.updateMany).not.toHaveBeenCalled();
  });

  it('debe lanzar UnauthorizedException si el token está expirado', async () => {
    mockJwtService.verify.mockImplementation(() => {
      const err = new Error('jwt expired');
      err.name = 'TokenExpiredError';
      throw err;
    });

    await expect(service.logout('expired-token')).rejects.toThrow(
      UnauthorizedException,
    );
  });

  it('debe lanzar UnauthorizedException si el token no es de tipo refresh', async () => {
    mockJwtService.verify.mockReturnValue({ sub: 42, type: 'access' });

    await expect(service.logout('access-token')).rejects.toThrow(
      UnauthorizedException,
    );
    await expect(service.logout('access-token')).rejects.toMatchObject({
      response: expect.objectContaining({
        code: 'AUTH_REFRESH_INVALID',
      }),
    });
  });

  // ─── c) Logout idempotente (hash ya null o usuario no encontrado) → 200 ──

  it('debe responder 200 incluso si el usuario ya no existe en BD (idempotente)', async () => {
    mockJwtService.verify.mockReturnValue({ sub: 999, type: 'refresh' });
    mockPrismaService.usuarios.updateMany.mockRejectedValue(
      new Error('Record to update not found'),
    );

    const result = await service.logout('valid-token-no-user');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
  });

  it('debe responder 200 si se llama con mismo token después de logout previo', async () => {
    mockJwtService.verify.mockReturnValue({ sub: 42, type: 'refresh' });
    mockPrismaService.usuarios.updateMany.mockResolvedValue({ count: 1 });

    // Primer logout
    const result1 = await service.logout('same-refresh-token');
    expect(result1).toEqual({ message: 'Sesión cerrada correctamente' });

    // Segundo logout (hash ya era null, update es no-op)
    const result2 = await service.logout('same-refresh-token');
    expect(result2).toEqual({ message: 'Sesión cerrada correctamente' });
  });
});
