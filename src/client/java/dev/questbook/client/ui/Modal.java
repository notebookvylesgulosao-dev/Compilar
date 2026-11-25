package dev.questbook.client.ui;

import dev.questbook.client.screen.QuestBookScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Janela modal desenhada sobre o Quest Book. Botões e campos são registrados a cada quadro. */
public abstract class Modal {
	public static final int PRIMARY = 1, DANGER = 2, SUCCESS = 3, GHOST = 4;

	private record Btn(int x, int y, int w, int h, Runnable action) {}

	protected final QuestBookScreen screen;
	private final List<Btn> btns = new ArrayList<>();
	private final List<TextField> fields = new ArrayList<>();
	protected int mx, my;
	protected int sw, sh;

	protected Modal(QuestBookScreen screen) { this.screen = screen; }

	/** Desenha o modal. {@code mx,my} são -9999 quando outro modal está por cima. */
	public abstract void render(DrawContext c, int mx, int my);

	protected void begin(int mx, int my) {
		btns.clear();
		fields.clear();
		this.mx = mx;
		this.my = my;
		this.sw = screen.width;
		this.sh = screen.height;
	}

	protected boolean dimBackground() { return true; }

	public void close() { screen.closeModal(this); }

	// ---------- widgets ----------
	protected boolean button(DrawContext c, int x, int y, int w, int h, String label, int style, boolean enabled, Runnable action) {
		boolean hover = enabled && Ui.in(mx, my, x, y, w, h);
		Ui.drawButton(c, x, y, w, h, label, style, enabled, hover);
		if (enabled) btns.add(new Btn(x, y, w, h, action));
		return hover;
	}

	/** Região clicável sem desenho. */
	protected void hit(int x, int y, int w, int h, Runnable action) { btns.add(new Btn(x, y, w, h, action)); }

	protected void field(DrawContext c, TextField f, int x, int y, int w, int h) {
		f.render(c, x, y, w, h, mx, my);
		fields.add(f);
	}

	/** Moldura da janela com título e botão X. */
	protected void window(DrawContext c, int x, int y, int w, int h, String title) {
		if (dimBackground()) Ui.rect(c, 0, 0, sw, sh, 0x99000000);
		Ui.rect(c, x - 2, y - 2, w + 4, h + 4, 0x55000000);
		Ui.frame(c, x, y, w, h, Ui.PANEL, Ui.BORDER);
		Ui.rect(c, x + 1, y + 1, w - 2, 24, Ui.PANEL2);
		Ui.rect(c, x + 1, y + 25, w - 2, 1, Ui.BORDER);
		Ui.text(c, Ui.clip(title, w - 50), x + 10, y + 9, Ui.TEXT);
		closeButton(c, x + w - 22, y + 5);
	}

	protected void closeButton(DrawContext c, int x, int y) {
		boolean hover = Ui.in(mx, my, x, y, 16, 16);
		Ui.rect(c, x, y, 16, 16, hover ? 0xFF8A3030 : Ui.PANEL3);
		Ui.line(c, x + 4, y + 4, x + 12, y + 12, 1, Ui.TEXT);
		Ui.line(c, x + 12, y + 4, x + 4, y + 12, 1, Ui.TEXT);
		hit(x, y, 16, 16, this::close);
	}

	// ---------- entrada ----------
	public boolean mouseClicked(double x, double y, int button) {
		if (button == 0) {
			for (int i = btns.size() - 1; i >= 0; i--) {
				Btn b = btns.get(i);
				if (Ui.in(x, y, b.x, b.y, b.w, b.h)) {
					MinecraftClient.getInstance().getSoundManager()
						.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					b.action.run();
					return true;
				}
			}
		}
		for (TextField f : fields) f.click(x, y);
		return true;
	}

	public boolean mouseReleased(double x, double y, int button) { return true; }

	public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return true; }

	public boolean mouseScrolled(double x, double y, double h, double v) { return true; }

	public boolean charTyped(char ch, int mods) {
		for (TextField f : fields) if (f.typed(ch)) return true;
		return true;
	}

	public boolean keyPressed(int key, int scan, int mods) {
		for (TextField f : fields) if (f.focused) { if (f.key(key, scan, mods)) return true; }
		if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
		return true;
	}
}
