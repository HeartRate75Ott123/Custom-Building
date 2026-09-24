package com.plumejade.custombuilding;

import com.mojang.logging.LogUtils;
import com.plumejade.custombuilding.blueprint.BlueprintDefinitions;
import com.plumejade.custombuilding.blueprint.BlueprintDefinition;
import com.plumejade.custombuilding.blueprint.BlueprintHelper;
import com.plumejade.custombuilding.component.CBDataComponents;
import com.plumejade.custombuilding.config.ClientPreferences;
import com.plumejade.custombuilding.item.BlueprintItem;
import com.plumejade.custombuilding.network.CBNetwork;
import com.plumejade.custombuilding.tab.GuideBook;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Custom Building - data driven building blueprint items.
 *
 * <p>Everything the player sees is described by a datapack file placed in
 * {@code data/<namespace>/custom_building/blueprint/<name>.json}.  Because items cannot be added to a
 * registry after the game has started, all blueprints share the single {@code custom_building:blueprint}
 * item and carry their identity in the {@link CBDataComponents#BLUEPRINT} data component.  The creative
 * tab is rebuilt whenever the datapack changes, so a freshly configured blueprint shows up right after
 * {@code /reload}.</p>
 */
@Mod(CustomBuilding.MODID)
public class CustomBuilding {
    /** The mod id, must match the value in gradle.properties. */
    public static final String MODID = "custom_building";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** The one and only blueprint item.  Bound blueprints are distinguished by their data component. */
    public static final DeferredItem<BlueprintItem> BLUEPRINT =
            ITEMS.registerItem("blueprint", BlueprintItem::new, new Item.Properties());

    /** The "Custom Building" / "自定义建筑" creative tab. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CUSTOM_BUILDING_TAB =
            CREATIVE_MODE_TABS.register("custom_building", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.custom_building"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(BLUEPRINT.get()))
                    .displayItems((parameters, output) -> {
                        // The default example item.  It has no behaviour at all.
                        output.accept(BlueprintHelper.createExampleStack());

                        // Every blueprint that is currently configured by a datapack.
                        for (BlueprintDefinition definition : BlueprintDefinitions.tabEntries()) {
                            output.accept(BlueprintHelper.createStack(definition));
                        }

                        // The written configuration guide, signed by Plume Jade.
                        output.accept(GuideBook.create());
                    })
                    .build());

    public CustomBuilding(IEventBus modEventBus, ModContainer modContainer) {
        CBDataComponents.DATA_COMPONENTS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(CBNetwork::register);

        // Remembers the building orientation the player chose last time.
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientPreferences.SPEC);
    }
}
