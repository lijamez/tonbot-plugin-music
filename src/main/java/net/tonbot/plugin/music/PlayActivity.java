package net.tonbot.plugin.music;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.Prefix;
import net.tonbot.common.TonbotBusinessException;
import net.tonbot.plugin.music.SearchResultsEviction.EvictionReason;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IMessage.Attachment;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuilder;

class PlayActivity extends AudioSessionActivity {

	private static final ActivityDescriptor activityDescriptor = ActivityDescriptor.builder()
			.route("music play")
			.parameters(ImmutableList.of("[query]"))
			.description(
					"Plays a track or unpauses the player.")
			.usageDescription("**Playing track(s) via direct link to a track or playlist:**\n"
					+ "```${absoluteReferencedRoute} https://www.youtube.com/watch?v=dQw4w9WgXcQ```\n"
					+ "The following services are supported:\n"
					+ "- YouTube\n"
					+ "- SoundCloud\n"
					+ "- Bandcamp\n"
					+ "- Vimeo\n"
					+ "- Twitch\n"
					+ "- Beam.pro\n"
					+ "- iTunes Playlist Upload\n"
					+ "- Spotify\n"
					+ "- Google Drive\n"
					+ "- HTTP Audio File\n"
					+ "- Discord File Upload\n"
					+ "\n"
					+ "**Playing a track via searching YouTube:**\n"
					+ "```${absoluteReferencedRoute} the sound of silence```\n"
					+ "You'll get a list of search results. Say ``${absoluteReferencedRoute} N`` (where N is the result number) to play it.\n"
					+ "\n"
					+ "**Playing track(s) by iTunes playlist upload:**\n"
					+ "Send your iTunes playlist export as an attachment with the message ``${absoluteReferencedRoute}``. For best results, make sure your tracks' title and artist metadata fields are correct.\n"
					+ "To export an iTunes playlist, click on a playlist, then go to File > Library > Export Playlist.\n"
					+ "\n"
					+ "**Resuming playback:**\n"
					+ "```${absoluteReferencedRoute}```\n"
					+ "Saying the command without any arguments or attachments will unpause playback.")
			.build();

	private final List<PlayActivityHandler> handlerChain = ImmutableList.of(
			this::handlePlayWithoutArgs,
			this::handleSearchResultSelection);

	private final List<PlayActivityHandler> searchHandlerChain = ImmutableList.of(
			this::handleEnqueueIdentifier,
			this::handleTrackSearch);

	private final GuildMusicManager guildMusicManager;
	private final IDiscordClient discordClient;
	private final BotUtils botUtils;
	private final TrackSearcher trackSearcher;

	@Inject
	public PlayActivity(
			@Prefix String prefix,
			IDiscordClient discordClient,
			GuildMusicManager guildMusicManager,
			TrackSearcher trackSearcher,
			BotUtils botUtils) {
		super(guildMusicManager);
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager, "guildMusicManager must be non-null.");
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.trackSearcher = Preconditions.checkNotNull(trackSearcher, "trackSearcher must be non-null.");

		// This is to ensure that search results that are "forgotten" by the track
		// searcher due to new searches will also be deleted from the channel.
		// Evictions due to any other reason should be retained because the PlayActivity
		// will edit the message.
		this.trackSearcher.addSearchResultEvictionListener(esr -> {
			if (esr.getReason() == EvictionReason.NEW_SEARCH
					&& esr.getEvictedSearchResults().getMessage().isPresent()) {
				deleteAsync(esr.getEvictedSearchResults().getMessage().get());
			}

			return null;
		});
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return activityDescriptor;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		MusicPermissions permissions = guildMusicManager.getPermission(event.getGuild().getLongID());

		boolean eventWasHandled = handlerChain.stream()
				.filter(handler -> handler.handle(audioSession, event, args, permissions))
				.findFirst()
				.isPresent();

		if (!eventWasHandled) {
			// The next set of handlers can take more time so we should at least acknowledge
			// the user's message.
			Future<IMessage> ackMessageFuture = RequestBuffer.request(() -> {
				StringBuilder msg = new StringBuilder();
				msg.append("Finding tracks");
				if (!StringUtils.isBlank(args)) {
					msg.append(" for ``").append(args).append("``");
				}
				msg.append("...");
				return event.getChannel().sendMessage(msg.toString());
			});

			try {
				searchHandlerChain.stream()
						.filter(handler -> handler.handle(audioSession, event, args, permissions))
						.findFirst();
			} finally {
				try {
					IMessage ackMessage = ackMessageFuture.get();
					deleteAsync(ackMessage);
				} catch (InterruptedException | ExecutionException | DiscordException | RateLimitException
						| MissingPermissionsException e) {
					// NBD if the ack message failed to send or if the ack message couldn't be
					// deleted.
				}

			}
		}
	}

	private boolean handlePlayWithoutArgs(
			AudioSession audioSession,
			MessageReceivedEvent event,
			String args,
			MusicPermissions permissions) {

		if (StringUtils.isBlank(args) && event.getMessage().getAttachments().isEmpty()) {
			permissions.checkPermission(event.getAuthor(), Action.PLAY_PAUSE);

			audioSession.play();
			return true;
		}

		return false;
	}

	private boolean handleSearchResultSelection(
			AudioSession audioSession,
			MessageReceivedEvent event,
			String args,
			MusicPermissions permissions) {

		IUser user = event.getAuthor();

		SearchResults prevSearchResults = trackSearcher.getPreviousSearchResults(audioSession, user.getLongID())
				.orElse(null);

		if (prevSearchResults != null) {
			List<AudioTrack> hits = prevSearchResults.getHits();
			try {
				int chosenIndex = Integer.parseInt(args.trim()) - 1;

				if (chosenIndex >= 0 && chosenIndex < hits.size()) {
					// The number is in range.
					AudioTrack chosenTrack = hits.get(chosenIndex);
					audioSession.enqueue(chosenTrack, user);
					trackSearcher.removePreviousSearchResults(audioSession, user.getLongID());

					if (prevSearchResults.getMessage().isPresent()) {
						editAsync(
								prevSearchResults.getMessage().get(),
								"Result #" + (chosenIndex + 1) + ": **" + chosenTrack.getInfo().title
										+ "** was queued by **" + user.getDisplayName(event.getGuild()) + "**");
					}

					// Delete the user's message.
					deleteAsync(event.getMessage());

					audioSession.play();

					return true;
				}

				// If the number wasn't in range, assume it's a mistake and just ignore it.
			} catch (IllegalArgumentException e) {
				// The args wasn't even a number. Therefore, it's probably a new search.
			}
		}

		return false;
	}

	private boolean handleEnqueueIdentifier(
			AudioSession audioSession,
			MessageReceivedEvent event,
			String args,
			MusicPermissions permissions) {
		permissions.checkPermission(event.getAuthor(), Action.ADD_TRACKS);

		IUser user = event.getAuthor();
		IChannel channel = event.getChannel();

		AudioLoadResult alr = null;
		// The user might have entered a link to a track.
		if (!StringUtils.isBlank(args)) {
			alr = audioSession.enqueue(args, user);

		} else if (!event.getMessage().getAttachments().isEmpty()) {
			// Maybe the user attached a file.
			Attachment attachment = event.getMessage().getAttachments().get(0);

			alr = audioSession.enqueue(attachment.getUrl(), user);
		}

		if (alr != null) {
			if (alr.getLoadedTracks().isPresent()) {
				List<AudioTrack> loadedTracks = alr.getLoadedTracks().get();

				if (loadedTracks.isEmpty()) {
					// No tracks. Let the next handler deal with it.
					return false;
				} else if (loadedTracks.size() == 1) {
					AudioTrack loadedTrack = loadedTracks.get(0);

					botUtils.sendMessage(channel, "**" + loadedTrack.getInfo().title + "** was queued by **"
							+ user.getDisplayName(channel.getGuild()) + "**");
				} else if (loadedTracks.size() > 1) {
					StringBuffer sb = new StringBuffer();
					sb.append("Added ").append(loadedTracks.size()).append(" tracks from playlist");
					alr.getPlaylistName().ifPresent(pn -> sb.append(" **").append(pn).append("**"));
					sb.append(".");

					botUtils.sendMessage(channel, sb.toString());
				}

				deleteAsync(event.getMessage());
				audioSession.play();

			} else if (alr.getException().isPresent()) {
				botUtils.sendMessage(channel, formatFriendlyException(alr.getException().get()));
			}

			return true;
		}

		return false;
	}

	private boolean handleTrackSearch(
			AudioSession audioSession,
			MessageReceivedEvent event,
			String query,
			MusicPermissions permissions) {
		permissions.checkPermission(event.getAuthor(), Action.ADD_TRACKS);

		// Perform a search
		SearchResults searchResults = trackSearcher.search(audioSession, event.getAuthor().getLongID(), query);
		List<AudioTrack> hits = searchResults.getHits();
		if (hits.isEmpty()) {
			botUtils.sendMessage(event.getChannel(), "No tracks found.");
		} else if (hits.size() == 1) {
			AudioTrack track = hits.get(0);
			audioSession.enqueue(track, event.getAuthor());
			trackSearcher.removePreviousSearchResults(audioSession, event.getAuthor().getLongID());
			botUtils.sendMessage(event.getChannel(), "**" + track.getInfo().title + "** was queued by **"
					+ event.getAuthor().getDisplayName(event.getChannel().getGuild()) + "**");

		} else {
			StringBuffer sb = new StringBuffer();
			sb.append("__Search Results:__\n\n");

			for (int i = 0; i < hits.size(); i++) {
				AudioTrack track = hits.get(i);
				sb.append("``[").append(i + 1)
						.append("]`` ").append(track.getInfo().title)
						.append(" ``(")
						.append(TimeFormatter.toFriendlyString(track.getInfo().length,
								TimeUnit.MILLISECONDS))
						.append(")``\n");
			}

			IMessage messageWithSearchResults = botUtils.sendMessageSync(event.getChannel(), sb.toString());
			searchResults.setMessage(messageWithSearchResults);
		}

		return true;
	}

	private void deleteAsync(IMessage message) {
		new RequestBuilder(discordClient)
				.shouldBufferRequests(true)
				.setAsync(true)
				.doAction(() -> {
					message.delete();
					return true;
				})
				.execute();
	}

	private void editAsync(IMessage message, String newContent) {
		new RequestBuilder(discordClient)
				.shouldBufferRequests(true)
				.setAsync(true)
				.doAction(() -> {
					message.edit(newContent);
					return true;
				})
				.execute();
	}

	private String formatFriendlyException(FriendlyException friendlyException) {
		Throwable cause = friendlyException.getCause();
		if (cause != null && cause instanceof TonbotBusinessException) {
			return cause.getMessage();
		} else {
			return friendlyException.getMessage();
		}
	}

	@FunctionalInterface
	private static interface PlayActivityHandler {

		public abstract boolean handle(
				AudioSession audioSession,
				MessageReceivedEvent event,
				String args,
				MusicPermissions permissions);
	}
}
