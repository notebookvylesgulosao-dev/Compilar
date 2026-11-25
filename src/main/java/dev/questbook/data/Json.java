package dev.questbook.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public final class Json {
	public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	public static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	public static final Type STATE_MAP = new TypeToken<Map<String, QuestState>>() {}.getType();

	private Json() {}

	public static <T> T parse(String s, Class<T> c) {
		try { return GSON.fromJson(s, c); } catch (Exception e) { return null; }
	}

	public static <T> T copy(T o, Class<T> c) { return GSON.fromJson(GSON.toJson(o), c); }
}
