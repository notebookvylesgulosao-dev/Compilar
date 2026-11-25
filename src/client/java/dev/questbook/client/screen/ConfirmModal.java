package dev.questbook.client.screen;

import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;

public class ConfirmModal extends Modal {
	private final String message;
	private final Runnable onYes;

	public ConfirmModal(QuestBookScreen s, String message, Runnable onYes) {
		super(s);
		this.message = message;
		this.onYes = onYes;
	}

	@Override
	public void render(DrawContext c, int mx, int my) {
		begin(mx, my);
		int w = Math.min(300, sw - 30);
		int th = Ui.wrapHeight(message, w - 24);
		int h = 70 + th;
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(c, x, y, w, h, Lang.t("Confirmar", "Confirm"));
		Ui.wrap(c, message, x + 12, y + 36, w - 24, Ui.TEXT);
		button(c, x + w - 170, y + h - 26, 78, 18, Lang.t("Confirmar", "Confirm"), DANGER, true, () -> { close(); onYes.run(); });
		button(c, x + w - 86, y + h - 26, 76, 18, Lang.t("Cancelar", "Cancel"), 0, true, this::close);
	}
}
