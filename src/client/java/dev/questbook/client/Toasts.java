package dev.questbook.client;

import dev.questbook.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Notificações discretas no canto superior direito. */
public final class Toasts {
	private static final long IN = 250, HOLD = 3500, OUT = 300;
	private static final int MAX_VISIBLE = 3;

	private static final class T {
		final String title, sub;
		final ItemStack icon;
		final int color;
		long start = -1;

		T(String title, String sub, ItemStack icon, int color) {
			this.title = title;
			this.sub = sub;
			this.icon = icon;
			this.color = color;
		}
	}

	private static final Deque<T> QUEUE = new ArrayDeque<>();
	private static final List<T> ACTIVE = new ArrayList<>();

	private Toasts() {}

	public static void push(String title, String sub, String icon, int color) {
		QUEUE.add(new T(title, sub, ClientData.stack(icon), color));
	}

	/** kind: 0 concluída, 1 recompensa, 2 desbloqueada. */
	public static void fromServer(int kind, String title, String icon) {
		switch (kind) {
			case 0 -> push(Lang.t("Missão concluída", "Quest completed"), title, icon, Ui.GREEN);
			case 1 -> push(Lang.t("Recompensa resgatada", "Reward claimed"),
				title.matches("\\d+") ? Lang.t(title + " missões", title + " quests") : title, icon, Ui.GOLD);
			default -> push(Lang.t("Nova missão disponível", "New quest available"), title, icon, Ui.ACCENT);
		}
	}

	public static void clear() {
		QUEUE.clear();
		ACTIVE.clear();
	}

	public static void render(DrawContext c) {
		long now = Util.getMeasuringTimeMs();
		ACTIVE.removeIf(t -> now - t.start > IN + HOLD + OUT);
		while (ACTIVE.size() < MAX_VISIBLE && !QUEUE.isEmpty()) {
			T t = QUEUE.poll();
			t.start = now;
			ACTIVE.add(t);
			MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.5F));
		}
		int sw = c.getScaledWindowWidth();
		int y = 8;
		for (T t : ACTIVE) {
			long age = now - t.start;
			float slide = age < IN ? age / (float) IN : age > IN + HOLD ? 1f - (age - IN - HOLD) / (float) OUT : 1f;
			slide = Math.max(0f, Math.min(1f, slide));
			float ease = 1f - (1f - slide) * (1f - slide);
			int w = Math.max(Ui.width(t.title), Ui.width(Ui.clip(t.sub, 160))) + 40;
			int h = 28;
			int x = sw - 8 - Math.round(w * ease);
			Ui.rect(c, x, y, w, h, 0xE6101216);
			Ui.rect(c, x, y, 2, h, t.color);
			Ui.rect(c, x + 2, y + h - 1, w - 2, 1, Ui.BORDER);
			c.drawItem(t.icon, x + 8, y + 6);
			Ui.text(c, t.title, x + 30, y + 5, t.color);
			Ui.text(c, Ui.clip(t.sub, 160), x + 30, y + 16, Ui.MUTED);
			y += h + 4;
		}
	}
}
