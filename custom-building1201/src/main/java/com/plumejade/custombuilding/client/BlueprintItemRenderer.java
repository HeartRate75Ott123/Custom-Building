package com.plumejade.custombuilding.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.custombuilding.blueprint.BlueprintHelper;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the item texture a blueprint configures.
 *
 * <p>The item model of {@code custom_building:blueprint} is a child of {@code builtin/entity}, which makes
 * vanilla hand item rendering over to this renderer.  A single quad is emitted in the standard "block"
 * space {@code [0,1]}^3 that the item display transforms are written against, textured with whatever
 * {@code texture} the datapack file names - no resource pack or model json is needed.</p>
 */
public class BlueprintItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final BlueprintItemRenderer INSTANCE = new BlueprintItemRenderer();

    private static final Map<ResourceLocation, RenderType> RENDER_TYPES = new HashMap<>();
    private static final float Z = 0.5F;

    private BlueprintItemRenderer() {
        super(null, null);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        RENDER_TYPES.clear();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ResourceLocation texture = BlueprintHelper.textureOf(stack);
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPES.computeIfAbsent(texture, RenderType::entityCutoutNoCull));
        PoseStack.Pose pose = poseStack.last();

        // Full texture, unfitted: the datapack documentation promises a 1:1 item icon.
        vertex(consumer, pose, 0.0F, 0.0F, 0.0F, 1.0F, packedLight, packedOverlay);
        vertex(consumer, pose, 0.0F, 1.0F, 0.0F, 0.0F, packedLight, packedOverlay);
        vertex(consumer, pose, 1.0F, 1.0F, 1.0F, 0.0F, packedLight, packedOverlay);
        vertex(consumer, pose, 1.0F, 0.0F, 1.0F, 1.0F, packedLight, packedOverlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v,
                               int packedLight, int packedOverlay) {
        consumer.vertex(pose.pose(), x, y, Z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
