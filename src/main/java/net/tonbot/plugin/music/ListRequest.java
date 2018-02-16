package net.tonbot.plugin.music;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.tonbot.common.Param;

@EqualsAndHashCode
@ToString
class ListRequest {

	@Getter
	@Param(name = "page number", ordinal = 0, description = "Page number.")
	private Integer pageNumber;
}
