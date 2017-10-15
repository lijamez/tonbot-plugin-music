package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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

/**
 * A class that's responsible for performing searches on YouTube and then
 * remembering its results. The search operations are thread safe.
 */
class TrackSearcher {

	private final YoutubeSearchProvider ytSearchProvider;
	private final Map<SearchResultsKey, SearchResults> searchResultsMap;
	private final List<Function<SearchResults, Void>> searchResultEvictionListeners;

	@Inject
	public TrackSearcher(YoutubeSearchProvider ytSearchProvider) {
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");
		this.searchResultsMap = new ConcurrentHashMap<>();
		this.searchResultEvictionListeners = new ArrayList<>();
	}

	/**
	 * Registers a listener. This listener will be called whenever a
	 * {@link SearchResult} is forgotten by this {@link TrackSearcher}. <br/>
	 * This method is NOT thread-safe.
	 * 
	 * @param listener
	 *            A listener. Non-null.
	 */
	public void addSearchResultEvictionListener(Function<SearchResults, Void> listener) {
		Preconditions.checkNotNull(listener, "listener must be non-null.");

		searchResultEvictionListeners.add(listener);
	}

	/**
	 * Gets the last remembered {@link SearchResult}.
	 * 
	 * @param audioSession
	 *            {@link AudioSession}. Non-null.
	 * @param userId
	 *            User ID.
	 * @return The last remembered {@link SearchResult}, if any.
	 */
	public Optional<SearchResults> getPreviousSearchResults(AudioSession audioSession, long userId) {
		SearchResultsKey key = new SearchResultsKey(audioSession, userId);
		return Optional.ofNullable(searchResultsMap.get(key));
	}

	/**
	 * Removes the remembered {@link SearchResult}. Listeners will be notified.
	 * 
	 * @param audioSession
	 *            {@link AudioSession}. Non-null.
	 * @param userId
	 *            User ID.
	 */
	public void removePreviousSearchResults(AudioSession audioSession, long userId) {
		Preconditions.checkNotNull(audioSession, "audioSession must be non-null.");

		SearchResultsKey key = new SearchResultsKey(audioSession, userId);
		removeLoudly(key);
	}

	/**
	 * Forgets the previous set of search results, if any, (which will inform
	 * listeners) and then performs a search. These new search results will be
	 * automatically remembered, but only if they are not empty.
	 * 
	 * @param query
	 *            The search query. Non-null.
	 * @return {@link SearchResults}. Non-null.
	 */
	public SearchResults search(AudioSession audioSession, long userId, String query) {
		Preconditions.checkNotNull(query, "query must be non-null.");

		SearchResultsKey key = new SearchResultsKey(audioSession, userId);
		removeLoudly(key);

		AudioItem audioItem = ytSearchProvider.loadSearchResult(query);

		List<AudioTrack> hits;

		if (audioItem == AudioReference.NO_TRACK) {
			hits = ImmutableList.of();
		} else if (audioItem instanceof AudioPlaylist) {
			List<AudioTrack> searchResultTracks = ((AudioPlaylist) audioItem).getTracks();

			hits = ImmutableList.copyOf(searchResultTracks);
		} else if (audioItem instanceof AudioTrack) {
			// Found an exact match. Queue it.
			// audioSession.enqueue((AudioTrack) audioItem, event.getAuthor());

			hits = ImmutableList.of((AudioTrack) audioItem);
		} else {
			throw new TonbotTechnicalFault("Unknown return value from YoutubeSearchProvider.");
		}

		SearchResults searchResults = new SearchResults(hits);

		if (!hits.isEmpty()) {
			putLoudly(key, searchResults);
		}

		return searchResults;
	}

	private void removeLoudly(SearchResultsKey key) {
		SearchResults removedSearchResults = searchResultsMap.remove(key);
		if (removedSearchResults != null) {
			searchResultEvictionListeners.forEach(listener -> listener.apply(removedSearchResults));
		}
	}

	private void putLoudly(SearchResultsKey key, SearchResults searchResults) {
		SearchResults removedSearchResults = searchResultsMap.put(key, searchResults);
		if (removedSearchResults != null) {
			searchResultEvictionListeners.forEach(listener -> listener.apply(removedSearchResults));
		}
	}

	@Data
	private static class SearchResultsKey {
		private final AudioSession session;
		private final long userId;
	}
}
