package com.plumejade.custombuilding.config;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client preferences, currently just the facing the player picked last time.
 *
 * <p>Kept in {@code config/custom_building-client.toml} so the choice also survives a restart, which is what
 * "remember the last orientation" should mean.  It is a client config, so it is only loaded on the client;
 * every accessor here is safe to call from either side.</p>
 */
public final class ClientPreferences {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> LAST_FACING = BUILDER
            .comment("The facing direction last chosen in the blueprint panel (north/south/east/west).",
                    "Used as the default the next time the panel is opened.")
            .define("lastFacing", "south", ClientPreferences::isHorizontalName);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientPreferences() {}

    /** The facing the player used last time, or {@code null} when nothing has been stored yet. */
    @Nullable
    public static Direction lastFacing() {
        if (!SPEC.isLoaded()) {
            return null;
        }
        try {
            return Direction.byName(LAST_FACING.get());
        } catch (Exception exception) {
            return null;
        }
    }

    /** Remembers the facing for the next time the panel is opened. */
    public static void setLastFacing(Direction direction) {
        if (direction == null || !direction.getAxis().isHorizontal() || !SPEC.isLoaded()) {
            return;
        }
        try {
            LAST_FACING.set(direction.getName());
            LAST_FACING.save();
        } catch (Exception ignored) {
            // A read-only config directory is not worth crashing over.
        }
    }

    private static boolean isHorizontalName(Object value) {
        if (!(value instanceof String name)) {
            return false;
        }
        Direction direction = Direction.byName(name);
        return direction != null && direction.getAxis().isHorizontal();
    }
}
