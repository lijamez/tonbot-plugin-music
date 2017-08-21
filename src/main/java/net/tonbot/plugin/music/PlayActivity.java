package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;

public class PlayActivity implements Activity {
	
	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "play"))
			.description("Plays something.")
			.build();

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	
	@Inject
	public PlayActivity(DiscordAudioPlayerManager discordAudioPlayerManager) {
		this.discordAudioPlayerManager = Preconditions.checkNotNull(discordAudioPlayerManager, "discordAudioPlayerManager must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		IGuild guild = event.getGuild();
		
		discordAudioPlayerManager.createPlayerFor(guild); //TODO maybe don't call this all the time
		discordAudioPlayerManager.enqueue(args, event.getChannel());
	}
}
