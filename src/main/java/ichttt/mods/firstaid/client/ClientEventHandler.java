/*
 * FirstAid
 * Copyright (C) 2017-2022
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.tutorial.GuiTutorial;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.AABBAlignedBoundingBox;
import ichttt.mods.firstaid.common.CapProvider;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.apiimpl.FirstAidRegistryImpl;
import ichttt.mods.firstaid.common.apiimpl.RegistryManager;
import ichttt.mods.firstaid.common.items.FirstAidItems;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.PlayerSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ClientEventHandler {
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");
    private static int id;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.connection == null || mc.isPaused()) return;
        if (EventCalendar.isGuiFun()) {
            GuiHealthScreen.BED_ITEMSTACK.setDamageValue(id);
            if (mc.level != null && mc.level.getGameTime() % 3 == 0) id++;
            if (id > 15) id = 0;
            GuiHealthScreen.tickFun();
            PlayerModelRenderer.tickFun();
        }
        if (!RegistryManager.debuffConfigErrors.isEmpty() && mc.level != null && mc.level.isClientSide) {
            mc.player.displayClientMessage(new TextComponent("[FirstAid] FirstAid has detected invalid debuff config entries."), false);
            for (String s : RegistryManager.debuffConfigErrors)
                mc.player.displayClientMessage(new TextComponent("[FirstAid] " + s), false);
            RegistryManager.debuffConfigErrors.clear();
        }
        if (HUDHandler.INSTANCE.ticker >= 0)
            HUDHandler.INSTANCE.ticker--;
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent event) {
        if (ClientHooks.SHOW_WOUNDS.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
            if (damageModel == null) return;
            if (!damageModel.hasTutorial) {
                damageModel.hasTutorial = true;
                CapProvider.tutorialDone.add(mc.player.getName().getString());
                Minecraft.getInstance().setScreen(new GuiTutorial());
            }
            else {
                mc.setScreen(new GuiHealthScreen(damageModel));
            }
        }
    }

    @SubscribeEvent
    public static void preRender(RenderGameOverlayEvent.PreLayer event) {
        RenderGameOverlayEvent.ElementType type = event.getType();
        if (type == RenderGameOverlayEvent.ElementType.LAYER && (event.getOverlay() == ForgeIngameGui.PLAYER_HEALTH_ELEMENT)) {
            FirstAidConfig.Client.VanillaHealthbarMode vanillaHealthBarMode = FirstAidConfig.CLIENT.vanillaHealthBarMode.get();
            if (vanillaHealthBarMode != FirstAidConfig.Client.VanillaHealthbarMode.NORMAL) {
                event.setCanceled(true);
                ForgeIngameGui gui = (ForgeIngameGui) Minecraft.getInstance().gui;
                if (gui.shouldDrawSurvivalElements() && vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.HIGHLIGHT_CRITICAL_PATH && FirstAidConfig.SERVER.vanillaHealthCalculation.get() == FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL) {
                    FirstaidIngameGui.renderHealth((ForgeIngameGui) Minecraft.getInstance().gui, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), event.getMatrixStack());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingRender(RenderLivingEvent.Post<Player, PlayerModel<Player>> event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            if (renderDispatcher.shouldRenderHitBoxes()) {
                PoseStack poseStack = event.getPoseStack();
                poseStack.pushPose();
                //See PlayerRenderer.getRenderOffset
                if (entity.isCrouching()) {
                    poseStack.translate(0D, 0.125D, 0D);
                }
                AABB aabb = entity.getBoundingBox();


                Collection<AABBAlignedBoundingBox> allBoxes = PlayerSizeHelper.getBoxes(entity).values();
                float r = 0.25F;
                float g = 1.0F;
                float b = 1.0F;

                for (AABBAlignedBoundingBox box : allBoxes) {
                    AABB bbox = box.createAABB(aabb);
                    LevelRenderer.renderLineBox(poseStack, event.getMultiBufferSource().getBuffer(RenderType.lines()), bbox.inflate(0.02D).move(-entity.getX(), -entity.getY(), -entity.getZ()), r, g, b, 1.0F);
                    r += 0.25F;
                    g += 0.5F;
                    b += 0.1F;

                    r %= 1.0F;
                    g %= 1.0F;
                    b %= 1.0F;
                }
                poseStack.popPose();
            }
        }
    }

    private static Component makeArmorMsg(double value) {
        return new TranslatableComponent("firstaid.specificarmor", FORMAT.format(value)).withStyle(ChatFormatting.BLUE); //applyTextStyle
    }

    private static Component makeToughnessMsg(double value) {
        return new TranslatableComponent("firstaid.specifictoughness", FORMAT.format(value)).withStyle(ChatFormatting.BLUE); //applyTextStyle
    }

    private static <T> void replaceOrAppend(List<T> list, T search, T replace) {
        int index = list.indexOf(search);
        if (FirstAidConfig.CLIENT.armorTooltipMode.get() == FirstAidConfig.Client.TooltipMode.REPLACE && index >= 0) {
            list.set(index, replace);
        } else {
            list.add(replace);
        }
    }


    @SubscribeEvent
    public static void tooltipItems(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (item == FirstAidItems.MORPHINE) {
            event.getToolTip().add(new TranslatableComponent("firstaid.tooltip.morphine", "3:30-4:30"));
            return;
        }
        if (FirstAidConfig.CLIENT.armorTooltipMode.get() != FirstAidConfig.Client.TooltipMode.NONE) {
            if (item instanceof ArmorItem) {
                ArmorItem armor = (ArmorItem) item;
                List<Component> tooltip = event.getToolTip();

                double normalArmor = ArmorUtils.getArmor(stack, armor.getSlot());
                double totalArmor = ArmorUtils.applyArmorModifier(armor.getSlot(), normalArmor);
                if (totalArmor > 0D) {
                    Component original = new TranslatableComponent("attribute.modifier.plus.0", FORMAT.format(normalArmor), new TranslatableComponent("attribute.name.generic.armor")).withStyle(ChatFormatting.BLUE);
                    replaceOrAppend(tooltip, original, makeArmorMsg(totalArmor));
                }

                double normalToughness = ArmorUtils.getArmorToughness(stack, armor.getSlot());
                double totalToughness = ArmorUtils.applyToughnessModifier(armor.getSlot(), normalToughness);
                if (totalToughness > 0D) {
                    Component original = new TranslatableComponent("attribute.modifier.plus.0", FORMAT.format(normalToughness), new TranslatableComponent("attribute.name.generic.armor_toughness")).withStyle(ChatFormatting.BLUE);
                    replaceOrAppend(tooltip, original, makeToughnessMsg(totalToughness));
                }
            }
        }
        if (item instanceof PotionItem) {
            List<MobEffectInstance> list = PotionUtils.getMobEffects(stack);
            if (!list.isEmpty()) {
                for (MobEffectInstance potionEffect : list) {
                    if (potionEffect.getEffect() == EventHandler.DAMAGE_RESISTANCE) {
                        MobEffect potion = potionEffect.getEffect();
                        Map<Attribute, AttributeModifier> map = potion.getAttributeModifiers();

                        if (!map.isEmpty())
                        {
                            for (Map.Entry<Attribute, AttributeModifier> entry : map.entrySet()) {
                                AttributeModifier falseModifier = entry.getValue();
                                AttributeModifier realModifier = new AttributeModifier(falseModifier.getName(), potion.getAttributeModifierValue(potionEffect.getAmplifier(), falseModifier), falseModifier.getOperation());

                                double d1;

                                if (realModifier.getOperation() != AttributeModifier.Operation.MULTIPLY_BASE && realModifier.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL) {
                                    d1 = realModifier.getAmount();
                                } else {
                                    d1 = realModifier.getAmount() * 100.0D;
                                }

                                Component raw = (new TranslatableComponent("attribute.modifier.plus." + realModifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1), new TranslatableComponent(entry.getKey().getDescriptionId()))).withStyle(ChatFormatting.BLUE);

                                List<Component> toolTip = event.getToolTip();
                                int index = toolTip.indexOf(raw);
                                if (index != -1) {
                                    Component replacement = (new TranslatableComponent("attribute.modifier.plus." + realModifier.getOperation().toValue(), ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(d1 * ((float) FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get() / 20F)), new TranslatableComponent(entry.getKey().getDescriptionId()))).withStyle(ChatFormatting.BLUE);
                                    toolTip.set(index, replacement);
                                }
                            }
                        }
                    }
                }

            }
        }

        AbstractPartHealer healer = FirstAidRegistryImpl.INSTANCE.getPartHealer(stack);
        if (healer != null && event.getPlayer() != null) {
            event.getToolTip().add(new TranslatableComponent("firstaid.tooltip.healer", healer.maxHeal.getAsInt() / 2, StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt())));
        }
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        FirstAid.isSynced = false;
        HUDHandler.INSTANCE.ticker = -1;
    }

    @SubscribeEvent
    public static void onConfigChange(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(FirstAid.MODID) && ClientHooks.firstAidOverlay != null) {
            OverlayRegistry.enableOverlay(ClientHooks.firstAidOverlay, FirstAidConfig.CLIENT.overlayMode.get() != FirstAidConfig.Client.OverlayMode.OFF);
        }
    }
}
