package org.sethomegui.Utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.sethomegui.SetHomeGUI;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Gson gson = new Gson();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})|#([A-Fa-f0-9]{6})");

    /**
     * Traduce códigos de color legacy (&) y Hexadecimal (&#ffffff)
     */
    public static String color(String message) {
        if (message == null || message.isEmpty()) return "";

        // 1. SOPORTE DE GRADIENTES (MINIMESSAGE)
        // Si detectamos los caracteres '<' y '>', procesamos los gradientes primero.
        if (message.contains("<") && message.contains(">")) {
            Component component = MiniMessage.miniMessage().deserialize(message);
            // Lo serializamos a formato legacy usando el ampersand (&)
            message = LegacyComponentSerializer.legacyAmpersand().serialize(component);
        }

        // 2. TU LÓGICA DE DETECCIÓN HEXADECIMAL TRADICIONAL (&#rrggbb o #rrggbb)
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            // Buscamos cuál de los dos grupos capturó el color (con & o sin &)
            String hex = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

            // Convertimos al formato interno de ChatColor de Bungee (necesita el # delante)
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);

        // 3. TRADUCCIÓN FINAL DE CÓDIGOS LEGACY (&a, &l, etc.)
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Formatea el nombre de un sonido para que sea compatible con el sistema de Minecraft
     */
    public static String formatSoundName(String input) {
        if (input == null || input.isEmpty()) return "minecraft:entity.player.levelup";
        String sound = input.toLowerCase().replace('_', '.');
        if (!sound.contains(":")) {
            sound = "minecraft:" + sound;
        }
        return sound;
    }

    /**
     * Procesa placeholders internos, de PAPI y aplica colores
     */
    public static String setPlaceholders(Player player, String text, SetHomeGUI plugin) {
        if (text == null) return "";

        text = text.replace("%player%", player.getName());

        // 1. REEMPLAZO DE PLACEHOLDERS INTERNAS (NATIVAS)
        if (player != null) {
            if (text.contains("%sethome_current%")) {
                int current = plugin.getHomeManager().getHomeCount(player.getUniqueId());
                text = text.replace("%sethome_current%", String.valueOf(current));
            }
            if (text.contains("%sethome_max%")) {
                int max = plugin.getHomeManager().getPlayerMaxHomes(player);
                String maxStr = (max == -1) ? "∞" : String.valueOf(max);
                text = text.replace("%sethome_max%", maxStr);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }

        return color(text);
    }

    /**
     * Envía el mensaje de uso correcto configurado al jugador cuando comete un error en los argumentos.
     *
     * @param player El jugador que recibirá el mensaje.
     * @param correctUsage El comando correcto (ej: "/delhome <home name>").
     * @param plugin La instancia principal del plugin para leer la configuración.
     */
    public static void sendUsage(Player player, String correctUsage, SetHomeGUI plugin) {
        if (player == null) return;

        // Leemos la plantilla desde el config.yml, si no existe usamos un valor por defecto seguro
        String baseMessage = plugin.getConfig().getString("messages.invalid-usage", "&#ef6603[SetHomeGUI] &#f9a805Usage: &f%usage%");

        // Reemplazamos la variable %usage% por el comando correcto pasado por parámetro
        String formattedMessage = baseMessage.replace("%usage%", correctUsage);

        // Enviamos el mensaje procesando los colores hexadecimales y tradicionales
        player.sendMessage(color(formattedMessage));
    }

    /**
     * Crea una cabeza de jugador (Player Head) a partir de un valor Base64 de textura
     */
    public static ItemStack getHeadFromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta == null) return head;

        if (supportsPlayerProfile()) {
            // Nueva API (1.18.1+) para evitar el uso de Reflection (NMS)
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                String decoded = new String(Base64.getDecoder().decode(base64));
                JsonObject obj = gson.fromJson(decoded, JsonObject.class);
                JsonElement urlElement = obj.getAsJsonObject("textures")
                        .getAsJsonObject("SKIN")
                        .get("url");

                if (urlElement != null) {
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new URL(urlElement.getAsString()));
                    profile.setTextures(textures);
                    meta.setOwnerProfile(profile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Compatibilidad con versiones antiguas usando Reflection (AuthLib)
            GameProfile profile = new GameProfile(UUID.randomUUID(), null);
            profile.getProperties().put("textures", new Property("textures", base64));
            try {
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        head.setItemMeta(meta);
        return head;
    }

    /**
     * Obtiene la cabeza de un jugador de forma segura.
     * Si el jugador es de data importada y el servidor no lo conoce, devuelve una cabeza
     * base por defecto para evitar saturar las conexiones de Mojang (Error 429).
     */
    public static ItemStack getPlayerHead(java.util.UUID uuid) {
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);

        // Si es un usuario desconocido importado, le metemos una skin fija por Base64 sin usar internet
        if (op.getName() == null || !op.hasPlayedBefore()) {
            // Textura de una cabeza de signo de interrogación / usuario gris misterioso
            String misteryUserTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWRiMGU1YzZmOTllNjZiNzRmYTg0Mjc3ODk0YjdlNGI4ZjA5YjI0NTJmYjdlNDg2ZDZlZTQyYjA0NWMyNjEzNyJ9fX0=";
            return getHeadFromBase64(misteryUserTexture);
        }

        // Si es un jugador real del servidor, cargamos su skin normal
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(op);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Resuelve qué acción representa el ítem pulsado SIN depender de posiciones fijas.
     *
     * Primero lee la llave inyectada en el PersistentDataContainer del propio ítem (que siempre
     * viaja con él aunque el administrador lo mueva de slot en gui.yml) y, si no existe, busca en
     * la configuración qué ítem ocupa realmente ese slot (soportando tanto 'slot' como 'slots').
     *
     * @return la acción normalizada (minúsculas y guiones bajos) o null si el ítem no tiene lógica.
     */
    public static String resolveMenuAction(SetHomeGUI plugin, ItemStack clickedItem, Section menuSection, int clickedSlot) {
        String raw = null;

        if (clickedItem != null && clickedItem.hasItemMeta()) {
            raw = clickedItem.getItemMeta().getPersistentDataContainer()
                    .get(plugin.getActionKey(), PersistentDataType.STRING);
        }

        if (raw == null && menuSection != null) {
            raw = findActionBySlot(menuSection.getSection("items"), clickedSlot);
        }

        return normalizeAction(raw);
    }

    /**
     * Recorre la sección 'items' del menú y devuelve la acción del ítem que ocupa el slot pulsado.
     */
    private static String findActionBySlot(Section itemsSection, int clickedSlot) {
        if (itemsSection == null) return null;

        for (Object keyObj : itemsSection.getKeys()) {
            String key = String.valueOf(keyObj);
            Section itemData = itemsSection.getSection(key);
            if (itemData == null) continue;

            if (itemData.contains("slot") && itemData.getInt("slot") == clickedSlot) {
                return itemData.getString("action", key);
            }

            if (itemData.contains("slots")) {
                List<Integer> slots = itemData.getIntList("slots");
                if (slots != null && slots.contains(clickedSlot)) {
                    return itemData.getString("action", key);
                }
            }
        }
        return null;
    }

    /**
     * Estandariza una acción para que 'my-homes', 'My Homes' y 'my_homes' sean equivalentes.
     */
    public static String normalizeAction(String raw) {
        if (raw == null) return null;
        String normalized = raw.toLowerCase(Locale.ROOT).trim().replace("-", "_").replace(" ", "_");
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Verifica si el servidor soporta la nueva API de perfiles de jugador (evita errores en versiones viejas)
     */
    private static boolean supportsPlayerProfile() {
        try {
            Class.forName("org.bukkit.profile.PlayerProfile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}