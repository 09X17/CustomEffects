# Cambios - 27/05/2026

## Eliminación de warnings de deprecación

### ColorUtils.java
- **Eliminado**: Import de `net.md_5.bungee.api.ChatColor`
- **Eliminado**: Patrón `HEX_PATTERN` (ya no necesario)
- **Reemplazado**: `ChatColor.of()` y `ChatColor.translateAlternateColorCodes()` por Adventure API
- **Nuevo método**: `translate()` usa `LegacyComponentSerializer.legacyAmpersand().deserialize()` para convertir colores `&` y hexadecimales

### InventoryClickListener.java
- **Eliminado**: Import de `org.bukkit.ChatColor`
- **Agregado**: Imports de `LegacyComponentSerializer` y `PlainTextComponentSerializer`
- **Reemplazado**: `ChatColor.stripColor()` por método auxiliar `stripColors()` usando Adventure API
- **Reemplazado**: `event.getView().getTitle()` (deprecated) por `LegacyComponentSerializer.legacySection().serialize(event.getView().title())`

## Resultado
- Build limpio sin warnings de deprecación
- Código actualizado a Adventure API (Paper 1.21.1)
- Compatibilidad mantenida con colores legacy y hexadecimales
