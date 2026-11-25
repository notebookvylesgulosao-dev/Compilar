package dev.questbook.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/** Primitivas de desenho para a interface escura e minimalista. */
public final class Ui {
	public static final int BG = 0xFF0D0E12, CANVAS = 0xFF101216, PANEL = 0xFF15171C, PANEL2 = 0xFF1B1E25,
		PANEL3 = 0xFF252935, BORDER = 0xFF2C313C, TEXT = 0xFFE8EAF0, MUTED = 0xFF8A91A3,
		ACCENT = 0xFF5B8CFF, GREEN = 0xFF3DDC84, GOLD = 0xFFFFC857, RED = 0xFFFF6464;

	private Ui() {}

	public static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

	public static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static void rect(DrawContext c, int x, int y, int w, int h, int col) { c.fill(x, y, x + w, y + h, col); }

	public static void frame(DrawContext c, int x, int y, int w, int h, int fill, int border) {
		c.fill(x, y, x + w, y + h, border);
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	public static int text(DrawContext c, String s, int x, int y, int col) { return c.drawText(tr(), s, x, y, col, false); }

	public static void textC(DrawContext c, String s, int cx, int y, int col) {
		c.drawText(tr(), s, cx - tr().getWidth(s) / 2, y, col, false);
	}

	public static void textR(DrawContext c, String s, int rx, int y, int col) {
		c.drawText(tr(), s, rx - tr().getWidth(s), y, col, false);
	}

	public static int width(String s) { return tr().getWidth(s); }

	public static String clip(String s, int w) {
		if (s == null) return "";
		if (tr().getWidth(s) <= w) return s;
		return tr().trimToWidth(s, Math.max(0, w - tr().getWidth("..."))) + "...";
	}

	public static int wrapHeight(String s, int w) {
		return Math.max(1, tr().wrapLines(StringVisitable.plain(s), w).size()) * 10;
	}

	public static int wrap(DrawContext c, String s, int x, int y, int w, int col) {
		List<OrderedText> lines = tr().wrapLines(StringVisitable.plain(s), w);
		int yy = y;
		for (OrderedText l : lines) {
			c.drawText(tr(), l, x, yy, col, false);
			yy += 10;
		}
		return lines.size() * 10;
	}

	public static int alpha(int col, float a) {
		int al = Math.max(0, Math.min(255, (int) (((col >>> 24) & 0xFF) * a)));
		return (al << 24) | (col & 0xFFFFFF);
	}

	/** Linha com espessura arbitrária, rotacionada. */
	public static void line(DrawContext c, float x1, float y1, float x2, float y2, float thickness, int col) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5f) return;
		int t = Math.max(1, Math.round(thickness));
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(x1, y1, 0f);
		m.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
		c.fill(0, -t / 2, Math.round(len), t - t / 2, col);
		m.pop();
	}

	public static void bar(DrawContext c, int x, int y, int w, int h, float frac, int col) {
		rect(c, x, y, w, h, PANEL3);
		int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
		if (fw > 0) rect(c, x, y, fw, h, col);
	}

	public static final int BTN_PRIMARY = 1, BTN_DANGER = 2, BTN_SUCCESS = 3, BTN_GHOST = 4;

	public static void drawButton(DrawContext c, int x, int y, int w, int h, String label, int style, boolean enabled, boolean hover) {
		int bg, border, fg = enabled ? TEXT : 0xFF5A6070;
		switch (style) {
			case BTN_PRIMARY -> { bg = hover ? 0xFF4A78E0 : 0xFF3B66CC; border = 0xFF5B8CFF; }
			case BTN_DANGER -> { bg = hover ? 0xFF8A3030 : 0xFF6B2626; border = 0xFFB04444; }
			case BTN_SUCCESS -> { bg = hover ? 0xFF2F9E62 : 0xFF258550; border = 0xFF3DDC84; }
			case BTN_GHOST -> { bg = hover ? PANEL3 : PANEL2; border = PANEL2; }
			default -> { bg = hover ? PANEL3 : PANEL2; border = hover ? ACCENT : BORDER; }
		}
		if (!enabled) { bg = PANEL; border = PANEL2; }
		frame(c, x, y, w, h, bg, border);
		String l = clip(label, w - 6);
		text(c, l, x + (w - width(l)) / 2, y + (h - 8) / 2, fg);
	}
}
