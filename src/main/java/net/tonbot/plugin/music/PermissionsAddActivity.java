package net.tonbot.plugin.music;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import net.tonbot.plugin.music.permissions.Rule;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.MessageTokenizer;
import sx.blah.discord.util.MessageTokenizer.RoleMentionToken;
import sx.blah.discord.util.MessageTokenizer.Token;

public class PermissionsAddActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music permissions add")
			.description(
					"Gives a permission to a role.")
			.build();

	private final IDiscordClient discordClient;
	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;

	@Inject
	public PermissionsAddActivity(
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
		
		if (StringUtils.isBlank(args)) {
			botUtils.sendMessage(event.getChannel(), "No arguments found.");
			return;
		}
		
		MessageTokenizer tokenizer = new MessageTokenizer(discordClient, args);
		
		IRole mentionedRole = null;
		
		if (tokenizer.hasNextMention()) {
			Token mentionToken = tokenizer.nextMention();
			if (mentionToken instanceof RoleMentionToken) {
				mentionedRole = ((RoleMentionToken) mentionToken).getMentionObject();
			}
		}
		
		if (mentionedRole == null) {
			botUtils.sendMessage(event.getChannel(), "No mention found.");
			return;
		}
		
		if (!tokenizer.hasNextWord()) {
			botUtils.sendMessage(event.getChannel(), "No action found.");
			return;
		}
		
		Token wordToken = tokenizer.nextWord();
		Action action;
		try {
			action = Action.valueOf(wordToken.getContent());
		} catch (IllegalArgumentException e) {
			botUtils.sendMessage(event.getChannel(), "Invalid action. Must be one of: " + StringUtils.join(Action.values(), ", "));
			return;
		}
		
		Rule rule = new Rule(mentionedRole.getLongID(), action);
		
		permissions.addRule(rule);
		
		botUtils.sendMessage(event.getChannel(), "Rule successfully added: " + rule);

	}
}
