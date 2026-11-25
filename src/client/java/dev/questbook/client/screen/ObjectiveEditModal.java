package dev.questbook.client.screen;

import dev.questbook.client.Describe;
import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.TextField;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.Json;
import dev.questbook.data.Objective;
import dev.questbook.data.ObjectiveType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

public class ObjectiveEditModal extends Modal {
	private final Objective work;
	private final Consumer<Objective> onSave;
	private final TextField target = new TextField("", 64);
	private final TextField count = new TextField("", 8);
	private final TextField extra = new TextField("", 64);

	public ObjectiveEditModal(QuestBookScreen s, Objective o, Consumer<Objective> onSave) {
		super(s);
		this.work = Json.copy(o, Objective.class);
		this.onSave = onSave;
		target.setText(work.target);
		count.numeric = true;
		count.setText(String.valueOf(work.count));
		extra.setText(work.extra);
		target.hint = "minecraft:stone";
	}

	private void cycle(int d) {
		ObjectiveType[] v = ObjectiveType.values();
		ObjectiveType old = work.type;
		work.type = v[(work.type.ordinal() + d + v.length) % v.length];
		if (old.kind != work.type.kind) target.setText("");
		if (work.type == ObjectiveType.LOCATION) count.setText("5");
	}

	private String hint() {
		return switch (work.type) {
			case COLLECT -> Lang.t("Conta itens no inventário (não consome).", "Counts items in inventory (not consumed).");
			case CRAFT -> Lang.t("Conta itens fabricados (bancada, fornalha...).", "Counts crafted items (table, furnace...).");
			case SUBMIT -> Lang.t("O jogador entrega os itens pelo botão Entregar.", "Player hands items in with the Submit button.");
			case KILL -> Lang.t("Conta entidades derrotadas pelo jogador.", "Counts entities killed by the player.");
			case BIOME -> Lang.t("Concluído ao entrar no bioma.", "Completed when entering the biome.");
			case STRUCTURE -> Lang.t("Concluído ao estar dentro da estrutura.", "Completed when inside the structure.");
			case DIMENSION -> Lang.t("Concluído ao entrar na dimensão.", "Completed when entering the dimension.");
			case LOCATION -> Lang.t("Alvo \"x,y,z\"; quantidade = raio em blocos; dimensão opcional.", "Target \"x,y,z\"; count = radius; optional dimension.");
			case INTERACT_BLOCK -> Lang.t("Conta cliques direitos no bloco.", "Counts right-clicks on the block.");
			case INTERACT_ENTITY -> Lang.t("Conta interações com a entidade.", "Counts interactions with the entity.");
		};
	}

	private void choose() {
		Consumer<String> set = id -> target.setText(id);
		switch (work.type.kind) {
			case ITEM -> PickerModal.items(screen, true, set);
			case BLOCK -> PickerModal.blocks(screen, set);
			case ENTITY -> PickerModal.entities(screen, set);
			case BIOME -> PickerModal.biomes(screen, set);
			case STRUCTURE -> PickerModal.structures(screen, set);
			case DIMENSION -> PickerModal.dimensions(screen, set);
			case NONE -> {
				var p = MinecraftClient.getInstance().player;
				if (p != null) {
					BlockPos b = p.getBlockPos();
					target.setText(b.getX() + "," + b.getY() + "," + b.getZ());
					extra.setText(p.getWorld().getRegistryKey().getValue().toString());
				}
			}
		}
	}

	@Override
	public void render(DrawContext c, int mx, int my) {
		begin(mx, my);
		int w = Math.min(380, sw - 30), h = 214;
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(c, x, y, w, h, Lang.t("Editar objetivo", "Edit objective"));
		int cy = y + 34;
		Ui.text(c, Lang.t("Tipo", "Type"), x + 12, cy, Ui.MUTED);
		cy += 11;
		button(c, x + 12, cy, 20, 18, "<", 0, true, () -> cycle(-1));
		Ui.frame(c, x + 34, cy, w - 68, 18, Ui.PANEL2, Ui.BORDER);
		Ui.textC(c, Describe.typeName(work.type), x + w / 2, cy + 5, Ui.TEXT);
		button(c, x + w - 32, cy, 20, 18, ">", 0, true, () -> cycle(1));
		cy += 24;
		boolean loc = work.type == ObjectiveType.LOCATION;
		Ui.text(c, loc ? Lang.t("Coordenadas (x,y,z)", "Coordinates (x,y,z)") : Lang.t("Alvo (id ou #tag)", "Target (id or #tag)"), x + 12, cy, Ui.MUTED);
		cy += 11;
		field(c, target, x + 12, cy, w - 24 - 96, 18);
		button(c, x + w - 12 - 90, cy, 90, 18, loc ? Lang.t("Minha posição", "My position") : Lang.t("Escolher...", "Choose..."), PRIMARY, true, this::choose);
		cy += 24;
		if (work.type.counted() || loc) {
			Ui.text(c, loc ? Lang.t("Raio (blocos)", "Radius (blocks)") : Lang.t("Quantidade", "Amount"), x + 12, cy, Ui.MUTED);
			cy += 11;
			field(c, count, x + 12, cy, 90, 18);
			if (loc) {
				field(c, extra, x + 112, cy, w - 124, 18);
				extra.hint = Lang.t("dimensão (opcional)", "dimension (optional)");
			}
			cy += 24;
		}
		Ui.wrap(c, hint(), x + 12, cy, w - 24, Ui.MUTED);
		button(c, x + w - 170, y + h - 26, 78, 18, Lang.t("Salvar", "Save"), PRIMARY, true, () -> {
			work.target = target.text.trim();
			work.count = Math.max(1, count.asInt(1));
			work.extra = extra.text.trim();
			close();
			onSave.accept(work);
		});
		button(c, x + w - 86, y + h - 26, 76, 18, Lang.t("Cancelar", "Cancel"), 0, true, this::close);
	}
}
