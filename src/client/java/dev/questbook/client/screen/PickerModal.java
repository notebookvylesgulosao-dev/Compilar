package dev.questbook.client.screen;

import dev.questbook.client.ClientData;
import dev.questbook.client.Describe;
import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.TextField;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.Chapter;
import dev.questbook.data.Quest;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Consumer;

/** Seletor genérico com busca, filtros por categoria/mod e grade de ícones. */
public class PickerModal extends Modal {
	public record Entry(String id, String label, ItemStack icon, String cat) {}

	private static final String[] CATS = {"all", "blocks", "tools", "combat", "armor", "food", "other", "tags"};

	private static String catLabel(String c) {
		return switch (c) {
			case "all" -> Lang.t("Todos", "All");
			case "blocks" -> Lang.t("Blocos", "Blocks");
			case "tools" -> Lang.t("Ferramentas", "Tools");
			case "combat" -> Lang.t("Combate", "Combat");
			case "armor" -> Lang.t("Armaduras", "Armor");
			case "food" -> Lang.t("Comida", "Food");
			case "tags" -> "Tags";
			default -> Lang.t("Outros", "Other");
		};
	}

	private final String title;
	private final List<Entry> all;
	private final boolean grid, categories;
	private final Consumer<String> onPick;
	private final TextField search = new TextField("", 40);
	private final List<String> namespaces = new ArrayList<>();
	private int cat = 0, ns = 0, scroll, contentH;
	private List<Entry> filtered = List.of();
	private String lastKey = null;

	private PickerModal(QuestBookScreen s, String title, List<Entry> all, boolean grid, boolean categories, Consumer<String> onPick) {
		super(s);
		this.title = title;
		this.all = all;
		this.grid = grid;
		this.categories = categories;
		this.onPick = onPick;
		search.hint = Lang.t("Buscar por nome, id, @mod...", "Search name, id, @mod...");
		search.focused = true;
		TreeSet<String> set = new TreeSet<>();
		for (Entry e : all) set.add(nsOf(e.id));
		namespaces.add("");
		namespaces.addAll(set);
	}

	private static String nsOf(String id) {
		String x = id.startsWith("#") ? id.substring(1) : id;
		int i = x.indexOf(':');
		return i < 0 ? "minecraft" : x.substring(0, i);
	}

	// ---------- fábricas ----------
	private static String itemCat(Item item) {
		ItemStack st = new ItemStack(item);
		String p = Registries.ITEM.getId(item).getPath();
		if (st.get(DataComponentTypes.EQUIPPABLE) != null && p.matches(".*(helmet|chestplate|leggings|boots|elytra).*")) return "armor";
		if (p.matches(".*(sword|bow|crossbow|trident|mace|arrow|shield|totem).*")) return "combat";
		if (st.get(DataComponentTypes.TOOL) != null || p.matches(".*(shears|flint_and_steel|fishing_rod|brush|spyglass|compass|clock).*")) return "tools";
		if (st.get(DataComponentTypes.FOOD) != null) return "food";
		if (item instanceof BlockItem) return "blocks";
		return "other";
	}

	private static List<Entry> itemEntries(boolean tags) {
		List<Entry> l = new ArrayList<>();
		for (Identifier id : new TreeSet<>(Registries.ITEM.getIds())) {
			Item it = Registries.ITEM.get(id);
			if (it == net.minecraft.item.Items.AIR) continue;
			ItemStack st = new ItemStack(it);
			l.add(new Entry(id.toString(), st.getName().getString(), st, itemCat(it)));
		}
		if (tags) tagEntries(l, Registries.ITEM);
		return l;
	}

	private static <T> void tagEntries(List<Entry> l, Registry<T> reg) {
		TreeSet<String> ids = new TreeSet<>();
		reg.streamTags().forEach(n -> ids.add("#" + n.getTag().id()));
		for (String id : ids) l.add(new Entry(id, Describe.human(id.substring(id.indexOf(':') + 1)) + " (tag)", null, "tags"));
	}

	public static void items(QuestBookScreen s, boolean tags, Consumer<String> onPick) {
		s.openModal(new PickerModal(s, Lang.t("Selecionar item", "Select item"), itemEntries(tags), true, true, onPick));
	}

	public static void blocks(QuestBookScreen s, Consumer<String> onPick) {
		List<Entry> l = new ArrayList<>();
		for (Identifier id : new TreeSet<>(Registries.BLOCK.getIds())) {
			Block b = Registries.BLOCK.get(id);
			ItemStack st = new ItemStack(b.asItem());
			l.add(new Entry(id.toString(), b.getName().getString(), st, "blocks"));
		}
		tagEntries(l, Registries.BLOCK);
		s.openModal(new PickerModal(s, Lang.t("Selecionar bloco", "Select block"), l, true, false, onPick));
	}

	public static void entities(QuestBookScreen s, Consumer<String> onPick) {
		List<Entry> l = new ArrayList<>();
		for (Identifier id : new TreeSet<>(Registries.ENTITY_TYPE.getIds())) {
			l.add(new Entry(id.toString(), Registries.ENTITY_TYPE.get(id).getName().getString(), null, "other"));
		}
		tagEntries(l, Registries.ENTITY_TYPE);
		s.openModal(new PickerModal(s, Lang.t("Selecionar entidade", "Select entity"), l, false, false, onPick));
	}

	private static <T> void dynamic(QuestBookScreen s, String title, net.minecraft.registry.RegistryKey<? extends Registry<T>> key,
									boolean tags, boolean biome, Consumer<String> onPick) {
		var world = MinecraftClient.getInstance().world;
		if (world == null) return;
		Registry<T> reg = world.getRegistryManager().get(key);
		List<Entry> l = new ArrayList<>();
		for (Identifier id : new TreeSet<>(reg.getIds())) {
			l.add(new Entry(id.toString(), Describe.target(biome ? dev.questbook.data.ObjectiveType.Kind.BIOME : dev.questbook.data.ObjectiveType.Kind.STRUCTURE, id.toString()), null, "other"));
		}
		if (tags) tagEntries(l, reg);
		s.openModal(new PickerModal(s, title, l, false, false, onPick));
	}

	public static void biomes(QuestBookScreen s, Consumer<String> onPick) {
		dynamic(s, Lang.t("Selecionar bioma", "Select biome"), RegistryKeys.BIOME, true, true, onPick);
	}

	public static void structures(QuestBookScreen s, Consumer<String> onPick) {
		dynamic(s, Lang.t("Selecionar estrutura", "Select structure"), RegistryKeys.STRUCTURE, true, false, onPick);
	}

	public static void dimensions(QuestBookScreen s, Consumer<String> onPick) {
		Set<String> ids = new TreeSet<>(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
		var h = MinecraftClient.getInstance().getNetworkHandler();
		if (h != null) h.getWorldKeys().forEach(k -> ids.add(k.getValue().toString()));
		List<Entry> l = new ArrayList<>();
		for (String id : ids) l.add(new Entry(id, Describe.human(id.substring(id.indexOf(':') + 1)), null, "other"));
		s.openModal(new PickerModal(s, Lang.t("Selecionar dimensão", "Select dimension"), l, false, false, onPick));
	}

	public static void quests(QuestBookScreen s, Set<String> exclude, Consumer<String> onPick) {
		List<Entry> l = new ArrayList<>();
		for (Chapter c : ClientData.chapters()) {
			for (Quest q : c.quests) {
				if (exclude.contains(q.id)) continue;
				l.add(new Entry(q.id, q.title + "  (" + c.title + ")", ClientData.stack(q.icon), "other"));
			}
		}
		s.openModal(new PickerModal(s, Lang.t("Selecionar missão", "Select quest"), l, false, false, onPick));
	}

	// ---------- filtro ----------
	private void refilter() {
		String q = search.text.trim().toLowerCase(Locale.ROOT);
		String key = q + "|" + cat + "|" + ns;
		if (key.equals(lastKey)) return;
		lastKey = key;
		String nsFilter = namespaces.get(ns);
		String modQ = null;
		if (q.startsWith("@")) {
			int sp = q.indexOf(' ');
			modQ = sp < 0 ? q.substring(1) : q.substring(1, sp);
			q = sp < 0 ? "" : q.substring(sp + 1).trim();
		}
		List<Entry> out = new ArrayList<>();
		for (Entry e : all) {
			if (categories) {
				String cc = CATS[cat];
				if (cc.equals("tags") ? !e.cat.equals("tags") : (!cc.equals("all") && !e.cat.equals(cc))) continue;
				if (cc.equals("all") && e.cat.equals("tags") && q.isEmpty() && modQ == null) continue;
			}
			String n = nsOf(e.id);
			if (!nsFilter.isEmpty() && !n.equals(nsFilter)) continue;
			if (modQ != null && !n.contains(modQ)) continue;
			if (!q.isEmpty() && !e.label.toLowerCase(Locale.ROOT).contains(q) && !e.id.contains(q)) continue;
			out.add(e);
		}
		filtered = out;
		scroll = 0;
	}

	// ---------- desenho ----------
	@Override
	public void render(DrawContext c, int mx, int my) {
		begin(mx, my);
		int w = Math.min(420, sw - 30), h = Math.min(330, sh - 30);
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(c, x, y, w, h, title);
		refilter();

		int cy = y + 32;
		field(c, search, x + 10, cy, w - 20 - (namespaces.size() > 2 ? 110 : 0), 18);
		if (namespaces.size() > 2) {
			String nl = Lang.t("Mod: ", "Mod: ") + (ns == 0 ? Lang.t("todos", "all") : namespaces.get(ns));
			button(c, x + w - 10 - 104, cy, 104, 18, nl, 0, true, () -> { ns = (ns + 1) % namespaces.size(); });
		}
		cy += 24;
		if (categories) {
			int bx = x + 10;
			for (int i = 0; i < CATS.length; i++) {
				String l = catLabel(CATS[i]);
				int bw = Ui.width(l) + 12;
				final int idx = i;
				button(c, bx, cy, bw, 16, l, cat == i ? PRIMARY : GHOST, true, () -> cat = idx);
				bx += bw + 3;
			}
			cy += 22;
		}
		int top = cy, bottom = y + h - 26;
		Ui.rect(c, x + 10, top, w - 20, bottom - top, Ui.PANEL2);
		Text tip = null;
		String tipId = null;
		c.enableScissor(x + 10, top, x + w - 10, bottom);
		int areaW = w - 20 - 6;
		if (grid) {
			int cell = 22, cols = Math.max(1, areaW / cell);
			int rows = (filtered.size() + cols - 1) / cols;
			contentH = rows * cell + 4;
			clampScroll(bottom - top);
			for (int i = 0; i < filtered.size(); i++) {
				int gx = x + 13 + (i % cols) * cell, gy = top + 2 + (i / cols) * cell - scroll;
				if (gy + cell < top || gy > bottom) continue;
				Entry e = filtered.get(i);
				boolean hover = Ui.in(mx, my, gx, gy, cell, cell) && my >= top && my < bottom;
				Ui.frame(c, gx, gy, cell - 1, cell - 1, hover ? Ui.PANEL3 : Ui.PANEL, hover ? Ui.ACCENT : Ui.BORDER);
				if (e.icon != null && !e.icon.isEmpty()) c.drawItem(e.icon, gx + 3, gy + 3);
				else Ui.textC(c, e.id.startsWith("#") ? "#" : "?", gx + cell / 2, gy + 7, Ui.GOLD);
				if (gy >= top - cell + 4 && gy + cell <= bottom + cell - 4 && my >= top && my < bottom) {
					hit(gx, gy, cell - 1, cell - 1, () -> pick(e));
				}
				if (hover) { tip = Text.literal(e.label); tipId = e.id; }
			}
		} else {
			int rh = 14;
			contentH = filtered.size() * rh + 4;
			clampScroll(bottom - top);
			for (int i = 0; i < filtered.size(); i++) {
				int ry = top + 2 + i * rh - scroll;
				if (ry + rh < top || ry > bottom) continue;
				Entry e = filtered.get(i);
				boolean hover = Ui.in(mx, my, x + 12, ry, w - 24, rh) && my >= top && my < bottom;
				if (hover) Ui.rect(c, x + 12, ry, w - 24, rh, Ui.PANEL3);
				int tx = x + 16;
				if (e.icon != null && !e.icon.isEmpty()) {
					c.getMatrices().push();
					c.getMatrices().translate(x + 14f, ry - 1f, 0f);
					c.getMatrices().scale(0.9f, 0.9f, 1f);
					c.drawItem(e.icon, 0, 0);
					c.getMatrices().pop();
					tx += 16;
				}
				Ui.text(c, Ui.clip(e.label, w - 24 - 150), tx, ry + 3, Ui.TEXT);
				Ui.textR(c, Ui.clip(e.id, 145), x + w - 18, ry + 3, Ui.MUTED);
				if (my >= top && my < bottom) hit(x + 12, ry, w - 24, rh, () -> pick(e));
			}
		}
		c.disableScissor();
		Ui.text(c, filtered.size() + Lang.t(" resultado(s)", " result(s)"), x + 10, y + h - 17, Ui.MUTED);
		button(c, x + w - 90, y + h - 22, 80, 16, Lang.t("Cancelar", "Cancel"), 0, true, this::close);
		if (tip != null) {
			c.drawTooltip(Ui.tr(), List.of(tip, Text.literal(tipId).formatted(net.minecraft.util.Formatting.DARK_GRAY)), mx, my);
		}
	}

	private void clampScroll(int viewH) {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
	}

	private void pick(Entry e) {
		onPick.accept(e.id);
		close();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scroll -= (int) (v * 22);
		if (scroll < 0) scroll = 0;
		return true;
	}
}
