package net.tonbot.plugin.music.permissions;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
	 * Checks whether if the {@link IUser} has the permission to perform the
	 * {@code action}.
	 * 
	 * @param user
	 *            The {@link IUser} who is performing the action. Non-null.
	 * @param action
	 *            The {@link Action} being performed. Non-null.
	 * @throws PermissionsException
	 *             if the user doesn't have permission.
	 */
	public void checkPermission(IUser user, Action action) {
		Preconditions.checkNotNull(user, "user must be non-null.");
		Preconditions.checkNotNull(action, "action must be non-null.");

		// Administrators can do anything.
		if (user.getPermissionsForGuild(guild).contains(Permissions.ADMINISTRATOR)) {
			return;
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
					return;
				}

			}

			String userName = user.getDisplayName(guild);
			throw new PermissionsException(userName + ", you don't have permission to: " + action.getDescription());
		} finally {
			rwLock.readLock().unlock();
		}
	}

	/**
	 * Adds rules.
	 * 
	 * @param rules
	 *            A collection of {@link Rule}s to add. Non-null.
	 * @return The rules that were actually added.
	 */
	public List<Rule> addAll(Collection<Rule> rules) {
		Preconditions.checkNotNull(rules, "rules must be non-null.");

		ImmutableList.Builder<Rule> addedRules = ImmutableList.builder();

		if (!rules.isEmpty()) {
			rwLock.writeLock().lock();
			try {
				for (Rule rule : rules) {
					Set<Action> actionSet = permittedActions.computeIfAbsent(rule.getRoleId(), rid -> new HashSet<>());

					if (actionSet.add(rule.getAction())) {
						addedRules.add(rule);
					}
				}
			} finally {
				rwLock.writeLock().unlock();
			}
		}

		return addedRules.build();
	}

	/**
	 * Removes rules.
	 * 
	 * @param rules
	 *            A collection of {@link Rule}s to remove. Non-null.
	 * @return The rules that were actually removed.
	 */
	public List<Rule> removeAll(Collection<Rule> rules) {
		Preconditions.checkNotNull(rules, "rules must be non-null.");

		ImmutableList.Builder<Rule> removedRules = ImmutableList.builder();

		if (!rules.isEmpty()) {
			rwLock.writeLock().lock();
			try {
				for (Rule rule : rules) {
					Set<Action> actionSet = permittedActions.get(rule.getRoleId());

					if (actionSet != null && actionSet.remove(rule.getAction())) {
						removedRules.add(rule);
					}
				}

			} finally {
				rwLock.writeLock().unlock();
			}
		}

		return removedRules.build();
	}

	/**
	 * Removes all rules for the given role ID.
	 * 
	 * @param roleId
	 *            The role ID to remove all rules from.
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
	 * 
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
			List<Rule> everyoneRules = DEFAULT_EVERYONE_ACTIONS.stream()
				.map(action -> new Rule(everyoneRole.getLongID(), action))
				.collect(Collectors.toList());
			
			this.addAll(everyoneRules);
		} finally {
			rwLock.writeLock().unlock();
		}
	}
}
