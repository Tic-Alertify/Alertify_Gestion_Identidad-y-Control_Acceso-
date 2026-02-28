import {
  ConflictException,
  ForbiddenException,
  HttpException,
  Injectable,
  InternalServerErrorException,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcrypt';
import { createHash, randomUUID } from 'node:crypto';
import { UsuariosService } from '../usuarios/usuarios.service';
import { PrismaService } from '../prisma/prisma.service';
import { RegistroDto } from './dto/registro.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { LogoutDto } from './dto/logout.dto';

const BCRYPT_ROUNDS = 10; // T03: Configurar 10 rounds de salt

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  private readonly accessSecret: string;
  private readonly accessTtl: string;
  private readonly refreshSecret: string;
  private readonly refreshTtl: string;
  private readonly refreshTtlMs: number;

  constructor(
    private readonly usuariosService: UsuariosService,
    private readonly jwtService: JwtService,
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService,
  ) {
    this.accessSecret =
      this.configService.get<string>('JWT_ACCESS_SECRET') ??
      this.configService.get<string>('JWT_SECRET', 'fallback-secret');
    this.accessTtl = this.configService.get<string>('JWT_ACCESS_TTL', '15m');
    this.refreshSecret = this.configService.get<string>(
      'JWT_REFRESH_SECRET',
      'refresh-fallback-secret',
    );
    this.refreshTtl = this.configService.get<string>('JWT_REFRESH_TTL', '7d');
    this.refreshTtlMs = this.parseTtlToMs(this.refreshTtl);
  }

  // ─── Helpers: TTL parsing ──────────────────────────────────────────
  private parseTtlToMs(ttl: string): number {
    const match = ttl.match(/^(\d+)(s|m|h|d)$/);
    if (!match) return 7 * 24 * 60 * 60 * 1000; // default 7d
    const value = parseInt(match[1], 10);
    const unit = match[2];
    const multipliers: Record<string, number> = {
      s: 1000,
      m: 60 * 1000,
      h: 60 * 60 * 1000,
      d: 24 * 60 * 60 * 1000,
    };
    return value * multipliers[unit];
  }

  // ─── T03: Hashing bcrypt ───────────────────────────────────────────
  async hashPassword(password: string): Promise<string> {
    return bcrypt.hash(password, BCRYPT_ROUNDS);
  }

  async comparePassword(
    password: string,
    hash: string,
  ): Promise<boolean> {
    return bcrypt.compare(password, hash);
  }

  // ─── T10: SHA-256 hash para refresh tokens ────────────────────────
  hashRefreshToken(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }

  // ─── T09: Generación de Access Token ──────────────────────────────
  async generateAccessToken(
    user: { id: number; email: string },
    roles: string[],
  ): Promise<string> {
    const payload = {
      sub: user.id,
      email: user.email,
      roles,
    };
    return this.jwtService.sign(payload, {
      secret: this.accessSecret,
      expiresIn: this.accessTtl as any,
    });
  }

  // ─── T10: Generación de Refresh Token ─────────────────────────────
  generateRefreshToken(userId: number): string {
    const payload = {
      sub: userId,
      type: 'refresh',
      jti: randomUUID(),
    };
    return this.jwtService.sign(payload, {
      secret: this.refreshSecret,
      expiresIn: this.refreshTtl as any,
    });
  }

  // ─── T02 + T04 + T05: Registro de usuario ─────────────────────────
  async registro(
    dto: RegistroDto,
  ): Promise<{ message: string; userId: number }> {
    // Verificar email duplicado
    const existingEmail = await this.usuariosService.findByEmail(dto.email);
    if (existingEmail) {
      throw new ConflictException('El email ya está registrado');
    }

    // Verificar username duplicado
    const existingUsername = await this.usuariosService.findByUsername(
      dto.username,
    );
    if (existingUsername) {
      throw new ConflictException(
        'El nombre de usuario no está disponible',
      );
    }

    try {
      // Hashear contraseña (T03)
      const passwordHash = await this.hashPassword(dto.password);

      // Crear usuario con rol CIUDADANO + audit log en transacción (T04)
      const usuario = await this.prisma.$transaction(async (tx) => {
        const newUser = await tx.usuarios.create({
          data: {
            email: dto.email,
            username: dto.username,
            password_hash: passwordHash,
            estado: 'activo',
            user_roles: {
              create: { role_id: 1 }, // CIUDADANO por defecto
            },
          },
        });

        // Registrar en AUDIT_LOG dentro de la misma transacción
        await tx.auditLog.create({
          data: {
            user_id: newUser.id,
            action: 'REGISTRO_USUARIO',
          },
        });

        return newUser;
      });

      return {
        message: 'Usuario registrado exitosamente',
        userId: usuario.id,
      };
    } catch (error) {
      // Re-throw known HTTP exceptions (ConflictException, etc.)
      if (error instanceof HttpException) {
        throw error;
      }
      throw new InternalServerErrorException(
        'Error al registrar el usuario',
      );
    }
  }

  // ─── T08 + T09 + T10: Login ────────────────────────────────────────
  async login(dto: LoginDto): Promise<{
    access_token: string;
    refresh_token: string;
    user: {
      id: number;
      email: string;
      username: string;
      roles: string[];
    };
  }> {
    // Buscar usuario por email con roles
    const usuario = await this.usuariosService.findByEmailWithRoles(
      dto.email,
    );

    if (!usuario) {
      throw new UnauthorizedException({
        message: 'Credenciales inválidas',
        code: 'AUTH_INVALID_CREDENTIALS',
      });
    }

    // Verificar contraseña con bcrypt
    const isMatch = await this.comparePassword(
      dto.password,
      usuario.password_hash,
    );

    if (!isMatch) {
      throw new UnauthorizedException({
        message: 'Credenciales inválidas',
        code: 'AUTH_INVALID_CREDENTIALS',
      });
    }

    // T11: Normalizar estado antes de comparar (trim + lowercase)
    const estado = (usuario.estado ?? '').trim().toLowerCase();

    // T11 + T12: Verificar estado del usuario con códigos diferenciados
    if (estado === 'bloqueado') {
      throw new ForbiddenException({
        message: 'La cuenta está bloqueada.',
        code: 'AUTH_ACCOUNT_BLOCKED',
      });
    }
    if (estado === 'inactivo') {
      throw new ForbiddenException({
        message: 'La cuenta está inactiva.',
        code: 'AUTH_ACCOUNT_INACTIVE',
      });
    }
    if (estado !== 'activo') {
      throw new ForbiddenException({
        message: 'La cuenta no está habilitada.',
        code: 'AUTH_ACCOUNT_INACTIVE',
      });
    }

    // Obtener roles del usuario
    const roles = usuario.user_roles.map((ur) => ur.rol.nombre);

    // Generar JWT (T09)
    const accessToken = await this.generateAccessToken(
      { id: usuario.id, email: usuario.email },
      roles,
    );

    // T10: Generar refresh token y guardar hash en DB
    const refreshToken = this.generateRefreshToken(usuario.id);
    const refreshTokenHash = this.hashRefreshToken(refreshToken);
    const refreshTokenExpiresAt = new Date(Date.now() + this.refreshTtlMs);

    await this.prisma.usuarios.update({
      where: { id: usuario.id },
      data: {
        refresh_token_hash: refreshTokenHash,
        refresh_token_expires_at: refreshTokenExpiresAt,
      },
    });

    // Registrar en AUDIT_LOG (no-bloqueante)
    try {
      await this.prisma.auditLog.create({
        data: {
          user_id: usuario.id,
          action: 'LOGIN_EXITOSO',
        },
      });
    } catch (auditError) {
      // Log interno del error pero no bloquear el login
      this.logger.error('Error al registrar audit log de login', auditError);
    }

    return {
      access_token: accessToken,
      refresh_token: refreshToken,
      user: {
        id: usuario.id,
        email: usuario.email,
        username: usuario.username,
        roles,
      },
    };
  }

  // ─── T10: Refresh ─────────────────────────────────────────────────
  async refresh(
    dto: RefreshDto,
  ): Promise<{ access_token: string; refresh_token: string }> {
    // 1. Verificar firma y expiración del JWT refresh token
    let payload: { sub: number; type: string; jti: string };
    try {
      payload = this.jwtService.verify(dto.refresh_token, {
        secret: this.refreshSecret,
      });
    } catch {
      throw new UnauthorizedException({
        message: 'Sesión expirada. Inicia sesión nuevamente.',
        code: 'AUTH_REFRESH_INVALID',
      });
    }

    if (payload.type !== 'refresh') {
      throw new UnauthorizedException({
        message: 'Sesión expirada. Inicia sesión nuevamente.',
        code: 'AUTH_REFRESH_INVALID',
      });
    }

    // 2. Buscar usuario y validar estado
    const usuario = await this.usuariosService.findByIdWithRoles(payload.sub);
    if (!usuario) {
      throw new UnauthorizedException({
        message: 'Sesión inválida. Inicia sesión nuevamente.',
        code: 'AUTH_REFRESH_INVALID',
      });
    }

    const estado = (usuario.estado ?? '').trim().toLowerCase();

    if (estado === 'bloqueado') {
      throw new ForbiddenException({
        message: 'La cuenta está bloqueada.',
        code: 'AUTH_ACCOUNT_BLOCKED',
      });
    }
    if (estado === 'inactivo') {
      throw new ForbiddenException({
        message: 'La cuenta está inactiva.',
        code: 'AUTH_ACCOUNT_INACTIVE',
      });
    }
    if (estado !== 'activo') {
      throw new ForbiddenException({
        message: 'La cuenta no está habilitada.',
        code: 'AUTH_ACCOUNT_INACTIVE',
      });
    }

    // 3. Verificar hash del refresh token en DB
    const incomingHash = this.hashRefreshToken(dto.refresh_token);
    if (
      !usuario.refresh_token_hash ||
      usuario.refresh_token_hash !== incomingHash ||
      !usuario.refresh_token_expires_at ||
      usuario.refresh_token_expires_at < new Date()
    ) {
      throw new UnauthorizedException({
        message: 'Sesión inválida. Inicia sesión nuevamente.',
        code: 'AUTH_REFRESH_INVALID',
      });
    }

    // 4. Rotación: generar nuevos tokens
    const roles = usuario.user_roles.map((ur) => ur.rol.nombre);
    const newAccessToken = await this.generateAccessToken(
      { id: usuario.id, email: usuario.email },
      roles,
    );
    const newRefreshToken = this.generateRefreshToken(usuario.id);
    const newRefreshHash = this.hashRefreshToken(newRefreshToken);
    const newExpiresAt = new Date(Date.now() + this.refreshTtlMs);

    await this.prisma.usuarios.update({
      where: { id: usuario.id },
      data: {
        refresh_token_hash: newRefreshHash,
        refresh_token_expires_at: newExpiresAt,
      },
    });

    return {
      access_token: newAccessToken,
      refresh_token: newRefreshToken,
    };
  }

  // ─── T10: Logout ──────────────────────────────────────────────────
  async logout(dto: LogoutDto): Promise<{ message: string }> {
    // Intentar decodificar para obtener userId
    let userId: number | null = null;
    try {
      const payload = this.jwtService.verify(dto.refresh_token, {
        secret: this.refreshSecret,
      });
      userId = payload.sub;
    } catch {
      // Token inválido/expirado: igualmente intentar invalidar
      try {
        const decoded = this.jwtService.decode(dto.refresh_token) as {
          sub?: number;
        } | null;
        userId = decoded?.sub ?? null;
      } catch {
        // No se puede decodificar; responder OK igualmente
      }
    }

    if (userId) {
      try {
        await this.prisma.usuarios.update({
          where: { id: userId },
          data: {
            refresh_token_hash: null,
            refresh_token_expires_at: null,
          },
        });
      } catch {
        // Si el usuario no existe, ignorar silenciosamente
        this.logger.warn(`Logout: usuario ${userId} no encontrado en DB`);
      }
    }

    return { message: 'Sesión cerrada' };
  }
}
