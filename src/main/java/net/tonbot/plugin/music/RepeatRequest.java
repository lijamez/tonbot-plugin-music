package net.tonbot.plugin.music;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;

@EqualsAndHashCode
@ToString
class RepeatRequest {

	@Getter
	@Param(name = "repeat mode", ordinal = 0, description = "The repeat mode.")
	RepeatMode repeatMode;
}
