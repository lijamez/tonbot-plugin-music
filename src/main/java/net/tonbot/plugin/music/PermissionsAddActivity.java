package net.tonbot.plugin.music;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import net.tonbot.plugin.music.permissions.Rule;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.Permissions;

public class PermissionsAddActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music allow")
			.parameters(ImmutableList.of("role", "action"))
			.description(
					"Gives a permission to a role.")
			.build();

	private final IDiscordClient discordClient;
	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;
	private final PermissionsArgsParser permArgsParser;

	@Inject
	public PermissionsAddActivity(
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

		List<Rule> rules;
		try {
			rules = permArgsParser.parseRules(args, event.getGuild());
			if (rules.isEmpty()) {
				botUtils.sendMessage(event.getChannel(),
						"You didn't say any actions. Here are the available actions: "
								+ StringUtils.join(Action.values(), ", "));
				return;
			}
		} catch (IllegalArgumentException e) {
			throw new TonbotBusinessException(e.getMessage());
		}

		MusicPermissions permissions = guildMusicManager.getPermission(event.getGuild().getLongID());
		permissions.addAll(rules);

		IRole role = discordClient.getRoleByID(rules.get(0).getRoleId());
		List<String> addedActionsDesc = rules.stream()
				.map(r -> r.getAction().getDescription())
				.collect(Collectors.toList());

		botUtils.sendMessage(event.getChannel(), role.getName() + " may: " + StringUtils.join(addedActionsDesc, ", "));
	}
}
