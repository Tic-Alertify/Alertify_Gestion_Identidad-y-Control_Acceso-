import 'dotenv/config';
import { PrismaClient } from '@prisma/client';
import { PrismaMssql } from '@prisma/adapter-mssql';

const adapter = new PrismaMssql({
  server: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '1433', 10),
  database: process.env.DB_NAME || 'AlertifyDB',
  user: process.env.DB_USER || '',
  password: process.env.DB_PASSWORD || '',
  options: {
    encrypt: process.env.DB_ENCRYPT === 'true',
    trustServerCertificate : process.env.DB_TRUST_SERVER_CERTIFICATE === 'true',
  },
});

const prisma = new PrismaClient({ adapter });

async function main() {
  console.log('🌱 Seeding database...');

  // Crear roles predefinidos
  const ciudadano = await prisma.roles.upsert({
    where: { nombre: 'CIUDADANO' },
    update: {},
    create: {
      nombre: 'CIUDADANO',
      descripcion: 'Rol por defecto para ciudadanos',
    },
  });

  const administrador = await prisma.roles.upsert({
    where: { nombre: 'ADMINISTRADOR' },
    update: {},
    create: {
      nombre: 'ADMINISTRADOR',
      descripcion: 'Rol de gestión completa del sistema',
    },
  });

  console.log('✅ Roles creados:', { ciudadano, administrador });
}

main()
  .catch((e) => {
    console.error('❌ Error en seed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
