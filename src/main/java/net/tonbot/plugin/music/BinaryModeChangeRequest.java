package net.tonbot.plugin.music;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;

@EqualsAndHashCode
@ToString
class BinaryModeChangeRequest {

	@Getter
	@Param(name = "on/off", ordinal = 0, description = "On or Off")
	private ToggleStatus status;
}
