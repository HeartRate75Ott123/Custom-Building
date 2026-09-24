package com.plumejade.custombuilding.blueprint;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * How a blueprint is turned around.
 *
 * <p>A structure template is authored facing south, which is the same convention vanilla structure blocks
 * use.  The panel lets the player pick one of the four horizontal directions and this class maps it onto the
 * {@link Rotation} the structure is placed with.</p>
 */
public final class BlueprintRotations {
    /** The order the facing button cycles through: 北 → 东 → 南 → 西. */
    public static final List<Direction> CYCLE = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private BlueprintRotations() {}

    public static Direction next(Direction current) {
        int index = CYCLE.indexOf(current);
        return CYCLE.get(index < 0 ? 0 : (index + 1) % CYCLE.size());
    }

    public static Direction previous(Direction current) {
        int index = CYCLE.indexOf(current);
        return CYCLE.get(index < 0 ? 0 : (index - 1 + CYCLE.size()) % CYCLE.size());
    }

    /** Maps the direction the building should face onto the rotation its template is placed with. */
    public static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
    }

    /** The translation key of a horizontal direction, matching MC-Prefab's {@code prefab.gui.<name>} keys. */
    public static String translationKey(Direction direction) {
        return "gui.custom_building." + direction.getName();
    }

    /** The footprint of a structure after it has been rotated. */
    public static Vec3i rotatedSize(Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
            default -> size;
        };
    }

    /** The footprint of a structure facing the given direction. */
    public static Vec3i rotatedSize(Vec3i size, Direction facing) {
        return rotatedSize(size, rotationFor(facing));
    }

    /**
     * Where the structure ends up: the north-west corner of its (rotated) bounding box.
     *
     * @param target the block the player right-clicked, i.e. where the footprint should start
     */
    public static net.minecraft.core.BlockPos originFor(net.minecraft.core.BlockPos target, Vec3i templateSize, Direction facing) {
        return StructureTemplate.getZeroPositionWithTransform(target, Mirror.NONE, rotationFor(facing),
                templateSize.getX(), templateSize.getZ());
    }
}
