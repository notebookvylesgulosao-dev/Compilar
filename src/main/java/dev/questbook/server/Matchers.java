package dev.questbook.server;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class Matchers {
	private Matchers() {}

	private static Identifier tagId(String t) { return Identifier.tryParse(t.substring(1)); }

	public static boolean item(ItemStack s, String target) {
		if (s.isEmpty()) return false;
		if (target.startsWith("#")) {
			Identifier id = tagId(target);
			return id != null && s.isIn(TagKey.of(RegistryKeys.ITEM, id));
		}
		return Registries.ITEM.getId(s.getItem()).toString().equals(target);
	}

	public static boolean entity(EntityType<?> t, String target) {
		if (target.startsWith("#")) {
			Identifier id = tagId(target);
			return id != null && t.isIn(TagKey.of(RegistryKeys.ENTITY_TYPE, id));
		}
		return Registries.ENTITY_TYPE.getId(t).toString().equals(target);
	}

	public static boolean block(BlockState s, String target) {
		if (target.startsWith("#")) {
			Identifier id = tagId(target);
			return id != null && s.isIn(TagKey.of(RegistryKeys.BLOCK, id));
		}
		return Registries.BLOCK.getId(s.getBlock()).toString().equals(target);
	}
}
