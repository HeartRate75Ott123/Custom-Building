package com.plumejade.custombuilding.client.gui;

import java.util.List;

import com.plumejade.custombuilding.CustomBuilding;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintRotations;
import com.plumejade.custombuilding.client.BlueprintPreviewState;
import com.plumejade.custombuilding.config.ClientPreferences;
import com.plumejade.custombuilding.network.BlueprintBuildPacket;
import com.plumejade.custombuilding.network.CBNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;

/**
 * The building panel shown when a configured blueprint is right-clicked.
 *
 * <p>The layout, the panel textures and the button textures are copied from MC-Prefab 1.21.1's
 * {@code GuiBasicStructure}: a control panel on the left, the preview image on the right, and the
 * Preview / Cancel / Build buttons along the bottom.  The "Building Options" row is replaced by the
 * "朝向" row that turns the building around.</p>
 */
public class BlueprintScreen extends Screen {
    private static final ResourceLocation LEFT_PANEL =
            new ResourceLocation(CustomBuilding.MODID, "textures/gui/custom_left_panel.png");
    private static final ResourceLocation RIGHT_PANEL =
            new ResourceLocation(CustomBuilding.MODID, "textures/gui/custom_right_panel.png");

    // Panel geometry, straight from GuiBase/GuiBasicStructure.
    private static final int INITIAL_X = 215;
    private static final int INITIAL_Y = 117;
    private static final int IMAGE_PANEL_X = 136;
    private static final int IMAGE_PANEL_WIDTH = 285;
    private static final int PANEL_HEIGHT = 190;

    /**
     * The panel art of the copied Prefab textures fills exactly x 0..89 / y 0..233 of their 256x256 canvas,
     * with a 3 pixel border on every side (black outline, two highlight pixels, then the fill).  Asking for
     * 89x233 with 2/2/4/4 borders, as MC-Prefab does, drops the outermost outline column and row and leaves
     * a see-through 1 pixel strip along the right and bottom edge of the panels.
     */
    private static final int PANEL_TEXTURE_WIDTH = 90;
    private static final int PANEL_TEXTURE_HEIGHT = 234;
    private static final int PANEL_BORDER = 3;

    /** Row of the bottom button strip.  Raised from Prefab's 177 so it does not hug the panel edge. */
    private static final int BUTTON_Y = 170;

    /** The preview is always drawn as a 4:3 box. */
    private static final int PREVIEW_WIDTH = 240;
    private static final int PREVIEW_HEIGHT = 180;

    private static final int TEXT_COLOUR = 0x404040;

    private final BlueprintDefinition definition;
    private final BlockPos pos;
    private final Direction face;
    private final InteractionHand hand;
    private Direction facing;

    public BlueprintScreen(BlueprintDefinition definition, BlockPos pos, Direction face, InteractionHand hand) {
        super(BlueprintDefinition.textComponent(definition.name()));
        this.definition = definition;
        this.pos = pos;
        this.face = face;
        this.hand = hand;
        this.facing = defaultFacing();
    }

    /** Opens the panel on the orientation that was used last time, or facing the player on first use. */
    private static Direction defaultFacing() {
        Direction remembered = ClientPreferences.lastFacing();
        if (remembered != null && remembered.getAxis().isHorizontal()) {
            return remembered;
        }
        if (Minecraft.getInstance().player != null) {
            Direction look = Minecraft.getInstance().player.getDirection().getOpposite();
            if (look.getAxis().isHorizontal()) {
                return look;
            }
        }
        return Direction.SOUTH;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - INITIAL_X;
        int y = this.height / 2 - INITIAL_Y;

        this.addRenderableWidget(new ExtendedButton(x + 8, y + 45, 100, 20,
                Component.translatable(BlueprintRotations.translationKey(this.facing)), button -> {
                    this.facing = BlueprintRotations.next(this.facing);
                    button.setMessage(Component.translatable(BlueprintRotations.translationKey(this.facing)));
                }));

        this.addRenderableWidget(new CustomButton(x + 24, y + BUTTON_Y, 90, 20,
                Component.translatable("gui.custom_building.button.preview"), button -> this.showPreview()));

        this.addRenderableWidget(Button
                .builder(Component.translatable("gui.custom_building.button.cancel"), button -> this.onClose())
                .bounds(x + 154, y + BUTTON_Y, 90, 20)
                .build());

        this.addRenderableWidget(new CustomButton(x + 310, y + BUTTON_Y, 90, 20,
                Component.translatable("gui.custom_building.button.build"), button -> this.build()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.width / 2 - INITIAL_X;
        int y = this.height / 2 - INITIAL_Y;

        this.renderBackground(guiGraphics);
        // The nine-slice helper draws through its own buffer, so make sure the gui batch is flushed first.
        guiGraphics.flush();

        GuiUtils.drawContinuousTexturedBox(LEFT_PANEL, x + 2, y + 10, 0, 0, 185, PANEL_HEIGHT,
                PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, 0);
        GuiUtils.drawContinuousTexturedBox(RIGHT_PANEL, x + IMAGE_PANEL_X, y + 10, 0, 0, IMAGE_PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, 0);
        guiGraphics.flush();

        if (this.definition.preview() != null) {
            int imageX = x + IMAGE_PANEL_X + (IMAGE_PANEL_WIDTH / 2 - PREVIEW_WIDTH / 2);
            GuiUtils.bindAndDrawScaledTexture(this.definition.preview(), guiGraphics, imageX, y + 15,
                    PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        }

        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        drawWrapped(guiGraphics, BlueprintDefinition.textComponent(this.definition.name()), x + 8, y + 17, 160);
        guiGraphics.drawString(this.font, Component.translatable("gui.custom_building.facing"),
                x + 8, y + 33, TEXT_COLOUR, false);

        Vec3i footprint = BlueprintRotations.rotatedSize(this.definition.size(), this.facing);
        guiGraphics.drawString(this.font, Component.translatable("gui.custom_building.size",
                        this.definition.size().getX(), this.definition.size().getY(), this.definition.size().getZ()),
                x + 8, y + 78, TEXT_COLOUR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.custom_building.footprint",
                        footprint.getX(), footprint.getZ()),
                x + 8, y + 92, TEXT_COLOUR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.custom_building.hint"),
                x + 8, y + 112, TEXT_COLOUR, false);
    }

    /** Word wraps like {@code GuiGraphics#drawWordWrap} does on newer versions. */
    private void drawWrapped(GuiGraphics guiGraphics, Component text, int x, int y, int width) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(this.font, lines.get(i), x, y + i * 9, TEXT_COLOUR, false);
        }
    }

    private void showPreview() {
        BlueprintPreviewState.request(this.definition.id(), this.pos, this.face, this.facing, this.hand);
        this.onClose();
    }

    private void build() {
        ClientPreferences.setLastFacing(this.facing);
        CBNetwork.sendToServer(new BlueprintBuildPacket(this.definition.id(), this.pos, this.face, this.facing, this.hand));
        this.onClose();
    }
}
