package net.tonbot.plugin.music;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;

@EqualsAndHashCode
@ToString
class SeekRequest {

	@Getter
	@Param(name = "seek time", ordinal = 0, description = "The seek time.", captureRemaining = true)
	String input;
}
