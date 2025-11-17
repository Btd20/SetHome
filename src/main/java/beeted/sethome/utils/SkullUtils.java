package beeted.sethome.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

public class SkullUtils {

    private static final Gson gson = new Gson();

    public static ItemStack getHeadFromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (supportsPlayerProfile()) {
            // Nueva API (1.18.1+)
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
            // Compatibilidad con versiones anteriores
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

    private static boolean supportsPlayerProfile() {
        try {
            Class.forName("org.bukkit.profile.PlayerProfile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
