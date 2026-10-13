package com.novaclient.module.impl;

import com.novaclient.gui.RenderUtil;
import com.novaclient.gui.Theme;
import com.novaclient.hud.HudElement;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Shows your armor (helmet to boots) and held item, with remaining durability. */
public class ArmorHudModule extends HudElement {
	private static final int ICON = 16;
	private static final int GAP = 2;
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	// Shown in the HUD editor when there's no player (or nothing equipped) so the element can be placed.
	// Created on first use: item stacks can't be built before Minecraft has finished loading items.
	private static List<ItemStack> sample;

	private final ModeSetting layout = addSetting(new ModeSetting("Layout", "Vertical", "Vertical", "Horizontal"));
	private final BooleanSetting showHeldItem = addSetting(new BooleanSetting("Show Held Item", true));
	private final BooleanSetting showDurability = addSetting(new BooleanSetting("Show Durability", true));

	private final List<ItemStack> items = new ArrayList<>(); // reused each frame

	public ArmorHudModule() {
		super("Armor HUD", "Shows your armor and its durability.", 1f, 0.6f);
	}

	private List<ItemStack> collectItems(boolean editing) {
		items.clear();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				ItemStack stack = player.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
			if (showHeldItem.get() && !player.getMainHandItem().isEmpty()) {
				items.add(player.getMainHandItem());
			}
		}
		if (items.isEmpty() && editing) {
			if (sample == null) {
				sample = List.of(
						new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
						new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
						new ItemStack(Items.DIAMOND_SWORD));
			}
			items.addAll(showHeldItem.get() ? sample : sample.subList(0, ARMOR_SLOTS.length));
		}
		return items;
	}

	private boolean vertical() {
		return layout.is("Vertical");
	}

	/** Width reserved for durability text, sized for the widest possible value. */
	private int textWidth() {
		return showDurability.get() ? Minecraft.getInstance().font.width("0000") : 0;
	}

	// The box is sized for the maximum number of items so it doesn't resize as armor changes.
	private int maxItems() {
		return ARMOR_SLOTS.length + (showHeldItem.get() ? 1 : 0);
	}

	@Override
	public int getWidth() {
		if (vertical()) {
			return ICON + (showDurability.get() ? GAP * 2 + textWidth() : 0);
		}
		return maxItems() * (Math.max(ICON, textWidth()) + GAP) - GAP;
	}

	@Override
	public int getHeight() {
		if (vertical()) {
			return maxItems() * (ICON + GAP) - GAP;
		}
		return ICON + (showDurability.get() ? 10 : 0);
	}

	@Override
	protected void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing) {
		Font font = Minecraft.getInstance().font;
		List<ItemStack> stacks = collectItems(editing);
		int cell = Math.max(ICON, textWidth());
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			int x = vertical() ? 0 : i * (cell + GAP) + (cell - ICON) / 2;
			int y = vertical() ? i * (ICON + GAP) : 0;
			graphics.item(stack, x, y);
			graphics.itemDecorations(font, stack, x, y); // durability bar and stack count, like the hotbar

			if (showDurability.get() && stack.isDamageableItem()) {
				int remaining = stack.getMaxDamage() - stack.getDamageValue();
				String text = String.valueOf(remaining);
				int color = durabilityColor(remaining / (float) stack.getMaxDamage());
				if (vertical()) {
					RenderUtil.text(graphics, font, text, ICON + GAP * 2, y + 4, color, true);
				} else {
					RenderUtil.text(graphics, font, text, x + (ICON - font.width(text)) / 2f, ICON + 1, color, true);
				}
			}
		}
	}

	/** Green when healthy, through yellow, to red when nearly broken. */
	private static int durabilityColor(float fraction) {
		if (fraction > 0.5f) {
			return Theme.lerp(0xFFFFE066, 0xFF66E07A, (fraction - 0.5f) * 2);
		}
		return Theme.lerp(0xFFFF5C5C, 0xFFFFE066, fraction * 2);
	}
}
