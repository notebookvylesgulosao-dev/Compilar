package dev.questbook.server;

import dev.questbook.data.*;

import java.util.List;

/** Livro de exemplo criado quando o mundo ainda não possui missões. */
final class DefaultBook {
	private DefaultBook() {}

	static QuestBook create() {
		QuestBook b = new QuestBook();
		Chapter c = new Chapter();
		c.id = "primeiros_passos";
		c.title = "Primeiros Passos";
		c.icon = "minecraft:oak_sapling";
		c.description = "Um capítulo de exemplo. Administradores podem editar tudo pelo botão Editor.";

		Quest wood = q("wood", "Colete Madeira", "Corte algumas árvores para começar sua jornada.", "minecraft:oak_log", 0, 0);
		wood.objectives.add(o(ObjectiveType.COLLECT, "#minecraft:logs", 8));
		wood.rewards.add(r(RewardType.ITEM, "minecraft:bread", 3));

		Quest table = q("table", "Mesa de Trabalho", "Fabrique uma bancada de trabalho.", "minecraft:crafting_table", 2, 0, "wood");
		table.objectives.add(o(ObjectiveType.CRAFT, "minecraft:crafting_table", 1));
		table.rewards.add(r(RewardType.XP_POINTS, "", 20));

		Quest pick = q("pick", "Ferramentas de Pedra", "Fabrique uma picareta de pedra.", "minecraft:stone_pickaxe", 4, -1, "table");
		pick.objectives.add(o(ObjectiveType.CRAFT, "minecraft:stone_pickaxe", 1));
		pick.rewards.add(r(RewardType.ITEM, "minecraft:cooked_beef", 4));

		Quest fight = q("fight", "Primeiro Combate", "Derrote zumbis que aparecem à noite.", "minecraft:iron_sword", 4, 1, "table");
		fight.objectives.add(o(ObjectiveType.KILL, "minecraft:zombie", 3));
		fight.rewards.add(r(RewardType.XP_LEVELS, "", 1));

		Quest rest = q("rest", "Descanse", "Use uma cama.", "minecraft:red_bed", 6, 2, "fight");
		rest.objectives.add(o(ObjectiveType.INTERACT_BLOCK, "#minecraft:beds", 1));
		rest.rewards.add(r(RewardType.ITEM, "minecraft:golden_apple", 1));

		Quest explore = q("explore", "Explorador", "Visite uma planície.", "minecraft:map", 6, -1, "pick");
		explore.objectives.add(o(ObjectiveType.BIOME, "minecraft:plains", 1));
		explore.rewards.add(r(RewardType.ITEM, "minecraft:emerald", 2));

		Quest iron = q("iron", "Entrega de Ferro", "Entregue lingotes de ferro (use o botão Entregar).", "minecraft:iron_ingot", 8, 0, "explore", "fight");
		iron.objectives.add(o(ObjectiveType.SUBMIT, "minecraft:iron_ingot", 8));
		iron.rewards.add(r(RewardType.ITEM, "minecraft:diamond", 1));
		iron.rewards.add(r(RewardType.XP_LEVELS, "", 3));

		c.quests.addAll(List.of(wood, table, pick, fight, rest, explore, iron));
		b.chapters.add(c);
		b.sanitize();
		return b;
	}

	private static Quest q(String id, String title, String desc, String icon, double x, double y, String... deps) {
		Quest q = new Quest();
		q.id = id;
		q.title = title;
		q.description = desc;
		q.icon = icon;
		q.x = x;
		q.y = y;
		q.dependencies.addAll(List.of(deps));
		return q;
	}

	private static Objective o(ObjectiveType t, String target, int count) {
		Objective o = new Objective();
		o.id = QuestBook.shortId();
		o.type = t;
		o.target = target;
		o.count = count;
		return o;
	}

	private static Reward r(RewardType t, String v, int amount) {
		Reward r = new Reward();
		r.type = t;
		r.value = v;
		r.amount = amount;
		return r;
	}
}
