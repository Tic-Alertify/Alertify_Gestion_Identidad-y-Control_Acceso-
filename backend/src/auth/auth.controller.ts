import {
  Body,
  Controller,
  Headers,
  HttpCode,
  HttpStatus,
  Post,
} from '@nestjs/common';
import { AuthService } from './auth.service';
import { RegistroDto } from './dto/registro.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { LogoutDto } from './dto/logout.dto';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  // T02: POST /auth/registro
  @Post('registro')
  @HttpCode(HttpStatus.CREATED)
  async registro(
    @Body() registroDto: RegistroDto,
  ): Promise<{ message: string; userId: number }> {
    return this.authService.registro(registroDto);
  }

  // T08 + T10: POST /auth/login
  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(
    @Body() loginDto: LoginDto,
  ): Promise<{
    access_token: string;
    refresh_token: string;
    user: {
      id: number;
      email: string;
      username: string;
      roles: string[];
    };
  }> {
    return this.authService.login(loginDto);
  }

  // T10: POST /auth/refresh
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  async refresh(
    @Body() refreshDto: RefreshDto,
  ): Promise<{ access_token: string; refresh_token: string }> {
    return this.authService.refresh(refreshDto);
  }

  // T15 + T16: POST /auth/logout
  @Post('logout')
  @HttpCode(HttpStatus.OK)
  async logout(
    @Headers('authorization') authHeader: string | undefined,
    @Body() logoutDto: LogoutDto,
  ): Promise<{ message: string }> {
    // Extraer access token del header Authorization (opcional)
    let accessToken: string | undefined;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      accessToken = authHeader.slice(7);
    }
    return this.authService.logout(logoutDto.refresh_token, accessToken);
  }
}
