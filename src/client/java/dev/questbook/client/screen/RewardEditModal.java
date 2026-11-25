package dev.questbook.client.screen;

import dev.questbook.client.Describe;
import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.TextField;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.Json;
import dev.questbook.data.Reward;
import dev.questbook.data.RewardType;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

public class RewardEditModal extends Modal {
	private final Reward work;
	private final Consumer<Reward> onSave;
	private final TextField value = new TextField("", 200);
	private final TextField amount = new TextField("", 8);

	public RewardEditModal(QuestBookScreen s, Reward r, Consumer<Reward> onSave) {
		super(s);
		this.work = Json.copy(r, Reward.class);
		this.onSave = onSave;
		value.setText(work.value);
		amount.numeric = true;
		amount.setText(String.valueOf(work.amount));
	}

	private void cycle(int d) {
		RewardType[] v = RewardType.values();
		RewardType old = work.type;
		work.type = v[(work.type.ordinal() + d + v.length) % v.length];
		if (old != work.type) {
			value.setText(work.type == RewardType.ITEM ? "minecraft:diamond" : "");
			if (work.type == RewardType.COMMAND) value.setText("give %player% minecraft:apple 1");
		}
	}

	@Override
	public void render(DrawContext c, int mx, int my) {
		begin(mx, my);
		int w = Math.min(380, sw - 30), h = 170;
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(c, x, y, w, h, Lang.t("Editar recompensa", "Edit reward"));
		int cy = y + 34;
		Ui.text(c, Lang.t("Tipo", "Type"), x + 12, cy, Ui.MUTED);
		cy += 11;
		button(c, x + 12, cy, 20, 18, "<", 0, true, () -> cycle(-1));
		Ui.frame(c, x + 34, cy, w - 68, 18, Ui.PANEL2, Ui.BORDER);
		Ui.textC(c, Describe.rewardTypeName(work.type), x + w / 2, cy + 5, Ui.TEXT);
		button(c, x + w - 32, cy, 20, 18, ">", 0, true, () -> cycle(1));
		cy += 26;
		switch (work.type) {
			case ITEM -> {
				Ui.text(c, Lang.t("Item", "Item"), x + 12, cy, Ui.MUTED);
				cy += 11;
				field(c, value, x + 12, cy, w - 24 - 96, 18);
				button(c, x + w - 102, cy, 90, 18, Lang.t("Escolher...", "Choose..."), PRIMARY, true,
					() -> PickerModal.items(screen, false, id -> value.setText(id)));
				cy += 24;
				Ui.text(c, Lang.t("Quantidade", "Amount"), x + 12, cy, Ui.MUTED);
				field(c, amount, x + 80, cy - 4, 80, 16);
			}
			case XP_POINTS, XP_LEVELS -> {
				Ui.text(c, work.type == RewardType.XP_LEVELS ? Lang.t("Níveis", "Levels") : Lang.t("Pontos", "Points"), x + 12, cy, Ui.MUTED);
				cy += 11;
				field(c, amount, x + 12, cy, 100, 18);
			}
			case COMMAND -> {
				Ui.text(c, Lang.t("Comando (use %player%)", "Command (use %player%)"), x + 12, cy, Ui.MUTED);
				cy += 11;
				field(c, value, x + 12, cy, w - 24, 18);
				cy += 24;
				Ui.wrap(c, Lang.t("Executado pelo servidor com permissão total ao resgatar.", "Run by the server with full permission on claim."), x + 12, cy, w - 24, Ui.MUTED);
			}
		}
		button(c, x + w - 170, y + h - 26, 78, 18, Lang.t("Salvar", "Save"), PRIMARY, true, () -> {
			work.value = value.text.trim();
			work.amount = Math.max(1, amount.asInt(1));
			close();
			onSave.accept(work);
		});
		button(c, x + w - 86, y + h - 26, 76, 18, Lang.t("Cancelar", "Cancel"), 0, true, this::close);
	}
}
