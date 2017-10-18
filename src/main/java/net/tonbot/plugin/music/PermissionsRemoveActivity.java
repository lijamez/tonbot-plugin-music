package net.tonbot.plugin.music;

import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import net.tonbot.plugin.music.permissions.Rule;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.Permissions;

public class PermissionsRemoveActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music disallow")
			.parameters(ImmutableList.of("role", "action"))
			.description(
					"Removes a permission from a role")
			.build();

	private final IDiscordClient discordClient;
	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;
	private final PermissionsArgsParser permArgsParser;

	@Inject
	public PermissionsRemoveActivity(
			IDiscordClient discordClient,
			GuildMusicManager guildMusicManager,
			BotUtils botUtils,
			PermissionsArgsParser permArgsParser) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils,
				"botUtils must be non-null.");
		this.permArgsParser = Preconditions.checkNotNull(permArgsParser, "permArgsParser must be non-null.");

	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		Set<Permissions> userPermissions = event.getAuthor().getPermissionsForGuild(event.getGuild());
		if (!userPermissions.contains(Permissions.ADMINISTRATOR)) {
			throw new TonbotBusinessException("You don't permissions to edit the permissions.");
		}

		Rule rule;
		try {
			rule = permArgsParser.parseRule(args);
		} catch (IllegalArgumentException e) {
			throw new TonbotBusinessException(e.getMessage());
		}

		MusicPermissions permissions = guildMusicManager.getPermission(event.getGuild().getLongID());
		boolean removed = permissions.removeRule(rule);

		if (removed) {
			IRole role = discordClient.getRoleByID(rule.getRoleId());
			botUtils.sendMessage(event.getChannel(),
					role.getName() + " can no longer: " + rule.getAction().getDescription());
		} else {
			botUtils.sendMessage(event.getChannel(), "That rule doesn't exist.");
		}
	}
}
