package dev.questbook.client;

import dev.questbook.data.Objective;
import dev.questbook.data.ObjectiveType;
import dev.questbook.data.Reward;
import dev.questbook.data.RewardType;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Locale;

/** Textos e ícones legíveis para objetivos e recompensas. */
public final class Describe {
	private Describe() {}

	public static String human(String path) {
		StringBuilder sb = new StringBuilder();
		for (String w : path.replace('/', ' ').replace('_', ' ').split(" ")) {
			if (w.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
		}
		return sb.toString();
	}

	private static String pathOf(String id) {
		int i = id.indexOf(':');
		return i < 0 ? id : id.substring(i + 1);
	}

	public static String target(ObjectiveType.Kind kind, String t) {
		if (t == null || t.isEmpty()) return "?";
		if (t.startsWith("#")) return human(pathOf(t)) + " (tag)";
		Identifier id = Identifier.tryParse(t);
		if (id == null) return t;
		try {
			switch (kind) {
				case ITEM -> { return ClientData.stack(t).getName().getString(); }
				case ENTITY -> { return Registries.ENTITY_TYPE.getOptionalValue(id).map(e -> e.getName().getString()).orElse(human(id.getPath())); }
				case BLOCK -> { return Registries.BLOCK.getOptionalValue(id).map(b -> b.getName().getString()).orElse(human(id.getPath())); }
				case BIOME -> {
					String key = "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.');
					return I18n.hasTranslation(key) ? I18n.translate(key) : human(id.getPath());
				}
				default -> { return human(id.getPath()); }
			}
		} catch (Exception e) {
			return human(id.getPath());
		}
	}

	public static String typeName(ObjectiveType t) {
		return switch (t) {
			case COLLECT -> Lang.t("Coletar", "Collect");
			case CRAFT -> Lang.t("Fabricar", "Craft");
			case SUBMIT -> Lang.t("Entregar", "Submit");
			case KILL -> Lang.t("Combate", "Kill");
			case BIOME -> Lang.t("Explorar bioma", "Explore biome");
			case STRUCTURE -> Lang.t("Explorar estrutura", "Explore structure");
			case DIMENSION -> Lang.t("Entrar em dimensão", "Enter dimension");
			case LOCATION -> Lang.t("Chegar a local", "Reach location");
			case INTERACT_BLOCK -> Lang.t("Interagir com bloco", "Interact with block");
			case INTERACT_ENTITY -> Lang.t("Interagir com entidade", "Interact with entity");
		};
	}

	public static String objective(Objective o) {
		String n = target(o.type.kind, o.target);
		int c = o.required();
		return switch (o.type) {
			case COLLECT -> Lang.t("Coletar ", "Collect ") + c + "× " + n;
			case CRAFT -> Lang.t("Fabricar ", "Craft ") + c + "× " + n;
			case SUBMIT -> Lang.t("Entregar ", "Submit ") + c + "× " + n;
			case KILL -> Lang.t("Derrotar ", "Kill ") + c + "× " + n;
			case BIOME -> Lang.t("Visitar o bioma ", "Visit biome ") + n;
			case STRUCTURE -> Lang.t("Encontrar ", "Find ") + n;
			case DIMENSION -> Lang.t("Entrar em ", "Enter ") + n;
			case LOCATION -> Lang.t("Chegar a ", "Reach ") + o.target.replace(",", ", ") + " (" + Lang.t("raio ", "radius ") + o.count + ")";
			case INTERACT_BLOCK -> Lang.t("Interagir com ", "Interact with ") + n + " (" + c + "×)";
			case INTERACT_ENTITY -> Lang.t("Interagir com ", "Interact with ") + n + " (" + c + "×)";
		};
	}

	public static ItemStack objectiveIcon(Objective o) {
		return switch (o.type.kind) {
			case ITEM -> ClientData.stack(o.target);
			case BLOCK -> {
				Identifier id = Identifier.tryParse(o.target.startsWith("#") ? "minecraft:barrier" : o.target);
				var b = id == null ? java.util.Optional.<net.minecraft.block.Block>empty() : Registries.BLOCK.getOptionalValue(id);
				ItemStack s = b.map(x -> new ItemStack(x.asItem())).orElse(ItemStack.EMPTY);
				yield s.isEmpty() ? new ItemStack(Items.BARRIER) : s;
			}
			case ENTITY -> new ItemStack(o.type == ObjectiveType.KILL ? Items.IRON_SWORD : Items.LEAD);
			case BIOME -> new ItemStack(Items.GRASS_BLOCK);
			case STRUCTURE -> new ItemStack(Items.FILLED_MAP);
			case DIMENSION -> new ItemStack(Items.ENDER_EYE);
			case NONE -> new ItemStack(Items.COMPASS);
		};
	}

	public static String reward(Reward r) {
		return switch (r.type) {
			case ITEM -> r.amount + "× " + target(ObjectiveType.Kind.ITEM, r.value);
			case XP_POINTS -> "+" + r.amount + " XP";
			case XP_LEVELS -> "+" + r.amount + Lang.t(" nível(is)", " level(s)");
			case COMMAND -> Lang.t("Comando: ", "Command: ") + r.value;
		};
	}

	public static ItemStack rewardIcon(Reward r) {
		return switch (r.type) {
			case ITEM -> ClientData.stack(r.value);
			case XP_POINTS -> new ItemStack(Items.EXPERIENCE_BOTTLE);
			case XP_LEVELS -> new ItemStack(Items.ENCHANTED_BOOK);
			case COMMAND -> new ItemStack(Items.COMMAND_BLOCK);
		};
	}

	public static String rewardTypeName(RewardType t) {
		return switch (t) {
			case ITEM -> Lang.t("Item", "Item");
			case XP_POINTS -> Lang.t("Pontos de XP", "XP points");
			case XP_LEVELS -> Lang.t("Níveis de XP", "XP levels");
			case COMMAND -> Lang.t("Comando", "Command");
		};
	}
}
