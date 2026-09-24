package com.plumejade.custombuilding.blueprint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.plumejade.custombuilding.CustomBuilding;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Turns the human friendly paths written in a blueprint datapack file into real {@link ResourceLocation}s.
 *
 * <p>All of the following spellings are accepted and mean the same resource, assuming the blueprint file
 * itself lives in the {@code custom_building} namespace:</p>
 * <ul>
 *     <li>{@code custom_building:textures/item/initial_house.png} - a full resource location</li>
 *     <li>{@code assets/custom_building/textures/item/initial_house.png} - the path as it appears in the jar</li>
 *     <li>{@code textures/item/initial_house.png} - relative to the namespace of the blueprint file</li>
 *     <li>{@code item/initial_house.png} - {@code textures/} is added automatically</li>
 *     <li>{@code item/initial_house} - {@code .png} is added automatically</li>
 * </ul>
 */
public final class BlueprintPaths {
    /** {@code <namespace>/textures/<path>} written without a colon, e.g. {@code assets/xx/textures/xx.png}. */
    private static final Pattern NAMESPACED_TEXTURE = Pattern.compile("^([a-z0-9_.\\-]+)/textures/(.+)$");

    /** The texture used when a blueprint does not configure one (or configures {@code default}). */
    public static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CustomBuilding.MODID, "textures/item/prefab.png");

    private BlueprintPaths() {}

    /**
     * Resolves a configured texture path.
     *
     * @param raw          the string written in the datapack file, may be {@code null} or {@code default}
     * @param definitionId the id of the blueprint definition the path was written in
     * @return the resolved texture, or {@code null} if the path is empty and {@code useDefault} is false
     */
    @Nullable
    public static ResourceLocation resolveTexture(@Nullable String raw, ResourceLocation definitionId, boolean useDefault) {
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("default")) {
            return useDefault ? DEFAULT_TEXTURE : null;
        }

        String path = normalise(raw);
        String namespace = definitionId.getNamespace();

        int colon = path.indexOf(':');
        if (colon >= 0) {
            namespace = path.substring(0, colon);
            path = path.substring(colon + 1);
        } else {
            Matcher matcher = NAMESPACED_TEXTURE.matcher(path);
            if (matcher.matches()) {
                namespace = matcher.group(1);
                path = "textures/" + matcher.group(2);
            }
        }

        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (path.indexOf('.', path.lastIndexOf('/') + 1) < 0) {
            path = path + ".png";
        }

        ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
        if (location == null) {
            throw new IllegalArgumentException("Not a valid texture path: '" + raw + "'");
        }
        return location;
    }

    /**
     * Resolves the structure id.  A bare name is looked up in the namespace of the blueprint file itself.
     */
    public static ResourceLocation resolveStructure(String raw, ResourceLocation definitionId) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("A blueprint must configure a structure");
        }
        ResourceLocation location = value.indexOf(':') < 0
                ? ResourceLocation.tryBuild(definitionId.getNamespace(), value)
                : ResourceLocation.tryParse(value);
        if (location == null) {
            throw new IllegalArgumentException("Not a valid structure name: '" + raw + "'");
        }
        return location;
    }

    /** Converts the structure id into the file the structure template manager is going to read. */
    public static ResourceLocation structureFile(ResourceLocation structureId) {
        return ResourceLocation.fromNamespaceAndPath(structureId.getNamespace(), "structure/" + structureId.getPath() + ".nbt");
    }

    private static String normalise(String raw) {
        String path = raw.trim().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("assets/")) {
            path = path.substring("assets/".length());
        }
        return path;
    }
}
