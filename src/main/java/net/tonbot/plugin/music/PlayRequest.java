package net.tonbot.plugin.music;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;

@EqualsAndHashCode
@ToString
class PlayRequest {

	@Getter
	@Param(name = "query", ordinal = 0, description = "A query.", captureRemaining = true)
	String query;
}
