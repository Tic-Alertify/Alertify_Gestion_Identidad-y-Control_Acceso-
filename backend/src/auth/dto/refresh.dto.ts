import { IsNotEmpty, IsString } from 'class-validator';

export class RefreshDto {
  @IsString({ message: 'El refresh token debe ser una cadena de texto' })
  @IsNotEmpty({ message: 'El refresh token es obligatorio' })
  refresh_token: string;
}
