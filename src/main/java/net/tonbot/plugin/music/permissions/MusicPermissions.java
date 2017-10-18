package net.tonbot.plugin.music.permissions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections.CollectionUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;

/**
 * Manages permissions for a particular guild.
 */
public class MusicPermissions {
	
	private static final Set<Action> DEFAULT_EVERYONE_ACTIONS = ImmutableSet.of(
			Action.PLAY_PAUSE,
			Action.ADD_TRACKS);
	
	private final IGuild guild;
	
	private final Map<Long, Set<Action>> permittedActions;
	
	private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
	
	public MusicPermissions(IGuild guild) {
		this.guild = Preconditions.checkNotNull(guild, "guild must be non-null.");
		this.permittedActions = new HashMap<>();
	}

	/**
	 * Checks whether if the {@link IUser} has the permission to perform the {@code action}.
	 * @param user The {@link IUser} who is performing the action. Non-null.
	 * @param action The {@link Action} being performed. Non-null.
	 * @return True iff the {@code user} can perform the {@code action}.
	 */
	public boolean doesUserHavePermission(IUser user, Action action) {
		Preconditions.checkNotNull(user, "user must be non-null.");
		Preconditions.checkNotNull(action, "action must be non-null.");
		
		// Server managers can do anything.
		if (user.getPermissionsForGuild(guild).contains(Permissions.MANAGE_SERVER)) {
			return true;
		}
		
		List<IRole> roles = user.getRolesForGuild(guild);
		
		rwLock.readLock().lock();
		try {
			for (IRole role : roles) {
				
				Set<Action> permittedActionSet = permittedActions.get(role.getLongID());
				if (CollectionUtils.isEmpty(permittedActionSet)) {
					continue;
				}
				
				if (permittedActionSet.contains(action)) {
					return true;
				}

			}
			
			return false;
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	/**
	 * Adds a rule.
	 * @param rule {@link Rule}. Non-null.
	 */
	public void addRule(Rule rule) {
		Preconditions.checkNotNull(rule, "rule must be non-null.");
		
		rwLock.writeLock().lock();
		try {
			Set<Action> actionSet = permittedActions.computeIfAbsent(rule.getRoleId(), rid -> new HashSet<>());
			actionSet.add(rule.getAction());
		} finally {
			rwLock.writeLock().unlock();
		}
		
	}
	
	/**
	 * Removes a rule.
	 * @param rule {@link Rule}. Non-null.
	 */
	public void removeRule(Rule rule) {
		Preconditions.checkNotNull(rule, "rule must be non-null.");
		
		rwLock.writeLock().lock();
		try {
			Set<Action> actionSet = permittedActions.get(rule.getRoleId());
			
			if (actionSet != null) {
				actionSet.remove(rule.getAction());
			}
		} finally {
			rwLock.writeLock().unlock();
		}
		
	}
	
	/**
	 * Removes all rules for the given role ID.
	 * @param roleId The role ID to remove all rules from.
	 */
	public void removeRulesForRole(long roleId) {
		rwLock.writeLock().lock();
		try {
			permittedActions.remove(roleId);
		} finally {
			rwLock.writeLock().unlock();
		}
	}
	
	/**
	 * Gets the map of role IDs to permitted actions.
	 * @return A map of role IDs to permitted actions.
	 */
	public Map<Long, Set<Action>> getPermissions() {
		return ImmutableMap.copyOf(permittedActions);
	}
	
	/**
	 * Resets all rules back to default.
	 */
	public void resetRules() {
		rwLock.writeLock().lock();
		try {
			permittedActions.clear();
			
			IRole everyoneRole = guild.getEveryoneRole();
			for (Action everyoneAction : DEFAULT_EVERYONE_ACTIONS) {
				Rule rule = new Rule(everyoneRole.getLongID(), everyoneAction);
				this.addRule(rule);
			}
		} finally {
			rwLock.writeLock().unlock();
		}
	}
}
