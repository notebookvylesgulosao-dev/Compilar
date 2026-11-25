package dev.questbook.data;

public class Objective {
	public String id = "";
	public ObjectiveType type = ObjectiveType.COLLECT;
	/** Item/entidade/bloco/bioma/estrutura/dimensão (aceita #tag) ou "x,y,z" para LOCATION. */
	public String target = "minecraft:stone";
	/** Quantidade; para LOCATION é o raio. */
	public int count = 1;
	/** LOCATION: dimensão opcional. */
	public String extra = "";

	public int required() {
		return type != null && type.counted() ? Math.max(1, count) : 1;
	}
}
