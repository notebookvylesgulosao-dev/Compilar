package dev.questbook.client;

import net.minecraft.client.MinecraftClient;

/** Tradução simples: português (pt_*) ou inglês. */
public final class Lang {
	private Lang() {}

	public static boolean pt() {
		String l = MinecraftClient.getInstance().options.language;
		return l != null && l.toLowerCase().startsWith("pt");
	}

	public static String t(String pt, String en) { return pt() ? pt : en; }
}
