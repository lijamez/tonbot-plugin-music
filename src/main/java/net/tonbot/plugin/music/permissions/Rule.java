package net.tonbot.plugin.music.permissions;

import lombok.Data;

@Data
public class Rule {

	private final long roleId;
	private final Action action;
}
