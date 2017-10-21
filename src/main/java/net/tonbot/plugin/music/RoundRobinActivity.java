package net.tonbot.plugin.music;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;

class RoundRobinActivity extends BinaryModeChangingActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music roundrobin")
			.parameters(ImmutableList.of("on/off"))
			.description("Toggles round robin mode.")
			.usageDescription(
					"Round robin is a mode where I will loop through all users who submited tracks "
							+ "and then play one track from their list, guaranteeing that each user gets their "
							+ "fair share of plays.")
			.build();

	@Inject
	public RoundRobinActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager, botUtils);
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected PlayMode onMode() {
		return PlayMode.ROUND_ROBIN;
	}
}
