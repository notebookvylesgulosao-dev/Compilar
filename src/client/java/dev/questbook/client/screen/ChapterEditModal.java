package dev.questbook.client.screen;

import dev.questbook.client.ClientData;
import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.TextField;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.Chapter;
import dev.questbook.data.Json;
import net.minecraft.client.gui.DrawContext;

public class ChapterEditModal extends Modal {
	private final Chapter work;
	private final boolean isNew;
	private final TextField title = new TextField("", 60);
	private final TextField desc = new TextField("", 600);

	public ChapterEditModal(QuestBookScreen s, Chapter c, boolean isNew) {
		super(s);
		this.work = Json.copy(c, Chapter.class);
		this.isNew = isNew;
		title.setText(work.title);
		desc.setText(work.description);
	}

	@Override
	public void render(DrawContext cx, int mx, int my) {
		begin(mx, my);
		int w = Math.min(380, sw - 30), h = 200;
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(cx, x, y, w, h, isNew ? Lang.t("Novo capítulo", "New chapter") : Lang.t("Editar capítulo", "Edit chapter"));
		int cy = y + 34;
		Ui.text(cx, Lang.t("Título", "Title"), x + 12, cy, Ui.MUTED);
		field(cx, title, x + 12, cy + 11, w - 24, 18);
		cy += 36;
		Ui.text(cx, Lang.t("Descrição", "Description"), x + 12, cy, Ui.MUTED);
		field(cx, desc, x + 12, cy + 11, w - 24, 18);
		cy += 36;
		Ui.text(cx, Lang.t("Ícone", "Icon"), x + 12, cy, Ui.MUTED);
		cy += 11;
		button(cx, x + 12, cy, w - 24, 22, "        " + work.icon, 0, true,
			() -> PickerModal.items(screen, false, id -> work.icon = id));
		cx.drawItem(ClientData.stack(work.icon), x + 18, cy + 3);
		if (!isNew) {
			button(cx, x + 12, y + h - 26, 40, 18, Lang.t("Subir", "Up"), 0, true, () -> ClientData.moveChapter(work, -1));
			button(cx, x + 56, y + h - 26, 46, 18, Lang.t("Descer", "Down"), 0, true, () -> ClientData.moveChapter(work, 1));
			button(cx, x + 106, y + h - 26, 52, 18, Lang.t("Excluir", "Delete"), DANGER, true, () ->
				screen.openModal(new ConfirmModal(screen,
					Lang.t("Excluir este capítulo e todas as suas missões?", "Delete this chapter and all its quests?"),
					() -> { ClientData.deleteChapter(work); close(); })));
		}
		button(cx, x + w - 170, y + h - 26, 78, 18, Lang.t("Salvar", "Save"), PRIMARY, true, () -> {
			work.title = title.text.trim();
			work.description = desc.text.trim();
			ClientData.saveChapter(work);
			close();
		});
		button(cx, x + w - 86, y + h - 26, 76, 18, Lang.t("Cancelar", "Cancel"), 0, true, this::close);
	}
}
