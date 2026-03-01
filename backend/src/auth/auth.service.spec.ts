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
  usuarios: { create: jest.fn(), update: jest.fn(), updateMany: jest.fn() },
  auditLog: { create: jest.fn() },
  jwtBlacklist: { create: jest.fn() },
};

const mockPrismaService = {
  $transaction: jest.fn().mockImplementation(async (cb) => cb(txMock)),
  usuarios: { update: jest.fn(), updateMany: jest.fn() },
  auditLog: { create: jest.fn() },
  jwtBlacklist: {
    create: jest.fn(),
    findUnique: jest.fn(),
    deleteMany: jest.fn(),
  },
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
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });

    const result = await service.logout('valid-refresh-token');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
    expect(mockJwtService.verify).toHaveBeenCalledWith('valid-refresh-token', {
      secret: 'test-refresh-secret',
    });
    expect(txMock.usuarios.updateMany).toHaveBeenCalledWith({
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

    expect(txMock.usuarios.updateMany).not.toHaveBeenCalled();
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
    txMock.usuarios.updateMany.mockRejectedValue(
      new Error('Record to update not found'),
    );

    const result = await service.logout('valid-token-no-user');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
  });

  it('debe responder 200 si se llama con mismo token después de logout previo', async () => {
    mockJwtService.verify.mockReturnValue({ sub: 42, type: 'refresh' });
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });

    // Primer logout
    const result1 = await service.logout('same-refresh-token');
    expect(result1).toEqual({ message: 'Sesión cerrada correctamente' });

    // Segundo logout (hash ya era null, update es no-op)
    const result2 = await service.logout('same-refresh-token');
    expect(result2).toEqual({ message: 'Sesión cerrada correctamente' });
  });
});

// ─── Tests: T16 – logout() con blacklist de access token ─────────────

describe('AuthService - logout() + T16 blacklist', () => {
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

  // ─── T16-a) Logout con access token → blacklist jti ──────────────

  it('debe blacklistear el access token cuando se proporciona junto al refresh', async () => {
    // Refresh verify
    mockJwtService.verify
      .mockReturnValueOnce({ sub: 42, type: 'refresh' })   // 1st call: refresh
      .mockReturnValueOnce({                                // 2nd call: access
        jti: 'uuid-abc-123',
        sub: 42,
        exp: Math.floor(Date.now() / 1000) + 900,
      });
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });
    txMock.jwtBlacklist.create.mockResolvedValue({});

    const result = await service.logout('valid-refresh', 'valid-access');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
    expect(txMock.jwtBlacklist.create).toHaveBeenCalledWith({
      data: {
        jti: 'uuid-abc-123',
        user_id: 42,
        expires_at: expect.any(Date),
      },
    });
  });

  // ─── T16-b) Logout sin access token → no blacklistea ─────────────

  it('no debe llamar jwtBlacklist.create cuando no se pasa access token', async () => {
    mockJwtService.verify.mockReturnValue({ sub: 42, type: 'refresh' });
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });

    const result = await service.logout('valid-refresh');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
    expect(txMock.jwtBlacklist.create).not.toHaveBeenCalled();
  });

  // ─── T16-c) Access token expirado/inválido → no falla, 200 ───────

  it('debe responder 200 si el access token es inválido o expirado', async () => {
    // Refresh verify OK, access verify throws
    mockJwtService.verify
      .mockReturnValueOnce({ sub: 42, type: 'refresh' })
      .mockImplementationOnce(() => { throw new Error('jwt expired'); });
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });

    const result = await service.logout('valid-refresh', 'expired-access');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
    expect(txMock.jwtBlacklist.create).not.toHaveBeenCalled();
  });

  // ─── T16-d) Dupplicado P2002 → idempotente, 200 ──────────────────

  it('debe responder 200 si el jti ya está en blacklist (P2002 unique violation)', async () => {
    mockJwtService.verify
      .mockReturnValueOnce({ sub: 42, type: 'refresh' })
      .mockReturnValueOnce({
        jti: 'uuid-duplicado',
        sub: 42,
        exp: Math.floor(Date.now() / 1000) + 900,
      });
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });

    const p2002Error = Object.assign(new Error('Unique constraint'), {
      code: 'P2002',
    });
    txMock.jwtBlacklist.create.mockRejectedValue(p2002Error);

    const result = await service.logout('valid-refresh', 'dup-access');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
  });

  // ─── T16-e) Access sin jti → no blacklistea ──────────────────────

  it('no debe blacklistear si el access token no contiene jti', async () => {
    mockJwtService.verify
      .mockReturnValueOnce({ sub: 42, type: 'refresh' })
      .mockReturnValueOnce({ sub: 42, exp: Math.floor(Date.now() / 1000) + 900 }); // no jti
    txMock.usuarios.updateMany.mockResolvedValue({ count: 1 });

    const result = await service.logout('valid-refresh', 'access-sin-jti');

    expect(result).toEqual({ message: 'Sesión cerrada correctamente' });
    expect(txMock.jwtBlacklist.create).not.toHaveBeenCalled();
  });
});

// ─── Tests: T16 – isTokenBlacklisted() ───────────────────────────────

describe('AuthService - isTokenBlacklisted()', () => {
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

  it('debe retornar true si el jti está en blacklist y no ha expirado', async () => {
    mockPrismaService.jwtBlacklist.findUnique.mockResolvedValue({
      jti: 'abc-123',
      expires_at: new Date(Date.now() + 60_000), // expira en 1 min
    });

    const result = await service.isTokenBlacklisted('abc-123');
    expect(result).toBe(true);
    expect(mockPrismaService.jwtBlacklist.findUnique).toHaveBeenCalledWith({
      where: { jti: 'abc-123' },
    });
  });

  it('debe retornar false si el jti no está en blacklist', async () => {
    mockPrismaService.jwtBlacklist.findUnique.mockResolvedValue(null);

    const result = await service.isTokenBlacklisted('no-existe');
    expect(result).toBe(false);
  });

  it('debe retornar false si la entrada ya expiró', async () => {
    mockPrismaService.jwtBlacklist.findUnique.mockResolvedValue({
      jti: 'expired-jti',
      expires_at: new Date(Date.now() - 60_000), // expiró hace 1 min
    });

    const result = await service.isTokenBlacklisted('expired-jti');
    expect(result).toBe(false);
  });
});

// ─── Tests: T16 – generateAccessToken() incluye jti ─────────────────

describe('AuthService - generateAccessToken() con jti', () => {
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

  it('debe incluir jti (UUID) en el payload del access token', async () => {
    mockJwtService.sign.mockReturnValue('signed-token');

    await service.generateAccessToken(
      { id: 1, email: 'test@test.com' },
      ['CIUDADANO'],
    );

    expect(mockJwtService.sign).toHaveBeenCalledWith(
      expect.objectContaining({
        sub: 1,
        email: 'test@test.com',
        roles: ['CIUDADANO'],
        jti: expect.stringMatching(
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
        ),
      }),
      { secret: 'test-access-secret', expiresIn: '15m' },
    );
  });
});
