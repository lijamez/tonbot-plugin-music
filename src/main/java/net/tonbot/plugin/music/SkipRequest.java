package net.tonbot.plugin.music;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;

@EqualsAndHashCode
@ToString
class SkipRequest {

	@Getter
	@Param(name = "track numbers", ordinal = 0, description = "The numbers of the tracks to skip. May be comma separated and ranges are supported. May also be \"mine\" to only skip your tracks or \"all\" to skip all tracks.", captureRemaining = true)
	String input;
}
