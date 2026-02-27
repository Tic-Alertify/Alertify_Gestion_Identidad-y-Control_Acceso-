import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { Usuarios } from '@prisma/client';

@Injectable()
export class UsuariosService {
  constructor(private readonly prisma: PrismaService) {}

  async findByEmail(email: string): Promise<Usuarios | null> {
    return this.prisma.usuarios.findUnique({
      where: { email },
    });
  }

  async findByUsername(username: string): Promise<Usuarios | null> {
    return this.prisma.usuarios.findUnique({
      where: { username },
    });
  }

  async findByIdWithRoles(
    id: number,
  ): Promise<
    (Usuarios & { user_roles: { rol: { nombre: string } }[] }) | null
  > {
    return this.prisma.usuarios.findUnique({
      where: { id },
      include: {
        user_roles: {
          include: {
            rol: true,
          },
        },
      },
    });
  }

  async findByEmailWithRoles(
    email: string,
  ): Promise<
    (Usuarios & { user_roles: { rol: { nombre: string } }[] }) | null
  > {
    return this.prisma.usuarios.findUnique({
      where: { email },
      include: {
        user_roles: {
          include: {
            rol: true,
          },
        },
      },
    });
  }

  // Eliminado: createUsuario() - no se utiliza, la transacción se maneja en AuthService
}
