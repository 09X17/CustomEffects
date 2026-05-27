# CustomEffects

Plugin de efectos visuales para Minecraft con sistema de categorías, vouchers y PlaceholderAPI.

## Características

- Menú de efectos con categorías configurables (rainbows, basic_colors, mechanics)
- Sistema de vouchers para desbloquear efectos
- Editor de menú in-game
- Integración con PlaceholderAPI
- Persistencia de datos con SQLite
- Configuración con auto-actualización al agregar nuevas opciones

## Requisitos

- Paper 1.21.1 o superior
- Java 21
- PlaceholderAPI

## Instalación

1. Descarga el archivo `CustomEffects-1.10.0.jar`
2. Colócalo en la carpeta `plugins/` de tu servidor
3. Reinicia el servidor
4. Edita `plugins/CustomEffects/config.yml` según tus necesidades
5. Recarga con `/effectos reload`

## Comandos

| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/effectos` | Abre el menú de efectos | `effectos.menu` |
| `/effectos reload` | Recarga la configuración | `effectos.reload` |
| `/effectos givevoucher <jugador> <efecto>` | Entrega un voucher | `effectos.admin` |

Aliases: `/efectos`, `/effects`

## Permisos

| Permiso | Descripción | Por defecto |
|---------|-------------|-------------|
| `effectos.menu` | Abrir el menú de efectos | `true` |
| `effectos.reload` | Recargar configuración | `op` |
| `effectos.admin` | Generar y dar vouchers | `op` |
| `effectos.chat.bypass` | Bypass de formato de chat | `op` |

## Dependencias

| Dependencia | Tipo |
|-------------|------|
| [Paper API](https://papermc.io/) 1.21.1 | Requerida |
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) 2.11.6 | Requerida |

## Configuración

Los archivos de configuración se encuentran en `plugins/CustomEffects/`:

- `config.yml` - Configuración principal (menú, mensajes, vouchers)
- `categories/*.yml` - Archivos de categorías de efectos

### PlaceholderAPI

| Placeholder | Descripción |
|-------------|-------------|
| `%effectos_prefix%` | Prefijo del plugin |
| `%effectos_effect%` | Efecto activo del jugador |
| `%effectos_hex%` | Color hex del efecto activo |

## Construcción

```bash
mvn clean package
```

El JAR se genera en `target/CustomEffects-1.10.0.jar`.

## Autor

**09X18 - AmplanNetwork**
