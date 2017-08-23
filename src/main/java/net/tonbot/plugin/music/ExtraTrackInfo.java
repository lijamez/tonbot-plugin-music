package net.tonbot.plugin.music;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
class ExtraTrackInfo {

	@NonNull
	private final Long addedByUserId;

	@NonNull
	private final Long addTimestamp;
}
