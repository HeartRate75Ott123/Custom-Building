package com.plumejade.custombuilding.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintPlacer;
import com.plumejade.custombuilding.blueprint.BlueprintRotations;
import com.plumejade.custombuilding.blueprint.BlueprintSchematic;
import com.plumejade.custombuilding.config.ClientPreferences;
import com.plumejade.custombuilding.network.BlueprintBuildPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * The translucent ghost of the structure, drawn while the player is choosing where to build.
 *
 * <p>Rendering follows Litematica's ghost overlay: the block models are baked once per 16x16x16 section into
 * cached {@link VertexBuffer}s, every quad is emitted unlit and at full brightness ({@code putBulkData} with
 * {@link LightTexture#FULL_BRIGHT}), and the whole thing is drawn through {@link RenderType#translucent()}
 * with the shader colour alpha lowered - Litematica calls this "ghost block alpha", default 0.5.</p>
 *
 * <p>While the ghost is up the left mouse button cancels it and the right mouse button builds it right away.</p>
 */
public final class BlueprintPreviewState {
    private static final float GHOST_ALPHA = 0.5F;
    private static final long MODEL_SEED = 42L;

    private record Mesh(BlockPos origin, VertexBuffer buffer) {}

    private static final List<Mesh> MESHES = new ArrayList<>();

    @Nullable
    private static ResourceLocation blueprintId;
    @Nullable
    private static BlockPos clickedPos;
    private static Direction face = Direction.UP;
    private static Direction facing = Direction.SOUTH;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static boolean active;

    private BlueprintPreviewState() {}

    /** Called by the panel's "预览！" button. */
    public static void request(ResourceLocation id, BlockPos pos, Direction clickedFace, Direction direction, InteractionHand usedHand) {
        clear();
        blueprintId = id;
        clickedPos = pos;
        face = clickedFace;
        facing = direction;
        hand = usedHand;
        ClientPreferences.setLastFacing(direction);

        BlueprintSchematic schematic = ClientSchematicCache.get(id);
        if (schematic != null) {
            activate(schematic);
        } else {
            ClientSchematicCache.request(id);
            notifyPlayer("message.custom_building.preview.loading", ChatFormatting.GRAY);
        }
    }

    /** Called when the server answers a schematic request. */
    public static void onSchematicReady(ResourceLocation id) {
        if (active || blueprintId == null || !blueprintId.equals(id)) {
            return;
        }
        BlueprintSchematic schematic = ClientSchematicCache.get(id);
        if (schematic != null) {
            activate(schematic);
        }
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Turns the ghost while it is being shown (sneak + mouse wheel).  The structure stays anchored to the
     * block that was right-clicked, only its orientation changes.
     *
     * @param step {@code 1} for the next direction, {@code -1} for the previous one
     */
    public static void rotate(int step) {
        if (!active || step == 0) {
            return;
        }
        for (int i = 0; i < Math.abs(step); i++) {
            facing = step > 0 ? BlueprintRotations.next(facing) : BlueprintRotations.previous(facing);
        }
        ClientPreferences.setLastFacing(facing);

        if (blueprintId != null) {
            BlueprintSchematic schematic = ClientSchematicCache.get(blueprintId);
            if (schematic != null) {
                buildMeshes(schematic);
                if (MESHES.isEmpty()) {
                    active = false;
                    notifyPlayer("message.custom_building.preview.unavailable", ChatFormatting.YELLOW);
                    return;
                }
            }
        }

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.custom_building.preview.facing",
                            Component.translatable(BlueprintRotations.translationKey(facing))).withStyle(ChatFormatting.AQUA),
                    true);
        }
    }

    /** Left click: drop the ghost without building. */
    public static void cancel() {
        if (!active) {
            return;
        }
        reset();
        notifyPlayer("message.custom_building.preview.cleared", ChatFormatting.YELLOW);
    }

    /** Right click: build exactly what is being shown. */    public static void confirmBuild() {
        if (!active || blueprintId == null || clickedPos == null) {
            return;
        }
        ResourceLocation id = blueprintId;
        BlockPos pos = clickedPos;
        Direction clickedFace = face;
        Direction direction = facing;
        InteractionHand usedHand = hand;

        reset();
        PacketDistributor.sendToServer(new BlueprintBuildPayload(id, pos, clickedFace, direction, usedHand));
    }

    /** Forgets everything, for example when leaving the world. */
    public static void clear() {
        reset();
    }

    private static void reset() {
        releaseMeshes();
        active = false;
        blueprintId = null;
        clickedPos = null;
    }

    private static void activate(BlueprintSchematic schematic) {
        buildMeshes(schematic);
        active = !MESHES.isEmpty();
        if (active) {
            CustomBuilding.LOGGER.info("Blueprint preview for '{}': {} blocks baked into {} section mesh(es)",
                    schematic.blueprintId(), schematic.positions().size(), MESHES.size());
            notifyPlayer("message.custom_building.preview.notice", ChatFormatting.GREEN);
        } else {
            CustomBuilding.LOGGER.warn("Blueprint preview for '{}' produced no geometry ({} blocks, size {})",
                    schematic.blueprintId(), schematic.positions().size(), schematic.size());
            notifyPlayer("message.custom_building.preview.unavailable", ChatFormatting.YELLOW);
        }
    }

    private static void notifyPlayer(String translationKey, ChatFormatting colour) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable(translationKey).withStyle(colour), false);
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Baking
    // ---------------------------------------------------------------------------------------------------

    private static void buildMeshes(BlueprintSchematic schematic) {
        releaseMeshes();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || schematic.isEmpty() || clickedPos == null) {
            CustomBuilding.LOGGER.warn("Skipped the blueprint preview: level={}, blocks={}, clickedPos={}",
                    level != null, schematic.positions().size(), clickedPos);
            return;
        }

        BlockPos origin = BlueprintPlacer.outlineOrigin(clickedPos, face, facing, schematic.size());
        if (origin == null) {
            CustomBuilding.LOGGER.warn("Skipped the blueprint preview: the structure size is unknown");
            return;
        }
        Rotation rotation = BlueprintRotations.rotationFor(facing);

        // World space block map: the key of the fake world used for face culling.
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int i = 0; i < schematic.positions().size(); i++) {
            BlockPos relative = StructureTemplate.transform(schematic.positions().get(i),
                    Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos world = origin.offset(relative);
            blocks.put(world, schematic.states().get(i).rotate(level, world, rotation));
        }

        Map<Long, List<BlockPos>> sections = new HashMap<>();
        for (BlockPos pos : blocks.keySet()) {
            long key = SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            sections.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pos);
        }

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BlockGetter getter = new SchematicBlockGetter(level, blocks);
        RandomSource random = RandomSource.create(MODEL_SEED);

        for (Map.Entry<Long, List<BlockPos>> entry : sections.entrySet()) {
            long key = entry.getKey();
            BlockPos sectionOrigin = new BlockPos(SectionPos.x(key) << 4, SectionPos.y(key) << 4, SectionPos.z(key) << 4);

            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            PoseStack poseStack = new PoseStack();
            for (BlockPos pos : entry.getValue()) {
                BlockState state = blocks.get(pos);
                poseStack.pushPose();
                poseStack.translate(pos.getX() - sectionOrigin.getX(),
                        pos.getY() - sectionOrigin.getY(),
                        pos.getZ() - sectionOrigin.getZ());
                bakeBlock(minecraft, dispatcher, getter, state, pos, poseStack, builder, random);
                poseStack.popPose();
            }

            MeshData mesh = builder.build();
            if (mesh == null) {
                continue;
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            MESHES.add(new Mesh(sectionOrigin, buffer));
        }
    }

    private static void bakeBlock(Minecraft minecraft, BlockRenderDispatcher dispatcher, BlockGetter getter,
                                  BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                  RandomSource random) {
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        BakedModel model = dispatcher.getBlockModel(state);
        PoseStack.Pose pose = poseStack.last();

        for (Direction side : Direction.values()) {
            random.setSeed(MODEL_SEED);
            List<BakedQuad> quads = model.getQuads(state, side, random, ModelData.EMPTY, null);
            if (quads.isEmpty() || !Block.shouldRenderFace(state, getter, pos, side, pos.relative(side))) {
                continue;
            }
            for (BakedQuad quad : quads) {
                emitQuad(minecraft, consumer, pose, quad, state, pos);
            }
        }

        random.setSeed(MODEL_SEED);
        for (BakedQuad quad : model.getQuads(state, null, random, ModelData.EMPTY, null)) {
            emitQuad(minecraft, consumer, pose, quad, state, pos);
        }
    }

    private static void emitQuad(Minecraft minecraft, VertexConsumer consumer, PoseStack.Pose pose,
                                 BakedQuad quad, BlockState state, BlockPos pos) {
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        if (quad.isTinted()) {
            int colour = minecraft.getBlockColors().getColor(state, minecraft.level, pos, quad.getTintIndex());
            red = (colour >> 16 & 0xFF) / 255.0F;
            green = (colour >> 8 & 0xFF) / 255.0F;
            blue = (colour & 0xFF) / 255.0F;
        }
        consumer.putBulkData(pose, quad, red, green, blue, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    private static void releaseMeshes() {
        for (Mesh mesh : MESHES) {
            mesh.buffer().close();
        }
        MESHES.clear();
    }

    // ---------------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------------

    public static void render(RenderLevelStageEvent event) {
        if (!active || MESHES.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // Stages derived from a render type carry no pose stack, only the model view matrix.
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(event.getModelViewMatrix());

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f projection = event.getProjectionMatrix();
        ShaderInstance shader = GameRenderer.getRendertypeTranslucentShader();
        RenderType renderType = RenderType.translucent();

        renderType.setupRenderState();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-0.4F, -0.8F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, GHOST_ALPHA);

        // Defensive: the terrain renderer uses the same shader and leaves ChunkOffset at the last section's
        // offset.  It does reset the value to zero before this stage, but pinning it explicitly costs nothing
        // and guards against the value ever changing.
        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            shader.CHUNK_OFFSET.upload();
        }

        try {
            for (Mesh mesh : MESHES) {
                BlockPos meshOrigin = mesh.origin();
                poseStack.pushPose();
                poseStack.translate(meshOrigin.getX() - camera.x, meshOrigin.getY() - camera.y, meshOrigin.getZ() - camera.z);
                // drawWithShader() only sets the uniforms and calls glDrawElements; the vertex array has to be
                // bound first, exactly like the vanilla chunk renderer does.
                mesh.buffer().bind();
                mesh.buffer().drawWithShader(poseStack.last().pose(), projection, shader);
                VertexBuffer.unbind();
                poseStack.popPose();
            }
        } catch (Exception exception) {
            CustomBuilding.LOGGER.error("Failed to draw the blueprint preview", exception);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        renderType.clearRenderState();
    }
}
