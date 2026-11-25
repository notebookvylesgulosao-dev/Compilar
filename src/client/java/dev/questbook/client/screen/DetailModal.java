package dev.questbook.client.screen;

import dev.questbook.client.ClientData;
import dev.questbook.client.Describe;
import dev.questbook.client.Lang;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Janela central com os detalhes da missão; fechada pelo X. */
public class DetailModal extends Modal {
	private final String questId;
	private int scroll, contentH;

	public DetailModal(QuestBookScreen s, String questId) {
		super(s);
		this.questId = questId;
	}

	private static String statusText(ClientData.Status st) {
		return switch (st) {
			case LOCKED -> Lang.t("Bloqueada", "Locked");
			case AVAILABLE -> Lang.t("Disponível", "Available");
			case STARTED -> Lang.t("Em andamento", "In progress");
			case DONE -> Lang.t("Concluída - recompensa pendente", "Completed - reward pending");
			case CLAIMED -> Lang.t("Concluída", "Completed");
		};
	}

	private static int statusColor(ClientData.Status st) {
		return switch (st) {
			case LOCKED -> Ui.MUTED;
			case AVAILABLE -> Ui.ACCENT;
			case STARTED -> Ui.GOLD;
			case DONE, CLAIMED -> Ui.GREEN;
		};
	}

	@Override
	public void render(DrawContext c, int mx, int my) {
		begin(mx, my);
		Quest q = ClientData.quest(questId);
		if (q == null) { close(); return; }
		int w = Math.min(400, sw - 30), h = Math.min(310, sh - 30);
		int x = (sw - w) / 2, y = (sh - h) / 2;
		window(c, x, y, w, h, "");
		ClientData.Status st = ClientData.status(q);

		// cabeçalho
		c.getMatrices().push();
		c.getMatrices().translate(x + 12f, y + 31f, 0f);
		c.getMatrices().scale(2f, 2f, 1f);
		c.drawItem(ClientData.stack(q.icon), 0, 0);
		c.getMatrices().pop();
		Ui.text(c, Ui.clip(q.title, w - 90), x + 52, y + 8, Ui.TEXT);
		Ui.text(c, statusText(st), x + 52, y + 36, statusColor(st));
		int pct = Math.round(ClientData.questFraction(q) * 100);
		Ui.bar(c, x + 52, y + 50, w - 52 - 14, 3, ClientData.questFraction(q), statusColor(st));
		if (!q.objectives.isEmpty()) Ui.textR(c, pct + "%", x + w - 14, y + 36, Ui.MUTED);

		int by = y + 66, fy = y + h - 34, bb = fy - 4;
		Ui.rect(c, x + 1, by - 3, w - 2, 1, Ui.BORDER);
		c.enableScissor(x + 1, by, x + w - 1, bb);
		int cy = by + 4 - scroll;
		int cw = w - 24;
		ItemStack tipStack = null;
		int tipX = 0, tipY = 0;

		if (!q.description.isEmpty()) {
			cy += Ui.wrap(c, q.description, x + 12, cy, cw, Ui.MUTED) + 8;
		}
		if (!ClientData.available(q)) {
			Ui.text(c, Lang.t("Requer", "Requires"), x + 12, cy, Ui.TEXT);
			cy += 12;
			for (String dep : q.dependencies) {
				Quest d = ClientData.quest(dep);
				if (d == null || ClientData.progress.done(dep)) continue;
				c.drawItem(ClientData.stack(d.icon), x + 14, cy - 2);
				Ui.text(c, Ui.clip(d.title, cw - 30), x + 34, cy + 2, Ui.RED);
				final String did = dep;
				hit(x + 12, cy - 2, cw, 18, () -> { close(); screen.focusQuest(ClientData.quest(did)); });
				cy += 18;
			}
			cy += 6;
		}

		if (!q.objectives.isEmpty()) {
			Ui.text(c, Lang.t("Objetivos", "Objectives"), x + 12, cy, Ui.TEXT);
			Ui.rect(c, x + 12 + Ui.width(Lang.t("Objetivos", "Objectives")) + 6, cy + 4, cw - Ui.width(Lang.t("Objetivos", "Objectives")) - 6, 1, Ui.BORDER);
			cy += 14;
			boolean avail = ClientData.available(q);
			boolean qDone = ClientData.progress.done(q.id);
			for (Objective o : q.objectives) {
				int cur = ClientData.progress.get(q.id, o.id), req = o.required();
				boolean done = cur >= req || qDone;
				int rowH = 28;
				c.drawItem(Describe.objectiveIcon(o), x + 14, cy + 2);
				boolean submit = o.type == ObjectiveType.SUBMIT && !done && avail;
				int textW = cw - 30 - (submit ? 62 : 0) - (o.type.counted() ? 40 : 16);
				Ui.text(c, Ui.clip(Describe.objective(o), textW), x + 34, cy + 2, done ? Ui.GREEN : Ui.TEXT);
				if (o.type.counted()) {
					Ui.textR(c, Math.min(cur, req) + "/" + req, x + w - 12 - (submit ? 62 : 0), cy + 2, done ? Ui.GREEN : Ui.MUTED);
					Ui.bar(c, x + 34, cy + 15, cw - 30 - (submit ? 62 : 0), 3, Math.min(1f, cur / (float) req), done ? Ui.GREEN : Ui.ACCENT);
				} else if (done) {
					Ui.textR(c, "OK", x + w - 12, cy + 2, Ui.GREEN);
				}
				if (submit) {
					boolean vis = cy >= by - 4 && cy + 18 <= bb + 4;
					button(c, x + w - 12 - 56, cy, 56, 16, Lang.t("Entregar", "Submit"), PRIMARY, vis,
						() -> ClientData.submit(q, o));
				}
				cy += rowH;
			}
			cy += 4;
		}

		if (!q.rewards.isEmpty()) {
			Ui.text(c, Lang.t("Recompensas", "Rewards"), x + 12, cy, Ui.TEXT);
			Ui.rect(c, x + 12 + Ui.width(Lang.t("Recompensas", "Rewards")) + 6, cy + 4, cw - Ui.width(Lang.t("Recompensas", "Rewards")) - 6, 1, Ui.BORDER);
			cy += 14;
			for (Reward r : q.rewards) {
				ItemStack icon = Describe.rewardIcon(r);
				c.drawItem(icon, x + 14, cy);
				Ui.text(c, Ui.clip(Describe.reward(r), cw - 30), x + 34, cy + 4,
					r.type == RewardType.ITEM ? Ui.TEXT : Ui.GOLD);
				cy += 20;
			}
		}
		contentH = cy - (by - scroll) + 6;
		c.disableScissor();
		int max = Math.max(0, contentH - (bb - by));
		if (scroll > max) scroll = max;
		if (max > 0) {
			int th = Math.max(16, (bb - by) * (bb - by) / contentH);
			int ty = by + (int) ((bb - by - th) * (scroll / (float) max));
			Ui.rect(c, x + w - 4, ty, 2, th, Ui.PANEL3);
		}

		// rodapé
		Ui.rect(c, x + 1, fy - 2, w - 2, 1, Ui.BORDER);
		if (ClientData.admin && screen.editing) {
			String cid = ClientData.book.chapterOf(q.id) == null ? "" : ClientData.book.chapterOf(q.id).id;
			button(c, x + 12, fy + 4, 70, 20, Lang.t("Editar", "Edit"), 0, true, () -> {
				close();
				screen.openModal(new EditQuestModal(screen, cid, q, false));
			});
		}
		if (!q.rewards.isEmpty()) {
			boolean can = ClientData.claimable(q);
			boolean claimed = st == ClientData.Status.CLAIMED;
			String label = can ? Lang.t("Resgatar recompensas", "Claim rewards")
				: claimed ? Lang.t("Resgatada", "Claimed") : Lang.t("Conclua os objetivos", "Finish objectives");
			button(c, x + w - 12 - 160, fy + 4, 160, 20, label, can ? SUCCESS : GHOST, can, () -> ClientData.claim(q));
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scroll = Math.max(0, scroll - (int) (v * 16));
		return true;
	}
}
