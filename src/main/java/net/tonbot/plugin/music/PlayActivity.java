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
import net.tonbot.common.Prefix;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IMessage.Attachment;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.RequestBuilder;

class PlayActivity extends AudioSessionActivity {

	private static final ActivityDescriptor activityDescriptor = ActivityDescriptor.builder()
			.route("music play")
			.parameters(ImmutableList.of("link to song"))
			.description(
					"Plays a track. If no song link is provided, then it unpauses the player.")
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

	private final IDiscordClient discordClient;
	private final BotUtils botUtils;

	@Inject
	public PlayActivity(
			@Prefix String prefix,
			IDiscordClient discordClient,
			DiscordAudioPlayerManager discordAudioPlayerManager,
			BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return activityDescriptor;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
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
					delete(event.getMessage());
				}

				// If the number wasn't in range, assume it's a mistake and just ignore it.
				return;
			} catch (IllegalArgumentException e) {
				// The args wasn't a number. Therefore, it's probably a new search.
			}
		}

		// 2) The user might have entered a link to a track.
		if (!StringUtils.isBlank(args)) {
			audioSession.enqueue(args, user);
			
		} else if (!event.getMessage().getAttachments().isEmpty()) {
			// 3) Maybe the user attached a file.
			Attachment attachment = event.getMessage().getAttachments().get(0);

			audioSession.enqueue(attachment.getUrl(), user);
		}
		delete(event.getMessage());

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
