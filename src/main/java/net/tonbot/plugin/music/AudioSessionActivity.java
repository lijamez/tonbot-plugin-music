package net.tonbot.plugin.music;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.tonbot.common.Activity;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;

abstract class AudioSessionActivity implements Activity {

	private static final Logger LOG = LoggerFactory.getLogger(AudioSessionActivity.class);

	private final GuildMusicManager guildMusicManager;

	public AudioSessionActivity(GuildMusicManager guildMusicManager) {
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager, "guildMusicManager must be non-null.");
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {

		final IGuild guild = event.getGuild();
		AudioSession audioSession = guildMusicManager.getAudioSession(guild.getLongID()).orElse(null);
		if (audioSession == null) {
			// Ignore it because there's no session.

			LOG.debug("The music command '{}' was ignored because there's no AudioSession for the guild '{}'.",
					event.getMessage().getContent(), guild.getName());

			return;
		}

		if (event.getChannel().getLongID() != audioSession.getDefaultChannelId()) {
			// Message was sent to a channel other than the session's default.
			return;
		}

		enactWithSession(event, args, audioSession);
	}

	protected abstract void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession);
}
