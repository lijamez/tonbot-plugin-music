package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;

import lombok.Builder;
import lombok.Data;

@Data
class SearchResultsEviction {

	private final SearchResults evictedSearchResults;
	private final EvictionReason reason;

	@Builder
	private SearchResultsEviction(
			SearchResults evictedSearchResults,
			EvictionReason reason) {
		this.evictedSearchResults = Preconditions.checkNotNull(evictedSearchResults,
				"evictedSearchResults must be non-null.");
		this.reason = Preconditions.checkNotNull(reason, "reason must be non-null.");

	}

	public static enum EvictionReason {
		NEW_SEARCH,
		MANUAL_REMOVAL
	}
}
