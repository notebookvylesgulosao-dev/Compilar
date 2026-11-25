package dev.questbook.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Campo de texto simples (uma linha) com cursor, colar/copiar e rolagem horizontal. */
public class TextField {
	public String text = "";
	public String hint = "";
	public int maxLen = 64;
	public boolean numeric;
	public boolean focused;
	public Consumer<String> onChange;
	public Runnable onEnter;

	private int cursor;
	private int scroll;
	private boolean all;
	private int x, y, w, h;

	public TextField() {}

	public TextField(String text, int maxLen) {
		this.maxLen = maxLen;
		setText(text);
	}

	public void setText(String s) {
		text = s == null ? "" : s;
		if (text.length() > maxLen) text = text.substring(0, maxLen);
		cursor = text.length();
		scroll = 0;
		all = false;
	}

	public void render(DrawContext c, int x, int y, int w, int h, int mx, int my) {
		this.x = x; this.y = y; this.w = w; this.h = h;
		boolean hover = Ui.in(mx, my, x, y, w, h);
		Ui.frame(c, x, y, w, h, Ui.PANEL, focused ? Ui.ACCENT : (hover ? Ui.PANEL3 : Ui.BORDER));
		int avail = w - 8;
		if (cursor < scroll) scroll = cursor;
		while (scroll < cursor && Ui.width(text.substring(scroll, cursor)) > avail) scroll++;
		String vis = Ui.tr().trimToWidth(text.substring(Math.min(scroll, text.length())), avail);
		int ty = y + (h - 8) / 2;
		if (text.isEmpty() && !focused) {
			Ui.text(c, Ui.clip(hint, avail), x + 4, ty, 0xFF565C6B);
		} else {
			if (all && focused && !text.isEmpty()) Ui.rect(c, x + 3, y + 2, Ui.width(vis) + 2, h - 4, 0xFF2D4A8A);
			Ui.text(c, vis, x + 4, ty, Ui.TEXT);
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0 && !all) {
				int cx = x + 4 + Ui.width(text.substring(Math.min(scroll, cursor), cursor));
				Ui.rect(c, cx, y + 2, 1, h - 4, Ui.TEXT);
			}
		}
	}

	public boolean click(double mx, double my) {
		boolean in = Ui.in(mx, my, x, y, w, h);
		focused = in;
		all = false;
		if (in) {
			int rel = (int) mx - (x + 4);
			int i = Math.min(scroll, text.length());
			while (i < text.length() && Ui.width(text.substring(scroll, i + 1)) <= rel) i++;
			cursor = i;
		}
		return in;
	}

	private void changed() {
		if (onChange != null) onChange.accept(text);
	}

	private void insert(String s) {
		StringBuilder f = new StringBuilder();
		for (char ch : s.toCharArray()) if (accept(ch)) f.append(ch);
		if (f.length() == 0) return;
		if (all) { text = ""; cursor = 0; all = false; }
		int room = maxLen - text.length();
		if (room <= 0) return;
		String add = f.length() > room ? f.substring(0, room) : f.toString();
		text = text.substring(0, cursor) + add + text.substring(cursor);
		cursor += add.length();
		changed();
	}

	private boolean accept(char ch) {
		if (ch < 32 || ch == 127) return false;
		if (numeric) return (ch >= '0' && ch <= '9') || ch == '-' || ch == '.';
		return true;
	}

	public boolean typed(char ch) {
		if (!focused) return false;
		insert(String.valueOf(ch));
		return true;
	}

	/** @return true se a tecla foi consumida. */
	public boolean key(int key, int scan, int mods) {
		if (!focused) return false;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (Screen.hasControlDown()) {
			if (key == GLFW.GLFW_KEY_A) { all = true; return true; }
			if (key == GLFW.GLFW_KEY_C) { mc.keyboard.setClipboard(text); return true; }
			if (key == GLFW.GLFW_KEY_X) { mc.keyboard.setClipboard(text); text = ""; cursor = 0; all = false; changed(); return true; }
			if (key == GLFW.GLFW_KEY_V) { insert(mc.keyboard.getClipboard()); return true; }
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (all) { text = ""; cursor = 0; all = false; changed(); }
				else if (cursor > 0) { text = text.substring(0, cursor - 1) + text.substring(cursor); cursor--; changed(); }
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (all) { text = ""; cursor = 0; all = false; changed(); }
				else if (cursor < text.length()) { text = text.substring(0, cursor) + text.substring(cursor + 1); changed(); }
			}
			case GLFW.GLFW_KEY_LEFT -> { all = false; cursor = Math.max(0, cursor - 1); }
			case GLFW.GLFW_KEY_RIGHT -> { all = false; cursor = Math.min(text.length(), cursor + 1); }
			case GLFW.GLFW_KEY_HOME -> { all = false; cursor = 0; }
			case GLFW.GLFW_KEY_END -> { all = false; cursor = text.length(); }
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { if (onEnter != null) onEnter.run(); }
			case GLFW.GLFW_KEY_ESCAPE -> { focused = false; }
			default -> { return false; }
		}
		return true;
	}

	public double asDouble(double fallback) {
		try { return Double.parseDouble(text.trim()); } catch (Exception e) { return fallback; }
	}

	public int asInt(int fallback) {
		try { return (int) Math.round(Double.parseDouble(text.trim())); } catch (Exception e) { return fallback; }
	}
}
