package dev.waypointjournal;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;

final class XaeroCompat {
    private static final Identifier ORIGIN = Identifier.fromNamespaceAndPath("waypointjournal", "destination");
    private static ThirdPartyWaypoints markers;
    private static Waypoint marker;
    private static String destination;

    private XaeroCompat() {}

    static void tick(Minecraft client) {
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        var world = session == null ? null : session.getWorldManager().getAutoWorld();
        ThirdPartyWaypoints current = world == null ? null : world.getContainer().getThirdPartyWaypointManager().get(ORIGIN);
        if (current != markers) {
            clear();
            markers = current;
        }
        if (markers == null) return;
        var point = Journal.tracked();
        if (point == null || client.player == null || client.level == null
            || !point.dimension.equals(client.level.dimension().identifier().toString())) {
            clear();
            return;
        }
        int x = point.xEnabled ? point.x : client.player.blockPosition().getX();
        int y = point.yEnabled ? point.y : client.player.blockPosition().getY();
        int z = point.zEnabled ? point.z : client.player.blockPosition().getZ();
        if (!point.id.equals(destination)) {
            clear();
            marker = new Waypoint(x, y, z, point.name, "X", WaypointColor.RED);
            marker.setTemporary(true);
            markers.add(point.id, marker);
            destination = point.id;
        } else {
            marker.setX(x);
            marker.setY(y);
            marker.setZ(z);
            marker.setName(point.name);
        }
        marker.setYIncluded(point.yEnabled);
    }

    private static void clear() {
        if (markers != null && marker != null) markers.clear();
        marker = null;
        destination = null;
    }
}
