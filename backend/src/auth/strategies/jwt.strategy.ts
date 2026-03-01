import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { ConfigService } from '@nestjs/config';
import { AuthService } from '../auth.service';

export interface JwtPayload {
  sub: number;
  email: string;
  roles: string[];
  jti: string;
  iat: number;
  exp: number;
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    private readonly configService: ConfigService,
    private readonly authService: AuthService,
  ) {
    const secret =
      configService.get<string>('JWT_ACCESS_SECRET') ??
      configService.get<string>('JWT_SECRET');
    if (!secret) {
      throw new Error('JWT_ACCESS_SECRET o JWT_SECRET no está configurado en las variables de entorno');
    }
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: secret,
    });
  }

  async validate(payload: JwtPayload): Promise<JwtPayload> {
    if (!payload.sub || !payload.jti) {
      throw new UnauthorizedException({
        message: 'Token inválido',
        code: 'AUTH_INVALID_TOKEN',
      });
    }

    // T16: Verificar si el token fue revocado (blacklist)
    const isBlacklisted = await this.authService.isTokenBlacklisted(
      payload.jti,
    );
    if (isBlacklisted) {
      throw new UnauthorizedException({
        message: 'Sesión cerrada. Inicia sesión nuevamente.',
        code: 'AUTH_TOKEN_REVOKED',
      });
    }

    return {
      sub: payload.sub,
      email: payload.email,
      roles: payload.roles,
      jti: payload.jti,
      iat: payload.iat,
      exp: payload.exp,
    };
  }
}
