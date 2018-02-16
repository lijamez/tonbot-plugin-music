package net.tonbot.plugin.music;

import javax.annotation.Nonnull;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;
import net.tonbot.plugin.music.permissions.Action;
import sx.blah.discord.handle.obj.IRole;

@EqualsAndHashCode
@ToString
class PermissionsAddRequest {

	@Getter
	@Param(name = "role", ordinal = 0, description = "A role.")
	@Nonnull
	private IRole role;

	@Getter
	@Param(name = "action", ordinal = 1, description = "An action.")
	@Nonnull
	private Action action;
}
