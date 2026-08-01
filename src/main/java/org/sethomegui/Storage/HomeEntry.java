package org.sethomegui.Storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Representacion neutral de un hogar, independiente del backend que lo almacene.
 */
public class HomeEntry {

    private String name;
    private String world = "world";
    private double x;
    private double y;
    private double z;
    private double yaw;
    private double pitch;
    private String icon;
    private String access = "nobody";
    private List<String> allowedFriends = new ArrayList<>();

    public HomeEntry() {
    }

    public HomeEntry(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public double getYaw() { return yaw; }
    public void setYaw(double yaw) { this.yaw = yaw; }

    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch = pitch; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getAccess() { return access; }
    public void setAccess(String access) { this.access = access == null ? "nobody" : access; }

    public List<String> getAllowedFriends() { return allowedFriends; }
    public void setAllowedFriends(List<String> allowedFriends) {
        this.allowedFriends = allowedFriends == null ? new ArrayList<>() : allowedFriends;
    }
}
