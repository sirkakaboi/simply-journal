package dev.waypointjournal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Journal {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = Minecraft.getInstance().gameDirectory.toPath().resolve("config/waypoint-journal");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static String world;
    private static Path file;
    private static Data data = new Data();
    private static boolean writable = true;
    private static boolean dirty;
    private static long changed;

    private Journal() {}

    public static boolean connect(Minecraft client) {
        if (client.level == null || client.player == null) return false;
        String key;
        if (client.getSingleplayerServer() != null) {
            key = "world:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (client.getCurrentServer() != null) {
            key = "server:" + client.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
        } else {
            key = "lan:" + client.getConnection().getConnection().getRemoteAddress();
        }
        selectWorld(key);
        return true;
    }

    static void selectWorld(String key) {
        if (key.equals(world)) return;
        save();
        world = key;
        file = DIRECTORY.resolve(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)) + ".json");
        data = new Data();
        writable = true;
        dirty = false;
        if (Files.exists(file)) {
            try {
                Data loaded = GSON.fromJson(Files.readString(file), Data.class);
                if (loaded == null || loaded.waypoints == null || loaded.waypoints.stream().anyMatch(p ->
                    p != null && (p.id == null || p.name == null || p.description == null || p.dimension == null))) {
                    throw new IOException("Invalid journal data");
                }
                data = loaded;
                for (Waypoint point : data.waypoints) {
                    if (point == null) continue;
                    if (point.originalX == null) point.originalX = point.x;
                    if (point.originalY == null) point.originalY = point.y;
                    if (point.originalZ == null) point.originalZ = point.z;
                }
            } catch (IOException | RuntimeException error) {
                writable = false;
                report("Couldn't read this journal. The saved file has been kept.", error);
            }
        }
    }

    public static void disconnect() {
        save();
        world = null;
        file = null;
        data = new Data();
    }

    public static List<Waypoint> waypoints() { return data.waypoints; }

    public static Waypoint tracked() {
        return data.waypoints.stream().filter(p -> p != null && p.id.equals(data.tracked)).findFirst().orElse(null);
    }

    public static void track(Waypoint point) {
        data.tracked = point == null ? null : point.id;
        changed();
        save();
    }

    public static Waypoint add(boolean death) {
        if (!connect(Minecraft.getInstance())) return null;
        int index = data.waypoints.indexOf(null);
        return addAt(index < 0 ? data.waypoints.size() : index, death);
    }

    public static Waypoint addAt(int index, boolean death) {
        Minecraft client = Minecraft.getInstance();
        if (!connect(client)) return null;
        if (index < data.waypoints.size() && data.waypoints.get(index) != null) return null;
        var position = client.player.blockPosition();
        Waypoint point = new Waypoint();
        point.id = UUID.randomUUID().toString();
        point.name = death ? "Death " + (++data.deaths) : "Waypoint " + (++data.created);
        point.description = death ? "Died on " + LocalDateTime.now().format(TIME) : "";
        point.x = position.getX();
        point.y = position.getY();
        point.z = position.getZ();
        point.originalX = point.x;
        point.originalY = point.y;
        point.originalZ = point.z;
        point.dimension = client.level.dimension().identifier().toString();
        point.death = death;
        while (data.waypoints.size() <= index) data.waypoints.add(null);
        data.waypoints.set(index, point);
        changed();
        save();
        return point;
    }

    public static void remove(Waypoint point) {
        data.waypoints.set(data.waypoints.indexOf(point), null);
        if (point.id.equals(data.tracked)) data.tracked = null;
        changed();
        save();
    }

    public static void changed() { dirty = true; changed = System.nanoTime(); }

    public static void tick() {
        if (dirty && System.nanoTime() - changed > 600_000_000L) save();
    }

    public static void save() {
        if (!dirty || file == null || !writable) return;
        try {
            Files.createDirectories(DIRECTORY);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException error) {
            changed = System.nanoTime();
            report("Couldn't save your waypoint journal.", error);
        }
    }

    private static void report(String message, Exception error) {
        LoggerFactory.getLogger("Simply Journal").error(message, error);
        var client = Minecraft.getInstance();
        if (client.player != null) client.player.sendSystemMessage(Component.literal(message));
    }

    public static String dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> id;
        };
    }

    public static final class Waypoint {
        public String id;
        public String name;
        public String description;
        public int x, y, z;
        public Integer originalX, originalY, originalZ;
        public boolean xEnabled = true;
        public boolean yEnabled = true;
        public boolean zEnabled = true;
        public String dimension;
        public boolean death;

        public int coordinate(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        public int originalCoordinate(int axis) { return axis == 0 ? originalX : axis == 1 ? originalY : originalZ; }
        public boolean enabled(int axis) { return axis == 0 ? xEnabled : axis == 1 ? yEnabled : zEnabled; }

        public void setCoordinate(int axis, int value) {
            switch (axis) { case 0 -> x = value; case 1 -> y = value; default -> z = value; }
        }

        public void setEnabled(int axis, boolean enabled) {
            switch (axis) { case 0 -> xEnabled = enabled; case 1 -> yEnabled = enabled; default -> zEnabled = enabled; }
        }
    }

    private static final class Data {
        List<Waypoint> waypoints = new ArrayList<>();
        String tracked;
        int deaths;
        int created;
    }
}
