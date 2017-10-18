package net.tonbot.plugin.music;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.EmbedBuilder;

public class PermissionsListActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music permissions")
			.description(
					"Lists the music player-specific permissions.")
			.build();

	private final IDiscordClient discordClient;
	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;

	@Inject
	public PermissionsListActivity(
			IDiscordClient discordClient,
			GuildMusicManager guildMusicManager,
			BotUtils botUtils) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils,
				"botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {

		MusicPermissions permissions = guildMusicManager.getPermission(event.getGuild().getLongID());

		EmbedBuilder eb = new EmbedBuilder();
		eb.withTitle("Music Player Permissions");
		
		Map<Long, Set<Action>> actionsPerRole = permissions.getPermissions();

		for (Entry<Long, Set<Action>> actionsForRole : actionsPerRole.entrySet()) {
			long roleId = actionsForRole.getKey();
			Set<Action> allowableActions = actionsForRole.getValue();

			IRole role = discordClient.getRoleByID(roleId);
			List<String> friendlyAllowableActions = allowableActions.stream()
					.map(action -> action.getDescription())
					.collect(Collectors.toList());
			
			eb.appendField(role.getName(), StringUtils.join(friendlyAllowableActions, "\n"), false);
		}
		
		eb.withFooterText("Those who have the 'Manage Server' permission can use any music command.");
		
		botUtils.sendEmbed(event.getChannel(), eb.build());
	}
}
