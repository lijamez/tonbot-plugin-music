package net.tonbot.plugin.music;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;

class ShuffleActivity extends BinaryModeChangingActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "shuffle"))
			.parameters(ImmutableList.of("on/off"))
			.description("Toggles shuffle mode.")
			.build();

	@Inject
	public ShuffleActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager, botUtils);
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected PlayMode onMode() {
		return PlayMode.SHUFFLE;
	}
}
