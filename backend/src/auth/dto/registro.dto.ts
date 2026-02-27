import { IsEmail, IsNotEmpty, IsString, Matches, MaxLength, MinLength } from 'class-validator';

export class RegistroDto {
  @IsEmail({}, { message: 'El formato del email no es válido' })
  @IsNotEmpty({ message: 'El email es obligatorio' })
  email: string;

  @IsString({ message: 'El username debe ser una cadena de texto' })
  @IsNotEmpty({ message: 'El username es obligatorio' })
  @MinLength(4, { message: 'El username debe tener al menos 4 caracteres' })
  @MaxLength(20, { message: 'El username no puede tener más de 20 caracteres' })
  @Matches(/^[a-zA-Z0-9]+$/, {
    message: 'El username solo puede contener caracteres alfanuméricos',
  })
  username: string;

  @IsString({ message: 'La contraseña debe ser una cadena de texto' })
  @IsNotEmpty({ message: 'La contraseña es obligatoria' })
  @MinLength(8, { message: 'La contraseña debe tener al menos 8 caracteres' })
  @Matches(/^(?=.*[A-Z])(?=.*\d).+$/, {
    message:
      'La contraseña debe contener al menos 1 letra mayúscula y 1 número',
  })
  password: string;
}
