package dev.questbook.client.screen;

import dev.questbook.client.ClientData;
import dev.questbook.client.Describe;
import dev.questbook.client.Lang;
import dev.questbook.client.QuestBookClient;
import dev.questbook.client.ui.Modal;
import dev.questbook.client.ui.TextField;
import dev.questbook.client.ui.Ui;
import dev.questbook.data.*;
import dev.questbook.net.Payloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Tela principal do Quest Book: capítulos, canvas com árvore de missões, busca e editor. */
public class QuestBookScreen extends Screen {
	private static final int CELL = 44, NODE = 30, TOP = 26, SIDE = 132;
	private static final double MIN_ZOOM = 0.35, MAX_ZOOM = 3.0;

	private static final class View {
		double ox, oy, zoom = 1.0;
		boolean init;
	}

	private record Btn(int x, int y, int w, int h, Runnable action) {}

	private static final Map<String, View> VIEWS = new HashMap<>();
	private static String lastChapter;

	public boolean editing;
	private String chapterId = lastChapter;
	private final TextField search = new TextField("", 40);
	private List<Quest> results = List.of();
	private Set<String> matchIds = Set.of();
	private final List<Modal> modals = new ArrayList<>();
	private final List<Btn> btns = new ArrayList<>();
	private int sbScroll;
	private int wx, wy, ww, wh, canX, canY, canW, canH, searchX, searchW;

	private int pressButton = -1;
	private double pressX, pressY;
	private boolean dragging;
	private Quest dragQuest;
	private double dragRawX, dragRawY;
	private Quest linkFrom;
	private Quest hoverQuest;

	public QuestBookScreen() {
		super(Text.literal("Quest Book"));
	}

	@Override
	protected void init() {
		search.hint = Lang.t("Buscar missão...", "Search quest...");
		search.onChange = t -> recomputeSearch();
		search.onEnter = () -> { if (!results.isEmpty()) focusQuest(results.get(0)); };
		ClientData.send(new Payloads.Refresh());
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void removed() {
		lastChapter = chapterId;
	}

	// ================= modais =================
	public void openModal(Modal m) { modals.add(m); }

	public void closeModal(Modal m) { modals.remove(m); }

	// ================= geometria / vista =================
	private void geometry() {
		int m = 10;
		wx = m;
		wy = m;
		ww = width - 2 * m;
		wh = height - 2 * m;
		canX = wx + 1 + SIDE;
		canY = wy + 1 + TOP;
		canW = ww - 2 - SIDE;
		canH = wh - 2 - TOP;
	}

	private Chapter currentChapter() {
		List<Chapter> cs = ClientData.chapters();
		if (cs.isEmpty()) return null;
		Chapter c = chapterId == null ? null : ClientData.book.chapter(chapterId);
		if (c == null) {
			c = cs.get(0);
			chapterId = c.id;
		}
		return c;
	}

	private View view(Chapter ch) {
		View v = VIEWS.computeIfAbsent(ch.id, k -> new View());
		if (!v.init) {
			centerOn(ch, v);
			v.init = true;
		}
		return v;
	}

	private void centerOn(Chapter ch, View v) {
		if (ch.quests.isEmpty()) { v.ox = 0; v.oy = 0; return; }
		double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
		for (Quest q : ch.quests) {
			minX = Math.min(minX, q.x); maxX = Math.max(maxX, q.x);
			minY = Math.min(minY, q.y); maxY = Math.max(maxY, q.y);
		}
		v.ox = -((minX + maxX) / 2) * CELL * v.zoom;
		v.oy = -((minY + maxY) / 2) * CELL * v.zoom;
	}

	private double sx(View v, double qx) { return canX + canW / 2.0 + v.ox + qx * CELL * v.zoom; }

	private double sy(View v, double qy) { return canY + canH / 2.0 + v.oy + qy * CELL * v.zoom; }

	private double qxOf(Quest q) { return q == dragQuest && dragging ? snap(dragRawX) : q.x; }

	private double qyOf(Quest q) { return q == dragQuest && dragging ? snap(dragRawY) : q.y; }

	private static double snap(double d) { return Math.round(d * 2) / 2.0; }

	private boolean visible(Quest q) {
		return editing || !q.hidden || ClientData.available(q) || ClientData.progress.done(q.id);
	}

	private Quest nodeAt(double mx, double my) {
		Chapter ch = currentChapter();
		if (ch == null || !Ui.in(mx, my, canX, canY, canW, canH)) return null;
		View v = view(ch);
		int s = Math.max(8, (int) (NODE * v.zoom));
		for (int i = ch.quests.size() - 1; i >= 0; i--) {
			Quest q = ch.quests.get(i);
			if (!visible(q)) continue;
			double cx = sx(v, qxOf(q)), cy = sy(v, qyOf(q));
			if (Ui.in(mx, my, (int) (cx - s / 2.0), (int) (cy - s / 2.0), s, s)) return q;
		}
		return null;
	}

	/** Centraliza a quest, troca de capítulo e abre os detalhes. */
	public void focusQuest(Quest q) {
		if (q == null) return;
		Chapter ch = ClientData.book.chapterOf(q.id);
		if (ch == null) return;
		chapterId = ch.id;
		View v = view(ch);
		v.ox = -q.x * CELL * v.zoom;
		v.oy = -q.y * CELL * v.zoom;
		search.focused = false;
		openModal(new DetailModal(this, q.id));
	}

	// ================= busca =================
	private boolean matches(Quest q, String qry) {
		if (q.title.toLowerCase(Locale.ROOT).contains(qry) || q.description.toLowerCase(Locale.ROOT).contains(qry)
			|| q.icon.contains(qry)) return true;
		for (Objective o : q.objectives) {
			if (o.target.contains(qry) || Describe.objective(o).toLowerCase(Locale.ROOT).contains(qry)) return true;
		}
		return false;
	}

	private void recomputeSearch() {
		String qry = search.text.trim().toLowerCase(Locale.ROOT);
		if (qry.isEmpty()) { results = List.of(); matchIds = Set.of(); return; }
		List<Quest> r = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (Chapter c : ClientData.chapters()) {
			for (Quest q : c.quests) {
				if (!visible(q) || !matches(q, qry)) continue;
				ids.add(q.id);
				if (r.size() < 8) r.add(q);
			}
		}
		results = r;
		matchIds = ids;
	}

	// ================= desenho =================
	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) {
		btns.clear();
		geometry();
		if (!ClientData.admin) { editing = false; linkFrom = null; }
		boolean modalOpen = !modals.isEmpty();
		int mx = modalOpen ? -9999 : mouseX, my = modalOpen ? -9999 : mouseY;

		Ui.rect(c, 0, 0, width, height, 0xB8000000);
		Ui.frame(c, wx, wy, ww, wh, Ui.BG, Ui.BORDER);
		Chapter ch = currentChapter();
		hoverQuest = null;
		renderCanvas(c, mx, my, ch);
		renderSidebar(c, mx, my);
		renderTop(c, mx, my, ch);
		c.getMatrices().push();
		c.getMatrices().translate(0f, 0f, 250f);
		renderSearchResults(c, mx, my);
		if (hoverQuest != null && !search.focused) tooltip(c, mx, my, hoverQuest);
		c.getMatrices().pop();

		for (int i = 0; i < modals.size(); i++) {
			boolean top = i == modals.size() - 1;
			c.getMatrices().push();
			c.getMatrices().translate(0f, 0f, 350f * (i + 1));
			modals.get(i).render(c, top ? mouseX : -9999, top ? mouseY : -9999);
			c.getMatrices().pop();
		}
	}

	private void btn(int x, int y, int w, int h, Runnable a) { btns.add(new Btn(x, y, w, h, a)); }

	private void drawBtn(DrawContext c, int mx, int my, int x, int y, int w, int h, String label, int style, boolean enabled, Runnable a) {
		Ui.drawButton(c, x, y, w, h, label, style, enabled, enabled && Ui.in(mx, my, x, y, w, h));
		if (enabled) btn(x, y, w, h, a);
	}

	private void renderCanvas(DrawContext c, int mx, int my, Chapter ch) {
		Ui.rect(c, canX, canY, canW, canH, Ui.CANVAS);
		if (ch == null) {
			Ui.textC(c, Lang.t("Nenhum capítulo disponível.", "No chapters available."), canX + canW / 2, canY + canH / 2 - 4, Ui.MUTED);
			return;
		}
		View v = view(ch);
		boolean canvasHover = Ui.in(mx, my, canX, canY, canW, canH);
		c.enableScissor(canX, canY, canX + canW, canY + canH);

		// grade
		double step = CELL * v.zoom;
		if (step >= 10) {
			double gx0 = canX + (((sx(v, 0) - canX) % step) + step) % step;
			for (double gx = gx0; gx < canX + canW; gx += step) c.fill((int) gx, canY, (int) gx + 1, canY + canH, 0xFF14171C);
			double gy0 = canY + (((sy(v, 0) - canY) % step) + step) % step;
			for (double gy = gy0; gy < canY + canH; gy += step) c.fill(canX, (int) gy, canX + canW, (int) gy + 1, 0xFF14171C);
		}

		// conexões
		float th = (float) Math.max(1.0, 2.0 * v.zoom);
		for (Quest q : ch.quests) {
			if (!visible(q)) continue;
			for (String dep : q.dependencies) {
				Quest d = ClientData.quest(dep);
				if (d == null || ClientData.book.chapterOf(dep) != ch || !visible(d)) continue;
				float x1 = (float) sx(v, qxOf(d)), y1 = (float) sy(v, qyOf(d));
				float x2 = (float) sx(v, qxOf(q)), y2 = (float) sy(v, qyOf(q));
				int col = ClientData.progress.done(dep) ? 0xFF2F8F5B : 0xFF353B48;
				Ui.line(c, x1, y1, x2, y2, th, col);
				float mxp = x1 + (x2 - x1) * 0.55f, myp = y1 + (y2 - y1) * 0.55f;
				double ang = Math.atan2(y2 - y1, x2 - x1);
				float al = (float) (7 * Math.max(0.6, v.zoom));
				for (int side = -1; side <= 1; side += 2) {
					double a = ang + Math.PI + side * 0.5;
					Ui.line(c, mxp, myp, mxp + (float) Math.cos(a) * al, myp + (float) Math.sin(a) * al, th, col);
				}
			}
		}

		// nós
		String qry = search.text.trim();
		boolean searching = !qry.isEmpty();
		long now = Util.getMeasuringTimeMs();
		for (Quest q : ch.quests) {
			if (!visible(q)) continue;
			ClientData.Status st = ClientData.status(q);
			double cx = sx(v, qxOf(q)), cy = sy(v, qyOf(q));
			int s = Math.max(8, (int) (NODE * v.zoom));
			int x0 = (int) (cx - s / 2.0), y0 = (int) (cy - s / 2.0);
			if (x0 + s < canX || x0 > canX + canW || y0 + s < canY || y0 > canY + canH) continue;
			int border = switch (st) {
				case LOCKED -> 0xFF333844;
				case AVAILABLE -> Ui.ACCENT;
				case STARTED -> Ui.GOLD;
				case DONE -> Ui.GREEN;
				case CLAIMED -> 0xFF2E8F5E;
			};
			boolean hover = canvasHover && Ui.in(mx, my, x0, y0, s, s);
			if (hover) hoverQuest = q;
			boolean match = searching && matchIds.contains(q.id);
			if (match) Ui.rect(c, x0 - 3, y0 - 3, s + 6, s + 6, Ui.alpha(0xFFFFFFFF, 0.35f + 0.25f * (float) Math.sin(now / 200.0)));
			if (q == linkFrom) Ui.rect(c, x0 - 3, y0 - 3, s + 6, s + 6, 0xFFFFFFFF);
			Ui.frame(c, x0, y0, s, s, st == ClientData.Status.LOCKED ? 0xFF14161B : (hover ? Ui.PANEL3 : Ui.PANEL2), border);

			c.getMatrices().push();
			c.getMatrices().translate((float) cx, (float) cy, 0f);
			float sc = (s - 6) / 16f;
			c.getMatrices().scale(sc, sc, 1f);
			c.drawItem(ClientData.stack(q.icon), -8, -8);
			c.getMatrices().pop();

			c.getMatrices().push();
			c.getMatrices().translate(0f, 0f, 200f);
			if (st == ClientData.Status.LOCKED) Ui.rect(c, x0 + 1, y0 + 1, s - 2, s - 2, 0xAA101216);
			if (searching && !match) Ui.rect(c, x0, y0, s, s, 0xBB101216);
			if (q.hidden && editing) Ui.rect(c, x0, y0, s, 2, Ui.MUTED);
			if (st == ClientData.Status.STARTED) Ui.bar(c, x0 + 2, y0 + s - 4, s - 4, 2, ClientData.questFraction(q), Ui.GOLD);
			if (st == ClientData.Status.DONE || st == ClientData.Status.CLAIMED) {
				int kx = x0 + s - 9, ky = y0 + s - 8;
				Ui.line(c, kx, ky + 3, kx + 2, ky + 5, 1, Ui.GREEN);
				Ui.line(c, kx + 2, ky + 5, kx + 6, ky, 1, Ui.GREEN);
			}
			if (ClientData.claimable(q)) {
				int bx = x0 + s - 8, by = y0 - 4;
				Ui.rect(c, bx, by, 10, 10, Ui.GOLD);
				Ui.textC(c, "!", bx + 5, by + 1, 0xFF1A1400);
			}
			c.getMatrices().pop();
		}
		c.disableScissor();

		if (editing) {
			String hint = Lang.t("Arraste: mover  |  Botão direito: nova/editar  |  Shift+clique A depois B: ligar",
				"Drag: move  |  Right-click: new/edit  |  Shift+click A then B: link");
			Ui.rect(c, canX, canY + canH - 14, canW, 14, 0xCC0D0E12);
			Ui.text(c, Ui.clip(hint, canW - 12), canX + 6, canY + canH - 11, Ui.GOLD);
		}
	}

	private void renderSidebar(DrawContext c, int mx, int my) {
		int x0 = wx + 1, y0 = canY;
		Ui.rect(c, x0, y0, SIDE, canH, Ui.PANEL);
		Ui.rect(c, x0 + SIDE - 1, y0, 1, canH, Ui.BORDER);
		int listH = canH - (editing ? 26 : 0);
		int rowH = 28;
		List<Chapter> cs = ClientData.chapters();
		int content = cs.size() * rowH + 8;
		sbScroll = Math.max(0, Math.min(sbScroll, Math.max(0, content - listH)));
		c.enableScissor(x0, y0, x0 + SIDE - 1, y0 + listH);
		int y = y0 + 4 - sbScroll;
		for (Chapter ch : cs) {
			boolean sel = ch.id.equals(chapterId);
			boolean hover = Ui.in(mx, my, x0, y, SIDE - 1, rowH) && my < y0 + listH;
			if (sel) Ui.rect(c, x0, y, SIDE - 1, rowH, Ui.PANEL2);
			else if (hover) Ui.rect(c, x0, y, SIDE - 1, rowH, 0xFF191C22);
			if (sel) Ui.rect(c, x0, y, 2, rowH, Ui.ACCENT);
			c.drawItem(ClientData.stack(ch.icon), x0 + 8, y + 6);
			Ui.text(c, Ui.clip(ch.title, SIDE - 34 - (editing ? 18 : 0)), x0 + 28, y + 5, sel ? Ui.TEXT : 0xFFC0C5D2);
			int[] cnt = ClientData.chapterCounts(ch);
			Ui.bar(c, x0 + 28, y + 18, SIDE - 28 - 10, 3, cnt[1] == 0 ? 0 : cnt[0] / (float) cnt[1], cnt[0] == cnt[1] && cnt[1] > 0 ? Ui.GREEN : Ui.ACCENT);
			if (y + rowH > y0 && y < y0 + listH) {
				final Chapter target = ch;
				btn(x0, y, SIDE - 1, rowH, () -> { chapterId = target.id; });
			}
			if (editing && y >= y0 - rowH && y < y0 + listH) {
				final Chapter target = ch;
				int bx = x0 + SIDE - 20, by = y + 4;
				Ui.drawButton(c, bx, by, 16, 16, "...", Ui.BTN_GHOST, true, Ui.in(mx, my, bx, by, 16, 16));
				btn(bx, by, 16, 16, () -> openModal(new ChapterEditModal(this, target, false)));
			}
			y += rowH;
		}
		c.disableScissor();
		if (editing) {
			drawBtn(c, mx, my, x0 + 6, y0 + canH - 22, SIDE - 13, 18, "+ " + Lang.t("Capítulo", "Chapter"), Ui.BTN_PRIMARY, true,
				() -> openModal(new ChapterEditModal(this, new Chapter(), true)));
		}
	}

	private void renderTop(DrawContext c, int mx, int my, Chapter ch) {
		int x0 = wx + 1, y0 = wy + 1;
		Ui.rect(c, x0, y0, ww - 2, TOP, Ui.PANEL);
		Ui.rect(c, x0, y0 + TOP - 1, ww - 2, 1, Ui.BORDER);
		Ui.text(c, "Quest Book", x0 + 10, y0 + 9, Ui.TEXT);
		int rx = wx + ww - 1 - 6;

		// fechar
		rx -= 18;
		boolean hx = Ui.in(mx, my, rx, y0 + 4, 18, 18);
		Ui.rect(c, rx, y0 + 4, 18, 18, hx ? 0xFF8A3030 : Ui.PANEL3);
		Ui.line(c, rx + 5, y0 + 9, rx + 13, y0 + 17, 1, Ui.TEXT);
		Ui.line(c, rx + 13, y0 + 9, rx + 5, y0 + 17, 1, Ui.TEXT);
		btn(rx, y0 + 4, 18, 18, this::close);

		if (ClientData.admin) {
			String l = Lang.t("Editor", "Editor") + (editing ? ": ON" : "");
			int w = Ui.width(l) + 14;
			rx -= w + 4;
			drawBtn(c, mx, my, rx, y0 + 4, w, 18, l, editing ? Ui.BTN_PRIMARY : 0, true, () -> { editing = !editing; linkFrom = null; });
		}
		int n = ClientData.claimableCount();
		String cl = Lang.t("Resgatar tudo", "Claim all") + (n > 0 ? " (" + n + ")" : "");
		int cw = Ui.width(cl) + 14;
		rx -= cw + 4;
		drawBtn(c, mx, my, rx, y0 + 4, cw, 18, cl, n > 0 ? Ui.BTN_SUCCESS : 0, n > 0, ClientData::claimAll);
		String ctr = Lang.t("Centralizar", "Center");
		int tw = Ui.width(ctr) + 14;
		rx -= tw + 4;
		drawBtn(c, mx, my, rx, y0 + 4, tw, 18, ctr, 0, ch != null, () -> {
			View v = view(ch);
			v.zoom = 1.0;
			centerOn(ch, v);
		});

		searchW = Math.min(170, Math.max(80, rx - 4 - (x0 + 10 + Ui.width("Quest Book") + 90)));
		searchX = rx - 6 - searchW;
		search.render(c, searchX, y0 + 4, searchW, 18, mx, my);
		if (ch != null) {
			int avail = searchX - (x0 + 10 + Ui.width("Quest Book") + 12) - 6;
			if (avail > 30) Ui.text(c, Ui.clip(ch.title, avail), x0 + 10 + Ui.width("Quest Book") + 12, y0 + 9, Ui.MUTED);
		}
	}

	private void renderSearchResults(DrawContext c, int mx, int my) {
		if (!search.focused || search.text.trim().isEmpty() || !modals.isEmpty()) return;
		int w = Math.max(searchW, 250), x = searchX + searchW - w, y = wy + TOP + 2;
		int rowH = 20;
		int h = Math.max(1, results.size()) * rowH + 4;
		Ui.frame(c, x, y, w, h, Ui.PANEL, Ui.ACCENT);
		if (results.isEmpty()) {
			Ui.text(c, Lang.t("Nenhum resultado", "No results"), x + 8, y + 8, Ui.MUTED);
			return;
		}
		int ry = y + 2;
		for (Quest q : results) {
			boolean hover = Ui.in(mx, my, x + 2, ry, w - 4, rowH);
			if (hover) Ui.rect(c, x + 2, ry, w - 4, rowH, Ui.PANEL3);
			c.drawItem(ClientData.stack(q.icon), x + 5, ry + 2);
			Ui.text(c, Ui.clip(q.title, w - 120), x + 26, ry + 6, Ui.TEXT);
			Chapter ch = ClientData.book.chapterOf(q.id);
			if (ch != null) Ui.textR(c, Ui.clip(ch.title, 80), x + w - 8, ry + 6, Ui.MUTED);
			final Quest target = q;
			btn(x + 2, ry, w - 4, rowH, () -> focusQuest(target));
			ry += rowH;
		}
	}

	private void tooltip(DrawContext c, int mx, int my, Quest q) {
		ClientData.Status st = ClientData.status(q);
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(q.title).formatted(Formatting.WHITE, Formatting.BOLD));
		Formatting f = switch (st) {
			case LOCKED -> Formatting.GRAY;
			case AVAILABLE -> Formatting.AQUA;
			case STARTED -> Formatting.GOLD;
			default -> Formatting.GREEN;
		};
		String s = switch (st) {
			case LOCKED -> Lang.t("Bloqueada", "Locked");
			case AVAILABLE -> Lang.t("Disponível", "Available");
			case STARTED -> Lang.t("Em andamento", "In progress") + " (" + Math.round(ClientData.questFraction(q) * 100) + "%)";
			case DONE -> Lang.t("Recompensa pronta!", "Reward ready!");
			case CLAIMED -> Lang.t("Concluída", "Completed");
		};
		lines.add(Text.literal(s).formatted(f));
		if (editing) lines.add(Text.literal("id: " + q.id).formatted(Formatting.DARK_GRAY));
		c.drawTooltip(Ui.tr(), lines, mx, my);
	}

	// ================= entrada =================
	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!modals.isEmpty()) return modals.get(modals.size() - 1).mouseClicked(mx, my, button);
		if (button == 0) {
			for (int i = btns.size() - 1; i >= 0; i--) {
				Btn b = btns.get(i);
				if (Ui.in(mx, my, b.x, b.y, b.w, b.h)) {
					MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					b.action.run();
					return true;
				}
			}
		}
		search.click(mx, my);
		if (search.focused) return true;
		if (Ui.in(mx, my, canX, canY, canW, canH)) {
			pressButton = button;
			pressX = mx;
			pressY = my;
			dragging = false;
			dragQuest = null;
			if (editing && button == 0) {
				Quest hit = nodeAt(mx, my);
				if (hit != null && !hasShiftDown()) {
					dragQuest = hit;
					dragRawX = hit.x;
					dragRawY = hit.y;
				}
			}
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!modals.isEmpty()) return modals.get(modals.size() - 1).mouseDragged(mx, my, button, dx, dy);
		if (pressButton < 0) return false;
		if (!dragging && Math.hypot(mx - pressX, my - pressY) > 3) dragging = true;
		if (dragging) {
			Chapter ch = currentChapter();
			if (ch == null) return true;
			View v = view(ch);
			if (dragQuest != null) {
				dragRawX += dx / (CELL * v.zoom);
				dragRawY += dy / (CELL * v.zoom);
			} else {
				v.ox += dx;
				v.oy += dy;
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (!modals.isEmpty()) return modals.get(modals.size() - 1).mouseReleased(mx, my, button);
		if (pressButton != button) return false;
		if (dragging && dragQuest != null) {
			double nx = snap(dragRawX), ny = snap(dragRawY);
			if (nx != dragQuest.x || ny != dragQuest.y) {
				dragQuest.x = nx;
				dragQuest.y = ny;
				ClientData.moveQuest(dragQuest, nx, ny);
			}
		} else if (!dragging) {
			handleClick(mx, my, button);
		}
		pressButton = -1;
		dragging = false;
		dragQuest = null;
		return true;
	}

	private void handleClick(double mx, double my, int button) {
		Chapter ch = currentChapter();
		if (ch == null) return;
		Quest hit = nodeAt(mx, my);
		if (hit != null) {
			if (button == 0) {
				if (editing && hasShiftDown()) { link(ch, hit); return; }
				MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
				openModal(new DetailModal(this, hit.id));
			} else if (button == 1 && editing) {
				openModal(new EditQuestModal(this, ch.id, hit, false));
			}
			return;
		}
		if (editing && button == 1) {
			View v = view(ch);
			Quest nq = new Quest();
			nq.id = "";
			nq.title = Lang.t("Nova missão", "New quest");
			nq.x = snap((mx - canX - canW / 2.0 - v.ox) / (CELL * v.zoom));
			nq.y = snap((my - canY - canH / 2.0 - v.oy) / (CELL * v.zoom));
			openModal(new EditQuestModal(this, ch.id, nq, true));
		} else if (button == 0) {
			linkFrom = null;
		}
	}

	/** Shift+clique A e depois B: B passa a depender de A (ou remove a dependência). */
	private void link(Chapter ch, Quest hit) {
		if (linkFrom == null) { linkFrom = hit; return; }
		if (linkFrom == hit) { linkFrom = null; return; }
		Quest from = linkFrom;
		linkFrom = null;
		Quest copy = Json.copy(hit, Quest.class);
		if (copy.dependencies.contains(from.id)) {
			copy.dependencies.remove(from.id);
		} else {
			if (ClientData.book.dependsOn(from.id, hit.id)) {
				dev.questbook.client.Toasts.push(Lang.t("Dependência circular", "Circular dependency"),
					Lang.t("Essa ligação criaria um ciclo.", "That link would create a cycle."), "minecraft:barrier", Ui.RED);
				return;
			}
			copy.dependencies.add(from.id);
		}
		Chapter owner = ClientData.book.chapterOf(hit.id);
		ClientData.saveQuest(owner == null ? ch.id : owner.id, copy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		if (!modals.isEmpty()) return modals.get(modals.size() - 1).mouseScrolled(mx, my, hAmount, vAmount);
		if (Ui.in(mx, my, wx + 1, canY, SIDE, canH)) {
			sbScroll = Math.max(0, sbScroll - (int) (vAmount * 20));
			return true;
		}
		Chapter ch = currentChapter();
		if (ch != null && Ui.in(mx, my, canX, canY, canW, canH)) {
			View v = view(ch);
			double nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, v.zoom * (vAmount > 0 ? 1.12 : 1 / 1.12)));
			double k = nz / v.zoom;
			double rx = mx - (canX + canW / 2.0), ry = my - (canY + canH / 2.0);
			v.ox = rx - (rx - v.ox) * k;
			v.oy = ry - (ry - v.oy) * k;
			v.zoom = nz;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (!modals.isEmpty()) return modals.get(modals.size() - 1).keyPressed(key, scan, mods);
		if (search.focused && search.key(key, scan, mods)) return true;
		if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
		if (!search.focused && QuestBookClient.openKey != null && QuestBookClient.openKey.matchesKey(key, scan)) {
			close();
			return true;
		}
		if (key == GLFW.GLFW_KEY_HOME) {
			Chapter ch = currentChapter();
			if (ch != null) centerOn(ch, view(ch));
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean charTyped(char chr, int mods) {
		if (!modals.isEmpty()) return modals.get(modals.size() - 1).charTyped(chr, mods);
		return search.typed(chr);
	}
}
