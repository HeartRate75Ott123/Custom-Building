package com.plumejade.custombuilding.tab;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The written configuration guide that ships inside the "Custom Building" creative tab.
 *
 * <p>1.20.1 predates written book components, so the book is a plain {@code written_book} stack with the
 * classic {@code title} / {@code author} / {@code pages} NBT.  Pages are JSON encoded components.</p>
 */
public final class GuideBook {
    public static final String TITLE = "Custom Building 配置说明";
    public static final String AUTHOR = "Plume Jade";

    private GuideBook() {}

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", TITLE);
        tag.putString("author", AUTHOR);
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);

        ListTag pages = new ListTag();
        for (String page : pages()) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        }
        tag.put("pages", pages);
        return stack;
    }

    private static List<String> pages() {
        return List.of(
                """
                §6§lCustom Building
                §r§7自定义建筑

                §0本模组让你只用数据包就能造出属于自己的建筑蓝图物品。

                §0把结构文件、预览图、物品贴图、名称和说明写进一个 json，重载数据包之后，它就会出现在「自定义建筑」创造选项卡里。""",

                """
                §6§l1. 文件放哪里

                §0数据包路径：
                §1data/<命名空间>/custom_building/blueprint/<名称>.json
                §0例：
                §1data/custom_building/custom_building/blueprint/initial_house.json

                §0蓝图 id 就是「命名空间:文件名」，上例是
                §1custom_building:initial_house

                §7同目录下的 blueprint_template.txt 是非 json 后缀的示例模板，不会被解析。""",

                """
                §6§l2. 字段一览

                §1structure §0（必填）
                结构名。写 initial_house 时按本文件的命名空间补全，也可以写完整 id。

                §1preview §0（可选）
                悬停时显示的预览图，会被拉伸成 §14:3§0。

                §1texture §0（可选）
                物品在背包里的贴图。不写、留空或写 default 时使用模组自带的默认蓝图贴图。""",

                """
                §6§l3. 名称与说明

                §1name §0（可选）
                物品名称。

                §1tooltip §0（可选）
                说明文字，可以是一行字符串，也可以是字符串数组。

                §0名称和说明都支持两种写法：
                §1· 翻译键§0：写 mypack.blueprint.initial_house，再在语言文件里填内容。
                §1· 直接写字§0：支持 §a§ 颜色代码 §0，例如
                §1§6初始小屋蓝图""",

                """
                §6§l4. 贴图路径怎么写

                §0下面几种写法意思完全一样：
                §1custom_building:textures/item/initial_house.png
                §1assets/custom_building/textures/item/initial_house.png
                §1textures/item/initial_house.png
                §1item/initial_house.png

                §0规则：可以省略开头的 assets/；没有命名空间时使用蓝图文件自己的命名空间；缺少 textures/ 会自动补上；缺少 .png 会自动补上。""",

                """
                §6§l5. 重载与创造选项卡

                §0编辑完数据包后执行：
                §1/reload

                §0模组会重新读取全部蓝图，并自动刷新「自定义建筑」创造选项卡，无需重启游戏。

                §0选项卡里始终有一件默认示例物品，它没有绑定任何蓝图，因此没有任何功能。

                §7联机时蓝图列表由服务端下发给客户端，原版客户端需装同样的模组。""",

                """
                §6§l6. 完整示例

                §0data/custom_building/custom_building/blueprint/initial_house.json

                §1{
                  "structure": "custom_building:initial_house",
                  "preview": "assets/custom_building/textures/blueprint/initial_house.png",
                  "texture": "assets/custom_building/textures/item/initial_house.png",
                  "name": "§6初始小屋蓝图",
                  "tooltip": ["§a右键地面来使用"]
                }

                §0结构文件放在：
                §1data/custom_building/structure/initial_house.nbt""",

                """
                §6§l7. 使用、朝向与预览

                §0手持蓝图§1右键地面§0会打开建造面板：左边选朝向（北/东/南/西），右边是预览图。

                §0面板会记住上一次预览或建造时用的朝向，下次打开直接沿用；第一次使用则朝向你自己。

                §0点§1预览！§0后会关闭面板，并在世界里显示建筑的§1半透明虚影§0，位置和朝向都是最终结果。

                §0虚影显示期间：
                §1· 左键§0 = 取消预览
                §1· 右键§0 = 直接在该位置建造
                §1· 潜行 + 滚轮§0 = 原地切换朝向（会同步记到面板里）

                §0面板里的§1建造！§0也可以直接建造。

                §0成功时聊天栏提示「§a建造完成！§0」，失败时用黄色文字说明原因。

                §0生存模式每次建造消耗一个蓝图，创造模式不消耗。

                §7—— Plume Jade""");
    }
}
