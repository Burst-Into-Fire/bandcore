package com.burstintofire.bandcore.finance.category;

/**
 * Categories every band starts with. The web app shows them through i18n keys
 * ({@code expenseCategories.defaults.<KEY>}) until an admin renames them; the stored name is the
 * pt-BR one, so names stay unique and readable outside the web app (exports, audit log).
 */
public enum DefaultCategory {

	REHEARSAL_SPACE("Estúdio de ensaio"),

	GEAR("Equipamento"),

	VIDEO("Vídeo"),

	PHOTOGRAPHY("Fotografia"),

	TRAVEL("Viagem"),

	MERCH("Merch"),

	OTHER("Outros");

	private final String defaultName;

	DefaultCategory(String defaultName) {
		this.defaultName = defaultName;
	}

	public String defaultName() {
		return defaultName;
	}

}
