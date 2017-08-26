package net.tonbot.plugin.music;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.RequestBuilder;

class PlayActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "play"))
			.parameters(ImmutableList.of("link to song"))
			.description(
					"Plays the song provided by the link. If no song link is provided, then it unpauses the player.")
			.build();

	private final IDiscordClient discordClient;
	private final BotUtils botUtils;

	@Inject
	public PlayActivity(
			IDiscordClient discordClient,
			DiscordAudioPlayerManager discordAudioPlayerManager,
			BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {

		IGuild guild = event.getGuild();
		IUser user = event.getAuthor();

		// 1) The user maybe entered a number in response to a search query.
		Optional<SearchResults> optPrevSearchResults = audioSession.getSearchResults(event.getAuthor());

		if (optPrevSearchResults.isPresent()) {
			SearchResults prevSearchResults = optPrevSearchResults.get();
			AudioPlaylist prevSearchPlaylist = prevSearchResults.getAudioPlaylist();
			try {
				int chosenIndex = Integer.parseInt(args.trim()) - 1;

				if (chosenIndex >= 0 && chosenIndex < prevSearchPlaylist.getTracks().size()) {
					// The number is in range.
					AudioTrack chosenTrack = prevSearchPlaylist.getTracks().get(chosenIndex);
					audioSession.enqueue(chosenTrack, user);
					audioSession.clearSearchResult(user);
					audioSession.play();

					botUtils.sendMessage(event.getChannel(),
							"Selected result #" + (chosenIndex + 1) + ": **" + chosenTrack.getInfo().title + "**");

					// Delete the previous search results message to reduce pollution.
					delete(prevSearchResults.getMessage());
				}

				// If the number wasn't in range, assume it's a mistake and just ignore it.
				return;
			} catch (IllegalArgumentException e) {
				// The args wasn't a number. Therefore, it's probably a new search.
			}
		}

		// 2) The user might have entered a link to a track.
		if (!StringUtils.isBlank(args)) {
			audioSession.enqueue(args, guild, user);
			event.getMessage().delete();
		}

		audioSession.play();
	}

	private void delete(IMessage message) {
		new RequestBuilder(discordClient)
				.shouldBufferRequests(true)
				.setAsync(true)
				.doAction(() -> {
					message.delete();
					return true;
				})
				.execute();
	}
}
