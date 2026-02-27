import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { AuthService } from './auth.service';
import { RegistroDto } from './dto/registro.dto';
import { LoginDto } from './dto/login.dto';

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

  // T08: POST /auth/login
  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(
    @Body() loginDto: LoginDto,
  ): Promise<{
    access_token: string;
    user: {
      id: number;
      email: string;
      username: string;
      roles: string[];
    };
  }> {
    return this.authService.login(loginDto);
  }
}
