package dev.questbook.data;

public enum ObjectiveType {
	COLLECT(Kind.ITEM), CRAFT(Kind.ITEM), SUBMIT(Kind.ITEM), KILL(Kind.ENTITY),
	BIOME(Kind.BIOME), STRUCTURE(Kind.STRUCTURE), DIMENSION(Kind.DIMENSION), LOCATION(Kind.NONE),
	INTERACT_BLOCK(Kind.BLOCK), INTERACT_ENTITY(Kind.ENTITY);

	public enum Kind { ITEM, ENTITY, BLOCK, BIOME, STRUCTURE, DIMENSION, NONE }

	public final Kind kind;

	ObjectiveType(Kind kind) { this.kind = kind; }

	/** Tipos com contagem; os demais são "feito / não feito". */
	public boolean counted() {
		return this == COLLECT || this == CRAFT || this == SUBMIT || this == KILL
			|| this == INTERACT_BLOCK || this == INTERACT_ENTITY;
	}
}
