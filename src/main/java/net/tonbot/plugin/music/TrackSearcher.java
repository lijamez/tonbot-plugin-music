package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Data;
import net.tonbot.common.TonbotTechnicalFault;
import net.tonbot.plugin.music.SearchResultsEviction.EvictionReason;

/**
 * A class that's responsible for performing searches on YouTube and then
 * remembering its results. The search operations are thread safe.
 */
class TrackSearcher {

	private final YoutubeSearchProvider ytSearchProvider;
	private final int maxResults;
	private final Map<SearchResultsKey, SearchResults> searchResultsMap;
	private final List<Function<SearchResultsEviction, Void>> searchResultEvictionListeners;

	@Inject
	public TrackSearcher(
			final YoutubeSearchProvider ytSearchProvider,
			final int maxResults) {
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");

		Preconditions.checkArgument(maxResults > 0, "maxResults must be positive.");
		this.maxResults = maxResults;

		this.searchResultsMap = new ConcurrentHashMap<>();
		this.searchResultEvictionListeners = new ArrayList<>();
	}

	/**
	 * Registers a listener. This listener will be called whenever a
	 * {@link SearchResults} is forgotten by this {@link TrackSearcher}. <br/>
	 * This method is NOT thread-safe.
	 * 
	 * @param listener
	 *            A listener. Non-null.
	 */
	public void addSearchResultEvictionListener(Function<SearchResultsEviction, Void> listener) {
		Preconditions.checkNotNull(listener, "listener must be non-null.");

		searchResultEvictionListeners.add(listener);
	}

	/**
	 * Gets the last remembered {@link SearchResults}.
	 * 
	 * @param audioSession
	 *            {@link AudioSession}. Non-null.
	 * @param userId
	 *            User ID.
	 * @return The last remembered {@link SearchResults}, if any.
	 */
	public Optional<SearchResults> getPreviousSearchResults(AudioSession audioSession, long userId) {
		SearchResultsKey key = new SearchResultsKey(audioSession, userId);
		return Optional.ofNullable(searchResultsMap.get(key));
	}

	/**
	 * Removes the remembered {@link SearchResults}. Listeners will be notified.
	 * Evictions will have the reason {@link EvictionReason#MANUAL_REMOVAL}.
	 * 
	 * @param audioSession
	 *            {@link AudioSession}. Non-null.
	 * @param userId
	 *            User ID.
	 */
	public void removePreviousSearchResults(AudioSession audioSession, long userId) {
		Preconditions.checkNotNull(audioSession, "audioSession must be non-null.");

		SearchResultsKey key = new SearchResultsKey(audioSession, userId);
		removeLoudly(key, EvictionReason.MANUAL_REMOVAL);
	}

	/**
	 * Performs a search. Results will evict the previous {@link SearchResults} with
	 * {@link EvictionReason#NEW_SEARCH}, if any. These new search results will be
	 * automatically remembered, but only if they are not empty.
	 * 
	 * @param query
	 *            The search query. Non-null.
	 * @return {@link SearchResults}. Non-null.
	 */
	public SearchResults search(AudioSession audioSession, long userId, String query) {
		Preconditions.checkNotNull(query, "query must be non-null.");

		AudioItem audioItem = ytSearchProvider.loadSearchResult(query);

		List<AudioTrack> hits;

		if (audioItem == AudioReference.NO_TRACK) {
			hits = ImmutableList.of();
		} else if (audioItem instanceof AudioPlaylist) {
			List<AudioTrack> searchResultTracks = ((AudioPlaylist) audioItem)
					.getTracks()
					.stream()
					.limit(maxResults)
					.collect(Collectors.toList());
			hits = ImmutableList.copyOf(searchResultTracks);
		} else if (audioItem instanceof AudioTrack) {
			hits = ImmutableList.of((AudioTrack) audioItem);
		} else {
			throw new TonbotTechnicalFault("Unknown return value from YoutubeSearchProvider.");
		}

		SearchResults searchResults = new SearchResults(hits);

		SearchResultsKey key = new SearchResultsKey(audioSession, userId);

		if (!hits.isEmpty()) {
			putLoudly(key, searchResults, EvictionReason.NEW_SEARCH);
		} else {
			removeLoudly(key, EvictionReason.NEW_SEARCH);
		}

		return searchResults;
	}

	private void removeLoudly(SearchResultsKey key, EvictionReason reason) {
		SearchResults removedSearchResults = searchResultsMap.remove(key);
		if (removedSearchResults != null) {
			notifyListeners(removedSearchResults, reason);
		}
	}

	private void putLoudly(SearchResultsKey key, SearchResults searchResults, EvictionReason reason) {
		SearchResults removedSearchResults = searchResultsMap.put(key, searchResults);
		if (removedSearchResults != null) {
			notifyListeners(removedSearchResults, reason);
		}
	}

	private void notifyListeners(SearchResults evictedSearchResults, EvictionReason reason) {
		SearchResultsEviction eviction = SearchResultsEviction.builder()
				.reason(reason)
				.evictedSearchResults(evictedSearchResults)
				.build();
		searchResultEvictionListeners.forEach(listener -> listener.apply(eviction));
	}

	@Data
	private static class SearchResultsKey {
		private final AudioSession session;
		private final long userId;
	}
}
