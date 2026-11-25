package dev.questbook.client.screen;

import dev.questbook.client.ClientData;
import dev.questbook.client.Describe;
import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.TextField;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.*;
import net.minecraft.client.gui.DrawContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Editor completo de uma missão (somente administradores). */
public class EditQuestModal extends Modal {
	private final String chapterId;
	private String targetChapter;
	private final Quest work;
	private final boolean isNew;
	private final TextField title = new TextField("", 80);
	private final TextField desc = new TextField("", 1500);
	private final TextField xf = new TextField("", 10);
	private final TextField yf = new TextField("", 10);
	private int scroll, contentH;
	private int by, bb;

	public EditQuestModal(QuestBookScreen s, String chapterId, Quest q, boolean isNew) {
		super(s);
		this.chapterId = chapterId;
		this.targetChapter = chapterId;
		this.work = Json.copy(q, Quest.class);
		this.isNew = isNew;
		title.setText(work.title);
		desc.setText(work.description);
		xf.numeric = true;
		yf.numeric = true;
		xf.setText(fmt(work.x));
		yf.setText(fmt(work.y));
	}

	private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

	private boolean vis(int cy, int h) { return cy >= by && cy + h <= bb; }

	private void sectionHeader(DrawContext c, int x, int w, int cy, String label, String addLabel, Runnable add) {
		Ui.text(c, label, x + 12, cy + 4, Ui.TEXT);
		Ui.rect(c, x + 12 + Ui.width(label) + 6, cy + 8, w - 24 - Ui.width(label) - 6 - 100, 1, Ui.BORDER);
		button(c, x + w - 12 - 94, cy, 94, 16, addLabel, PRIMARY, vis(cy, 16), add);
	}

	@Override
	public void render(DrawContext c, int mx, int my) {
		begin(mx, my);
		int w = Math.min(460, sw - 20), h = Math.min(sh - 20, 430);
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(c, x, y, w, h, isNew ? Lang.t("Nova missão", "New quest") : Lang.t("Editar missão", "Edit quest"));
		by = y + 30;
		bb = y + h - 34;
		c.enableScissor(x + 1, by, x + w - 1, bb);
		int cy = by + 6 - scroll;
		int fw = w - 24;

		Ui.text(c, Lang.t("Título", "Title"), x + 12, cy, Ui.MUTED);
		fieldV(c, title, x + 12, cy + 10, fw, 16);
		cy += 32;
		Ui.text(c, Lang.t("Descrição", "Description"), x + 12, cy, Ui.MUTED);
		fieldV(c, desc, x + 12, cy + 10, fw, 16);
		cy += 32;

		Ui.text(c, Lang.t("Ícone", "Icon"), x + 12, cy, Ui.MUTED);
		button(c, x + 12, cy + 10, fw - 160, 20, "        " + work.icon, 0, vis(cy + 10, 20),
			() -> PickerModal.items(screen, false, id -> work.icon = id));
		c.drawItem(ClientData.stack(work.icon), x + 17, cy + 12);
		button(c, x + w - 12 - 150, cy + 10, 150, 20,
			(work.hidden ? "[x] " : "[  ] ") + Lang.t("Oculta até liberar", "Hidden until unlocked"), 0, vis(cy + 10, 20),
			() -> work.hidden = !work.hidden);
		cy += 36;

		Ui.text(c, "X", x + 12, cy + 4, Ui.MUTED);
		fieldV(c, xf, x + 24, cy, 70, 16);
		Ui.text(c, "Y", x + 108, cy + 4, Ui.MUTED);
		fieldV(c, yf, x + 120, cy, 70, 16);
		cy += 26;

		// capítulo (clique para alternar; mover a missão entre capítulos)
		Chapter tc = ClientData.book.chapter(targetChapter);
		String tcName = tc == null ? targetChapter : tc.title;
		Ui.text(c, Lang.t("Capítulo", "Chapter"), x + 12, cy + 4, Ui.MUTED);
		button(c, x + 64, cy, fw - 52, 16, Ui.clip(tcName, fw - 80) + "  >", 0, vis(cy, 16), () -> {
			List<Chapter> cs = ClientData.chapters();
			if (cs.size() < 2) return;
			int cur = 0;
			for (int k = 0; k < cs.size(); k++) if (cs.get(k).id.equals(targetChapter)) cur = k;
			targetChapter = cs.get((cur + 1) % cs.size()).id;
		});
		cy += 26;

		// dependências
		sectionHeader(c, x, w, cy, Lang.t("Dependências", "Dependencies"), "+ " + Lang.t("Missão", "Quest"), () -> {
			Set<String> ex = new HashSet<>(work.dependencies);
			ex.add(work.id);
			for (Chapter ch : ClientData.chapters()) {
				for (Quest q : ch.quests) {
					if (!work.id.isEmpty() && ClientData.book.dependsOn(q.id, work.id)) ex.add(q.id);
				}
			}
			PickerModal.quests(screen, ex, id -> work.dependencies.add(id));
		});
		cy += 22;
		if (work.dependencies.isEmpty()) {
			Ui.text(c, Lang.t("Nenhuma — disponível desde o início.", "None — available from the start."), x + 16, cy + 2, Ui.MUTED);
			cy += 16;
		}
		for (int i = 0; i < work.dependencies.size(); i++) {
			Quest d = ClientData.quest(work.dependencies.get(i));
			String name = d == null ? work.dependencies.get(i) + " (?)" : d.title;
			if (d != null) c.drawItem(ClientData.stack(d.icon), x + 14, cy);
			Ui.text(c, Ui.clip(name, fw - 60), x + 34, cy + 4, Ui.TEXT);
			final int idx = i;
			button(c, x + w - 12 - 18, cy, 18, 16, "x", DANGER, vis(cy, 16), () -> work.dependencies.remove(idx));
			cy += 20;
		}
		cy += 6;

		// objetivos
		sectionHeader(c, x, w, cy, Lang.t("Objetivos", "Objectives"), "+ " + Lang.t("Objetivo", "Objective"), () -> {
			if (work.objectives.size() >= 12) return;
			Objective o = new Objective();
			o.id = QuestBook.shortId();
			o.target = "minecraft:oak_log";
			screen.openModal(new ObjectiveEditModal(screen, o, saved -> work.objectives.add(saved)));
		});
		cy += 22;
		if (work.objectives.isEmpty()) {
			Ui.text(c, Lang.t("Sem objetivos — conclui automaticamente.", "No objectives — completes automatically."), x + 16, cy + 2, Ui.MUTED);
			cy += 16;
		}
		for (int i = 0; i < work.objectives.size(); i++) {
			Objective o = work.objectives.get(i);
			c.drawItem(Describe.objectiveIcon(o), x + 14, cy);
			Ui.text(c, Ui.clip(Describe.objective(o), fw - 100), x + 34, cy + 4, Ui.TEXT);
			final int idx = i;
			button(c, x + w - 12 - 18 - 4 - 50, cy, 50, 16, Lang.t("Editar", "Edit"), 0, vis(cy, 16),
				() -> screen.openModal(new ObjectiveEditModal(screen, o, saved -> work.objectives.set(idx, saved))));
			button(c, x + w - 12 - 18, cy, 18, 16, "x", DANGER, vis(cy, 16), () -> work.objectives.remove(idx));
			cy += 20;
		}
		cy += 6;

		// recompensas
		sectionHeader(c, x, w, cy, Lang.t("Recompensas", "Rewards"), "+ " + Lang.t("Recompensa", "Reward"), () -> {
			if (work.rewards.size() >= 12) return;
			screen.openModal(new RewardEditModal(screen, new Reward(), saved -> work.rewards.add(saved)));
		});
		cy += 22;
		if (work.rewards.isEmpty()) {
			Ui.text(c, Lang.t("Sem recompensas.", "No rewards."), x + 16, cy + 2, Ui.MUTED);
			cy += 16;
		}
		for (int i = 0; i < work.rewards.size(); i++) {
			Reward r = work.rewards.get(i);
			c.drawItem(Describe.rewardIcon(r), x + 14, cy);
			Ui.text(c, Ui.clip(Describe.reward(r), fw - 100), x + 34, cy + 4, Ui.TEXT);
			final int idx = i;
			button(c, x + w - 12 - 18 - 4 - 50, cy, 50, 16, Lang.t("Editar", "Edit"), 0, vis(cy, 16),
				() -> screen.openModal(new RewardEditModal(screen, r, saved -> work.rewards.set(idx, saved))));
			button(c, x + w - 12 - 18, cy, 18, 16, "x", DANGER, vis(cy, 16), () -> work.rewards.remove(idx));
			cy += 20;
		}
		cy += 8;
		contentH = cy - (by - scroll);
		c.disableScissor();
		int maxScroll = Math.max(0, contentH - (bb - by));
		if (scroll > maxScroll) scroll = maxScroll;

		Ui.rect(c, x + 1, bb, w - 2, 1, Ui.BORDER);
		button(c, x + w - 170, y + h - 26, 78, 18, Lang.t("Salvar", "Save"), PRIMARY, true, this::save);
		button(c, x + w - 86, y + h - 26, 76, 18, Lang.t("Cancelar", "Cancel"), 0, true, this::close);
		if (!isNew) {
			button(c, x + 12, y + h - 26, 60, 18, Lang.t("Excluir", "Delete"), DANGER, true, () ->
				screen.openModal(new ConfirmModal(screen,
					Lang.t("Excluir a missão \"" + work.title + "\"?", "Delete quest \"" + work.title + "\"?"),
					() -> { ClientData.deleteQuest(work); close(); })));
		}
	}

	private void fieldV(DrawContext c, TextField f, int x, int y, int w, int h) {
		if (vis(y, h)) field(c, f, x, y, w, h);
		else f.render(c, x, y, w, h, -9999, -9999);
	}

	private void save() {
		work.title = title.text.trim().isEmpty() ? Lang.t("Sem título", "Untitled") : title.text.trim();
		work.description = desc.text.trim();
		work.x = xf.asDouble(work.x);
		work.y = yf.asDouble(work.y);
		ClientData.saveQuest(targetChapter, work);
		close();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scroll = Math.max(0, scroll - (int) (v * 16));
		return true;
	}
}
