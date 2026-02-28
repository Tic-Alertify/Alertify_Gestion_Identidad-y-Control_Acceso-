import {
  ConflictException,
  ForbiddenException,
  HttpException,
  Injectable,
  InternalServerErrorException,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcrypt';
import { UsuariosService } from '../usuarios/usuarios.service';
import { PrismaService } from '../prisma/prisma.service';
import { RegistroDto } from './dto/registro.dto';
import { LoginDto } from './dto/login.dto';

const BCRYPT_ROUNDS = 10; // T03: Configurar 10 rounds de salt

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly usuariosService: UsuariosService,
    private readonly jwtService: JwtService,
    private readonly prisma: PrismaService,
  ) { }

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

  // ─── T09: Generación de JWT ────────────────────────────────────────
  async generateAccessToken(
    user: { id: number; email: string },
    roles: string[],
  ): Promise<string> {
    const payload = {
      sub: user.id,
      email: user.email,
      roles,
    };
    return this.jwtService.sign(payload);
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

  // ─── T08 + T09: Login ─────────────────────────────────────────────
  async login(dto: LoginDto): Promise<{
    access_token: string;
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
      user: {
        id: usuario.id,
        email: usuario.email,
        username: usuario.username,
        roles,
      },
    };
  }
}
