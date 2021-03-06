package me.unrealization.jeeves.modules;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.Permissions;
import me.unrealization.jeeves.bot.Jeeves;
import me.unrealization.jeeves.bot.MessageQueue;
import me.unrealization.jeeves.bot.RoleQueue;
import me.unrealization.jeeves.interfaces.BotCommand;
import me.unrealization.jeeves.interfaces.BotModule;
import me.unrealization.jeeves.interfaces.UserJoinedHandler;

public class Roles extends BotModule implements UserJoinedHandler
{
	public Roles()
	{
		this.version = "1.1.0";

		this.commandList = new String[13];
		this.commandList[0] = "GetRoles";
		this.commandList[1] = "Join";
		this.commandList[2] = "Leave";
		this.commandList[3] = "Members";
		this.commandList[4] = "MissingRole";
		this.commandList[5] = "GetUntaggedUsers";
		this.commandList[6] = "GetAutoRole";
		this.commandList[7] = "SetAutoRole";
		this.commandList[8] = "LockRole";
		this.commandList[9] = "UnlockRole";
		this.commandList[10] = "GetLockedRoles";
		this.commandList[11] = "AssignRole";
		this.commandList[12] = "UnassignRole";

		this.defaultConfig.put("autoRole", "");
		this.defaultConfig.put("lockedRoles", new ArrayList<String>());
	}

	@Override
	public void userJoinedHandler(UserJoinEvent event)
	{
		String roleIdString = (String)Jeeves.serverConfig.getValue(event.getGuild().getLongID(), "autoRole");

		if (roleIdString.isEmpty() == true)
		{
			return;
		}

		long roleId = Long.parseLong(roleIdString);
		IRole role = event.getGuild().getRoleByID(roleId);

		if (role == null)
		{
			Roles roles = new Roles();
			Jeeves.serverConfig.setValue(event.getGuild().getLongID(), "autoRole", roles.getDefaultConfig().get("autoRole"));
			return;
		}

		RoleQueue.addRoleToUser(role, event.getUser());
	}

	private static List<IRole> getManageableRoles(IGuild server) throws Exception
	{
		List<IRole> botRoles = Jeeves.bot.getOurUser().getRolesForGuild(server);

		int rolePosition = -1;

		for (int roleIndex = 0; roleIndex < botRoles.size(); roleIndex++)
		{
			IRole role = botRoles.get(roleIndex);

			if ((role.getPermissions().contains(Permissions.MANAGE_ROLES) == true) && ((rolePosition == -1) || (role.getPosition() > rolePosition)))
			{
				rolePosition = role.getPosition();
			}
		}

		if (rolePosition == -1)
		{
			throw new Exception("Missing permission " + Permissions.MANAGE_ROLES.name());
		}

		List<IRole> serverRoles = server.getRoles();
		List<IRole> manageableRoles = new ArrayList<IRole>();

		for (int roleIndex = 0; roleIndex < serverRoles.size(); roleIndex++)
		{
			IRole role = serverRoles.get(roleIndex);

			if (role.isEveryoneRole() == true)
			{
				continue;
			}

			if (role.getPosition() < rolePosition)
			{
				manageableRoles.add(role);
			}
		}

		return manageableRoles;
	}

	private static boolean isLocked(IRole role)
	{
		Object lockedRoles = Jeeves.serverConfig.getValue(role.getGuild().getLongID(), "lockedRoles");

		if (lockedRoles.getClass() == String.class)
		{
			return false;
		}

		List<String> lockedRoleList = Jeeves.listToStringList((List<?>)lockedRoles);
		String roleIdString = Long.toString(role.getLongID());
		return lockedRoleList.contains(roleIdString);
	}

	public static class GetRoles extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Check which roles the bot can manage.";
			return output;
		}

		@Override
		public String getParameters()
		{
			return null;
		}

		@Override
		public void execute(IMessage message, String argumentString)
		{
			List<IRole> manageableRoles;

			try
			{
				manageableRoles = Roles.getManageableRoles(message.getGuild());
			}
			catch (Exception e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "The bot does not have the permission to manage roles.");
				return;
			}

			if (manageableRoles.size() == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "No manageable roles found.");
				return;
			}

			String output = "The following roles can be managed by the bot:\n";
			int foundRoles = 0;

			for (int roleIndex = 0; roleIndex < manageableRoles.size(); roleIndex++)
			{
				IRole role = manageableRoles.get(roleIndex);

				if (Roles.isLocked(role) == true)
				{
					continue;
				}

				foundRoles++;
				output += "\t" + role.getName() + "\n";
			}

			if (foundRoles == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "All manageable roles are locked.");
				return;
			}

			MessageQueue.sendMessage(message.getChannel(), output);
		}
	}

	public static class Join extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Join one of the public roles.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<role>";
			return output;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
				return;
			}

			List<IRole> manageableRoles;

			try
			{
				manageableRoles = Roles.getManageableRoles(message.getGuild());
			}
			catch (Exception e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "The bot does not have the permission to manage roles.");
				return;
			}

			if (manageableRoles.contains(role) == false)
			{
				MessageQueue.sendMessage(message.getChannel(), "The bot is not allowed to manage the role " + role.getName());
				return;
			}

			if (Roles.isLocked(role) == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "The role " + role.getName() + " is locked.");
				return;
			}

			if (message.getAuthor().hasRole(role) == true)
			{
				MessageQueue.sendMessage(message.getChannel(), message.getAuthor().getName() + " already has the role " + role.getName());
				return;
			}

			RoleQueue.addRoleToUser(role, message.getAuthor(), message.getChannel());
		}
	}

	public static class Leave extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Leave one of the public roles.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<role>";
			return output;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
				return;
			}

			List<IRole> manageableRoles;

			try
			{
				manageableRoles = Roles.getManageableRoles(message.getGuild());
			}
			catch (Exception e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "The bot does not have the permission to manage roles.");
				return;
			}

			if (manageableRoles.contains(role) == false)
			{
				MessageQueue.sendMessage(message.getChannel(), "The bot is not allowed to manage the role " + role.getName());
				return;
			}

			if (Roles.isLocked(role) == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "The role " + role.getName() + " is locked.");
				return;
			}

			if (message.getAuthor().hasRole(role) == false)
			{
				MessageQueue.sendMessage(message.getChannel(), message.getAuthor().getName() + " does not have the role " + role.getName());
				return;
			}

			RoleQueue.removeRoleFromUser(role, message.getAuthor(), message.getChannel());
		}
	}

	public static class Members extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Get a list of users with the given role.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<role>";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
			}

			List<IUser> userList = message.getGuild().getUsersByRole(role);

			if (userList.size() == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "The role " + role.getName() + " has no members.");
				return;
			}

			String output = "The role " + role.getName() + " has the following members:\n";

			for (int userIndex = 0; userIndex < userList.size(); userIndex++)
			{
				output += "\t" + userList.get(userIndex).getName() + "\n";
			}

			MessageQueue.sendMessage(message.getAuthor(), output);
			MessageQueue.sendMessage(message.getChannel(), "Member list sent as private message.");
		}
	}

	public static class MissingRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Get a list of users missing the given role.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<role>";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
			}

			List<IUser> userList = message.getGuild().getUsers();
			List<IUser> usersMissingRole = new ArrayList<IUser>();

			for (int userIndex = 0; userIndex < userList.size(); userIndex++)
			{
				IUser user = userList.get(userIndex);

				if (user.hasRole(role) == true)
				{
					continue;
				}

				usersMissingRole.add(user);
			}

			if (usersMissingRole.size() == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "No users are missing the role " + role.getName());
				return;
			}

			String output = "The following users are missing the role " + role.getName() + "\n";

			for (int userIndex = 0; userIndex < usersMissingRole.size(); userIndex++)
			{
				output += "\t" + usersMissingRole.get(userIndex).getName() + "\n";
			}

			MessageQueue.sendMessage(message.getAuthor(), output);
			MessageQueue.sendMessage(message.getChannel(), "User list sent as private message.");
		}
	}

	public static class GetUntaggedUsers extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Get a list users without any roles.";
			return output;
		}

		@Override
		public String getParameters()
		{
			return null;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String argumentString)
		{
			List<IUser> userList = message.getGuild().getUsers();
			List<IUser> untaggedUsers = new ArrayList<IUser>();

			for (int userIndex = 0; userIndex < userList.size(); userIndex++)
			{
				IUser user = userList.get(userIndex);
				List<IRole> roleList = user.getRolesForGuild(message.getGuild());

				if (roleList.size() > 0)
				{
					continue;
				}

				untaggedUsers.add(user);
			}

			if (untaggedUsers.size() == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "There are no untagged users on this Discord.");
				return;
			}

			String output = "The following users have no role:\n";

			for (int userIndex = 0; userIndex < untaggedUsers.size(); userIndex++)
			{
				output += "\t" + untaggedUsers.get(userIndex).getName() + "\n";
			}

			MessageQueue.sendMessage(message.getAuthor(), output);
			MessageQueue.sendMessage(message.getChannel(), "User list sent as private message.");
		}
	}

	public static class GetAutoRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Get the role automatically assigned to new users.";
			return output;
		}

		@Override
		public String getParameters() 
		{
			return null;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String argumentString)
		{
			String roleIdString = (String)Jeeves.serverConfig.getValue(message.getGuild().getLongID(), "autoRole");

			if (roleIdString.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "No automatically assigned role has been set.");
				return;
			}

			long roleId = Long.parseLong(roleIdString);
			IRole role = message.getGuild().getRoleByID(roleId);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "An automatically assigned role has been set, but it does not exist.");

				Roles roles = new Roles();
				Jeeves.serverConfig.setValue(message.getGuild().getLongID(), "autoRole", roles.getDefaultConfig().get("autoRole"));

				try
				{
					Jeeves.serverConfig.saveConfig();
				}
				catch (ParserConfigurationException | TransformerException e)
				{
					Jeeves.debugException(e);
				}

				return;
			}

			MessageQueue.sendMessage(message.getChannel(), "The automatically assigned role is: " + role.getName());
		}
	}

	public static class SetAutoRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Set or clear the role automatically assigned to new users.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "[role]";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			IRole role = null;

			if (roleName.isEmpty() == true)
			{
				Jeeves.serverConfig.setValue(message.getGuild().getLongID(), "autoRole", "");
			}
			else
			{
				role = Jeeves.findRole(message.getGuild(), roleName);

				if (role == null)
				{
					MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
					return;
				}

				String roleIdString = Long.toString(role.getLongID());
				Jeeves.serverConfig.setValue(message.getGuild().getLongID(), "autoRole", roleIdString);
			}

			try
			{
				Jeeves.serverConfig.saveConfig();
			}
			catch (ParserConfigurationException | TransformerException e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "Cannot store the setting.");
				return;
			}

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "The automatically assigned role has been cleared.");
			}
			else
			{
				MessageQueue.sendMessage(message.getChannel(), "The automatically assigned role has been set to: " + role.getName());
			}
		}
	}

	public static class LockRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Lock a role so users can no longer assign it to themselves.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<role>";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
				return;
			}

			if (Roles.isLocked(role) == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "The role " + role.getName() + " is locked already.");
				return;
			}

			Object lockedRoles = Jeeves.serverConfig.getValue(message.getGuild().getLongID(), "lockedRoles");
			List<String> lockedRoleList;

			if (lockedRoles.getClass() == String.class)
			{
				lockedRoleList = new ArrayList<String>();
			}
			else
			{
				lockedRoleList = Jeeves.listToStringList((List<?>)lockedRoles);
			}

			String roleIdString = Long.toString(role.getLongID());
			lockedRoleList.add(roleIdString);
			Jeeves.serverConfig.setValue(message.getGuild().getLongID(), "lockedRoles", lockedRoleList);

			try
			{
				Jeeves.serverConfig.saveConfig();
			}
			catch (ParserConfigurationException | TransformerException e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "Cannot store the setting.");
				return;
			}

			MessageQueue.sendMessage(message.getChannel(), "The following role has been locked: " + role.getName());
		}
	}

	public static class UnlockRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Unlock a role so users can assign it to themselves.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<role>";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String roleName)
		{
			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
				return;
			}

			Object lockedRoles = Jeeves.serverConfig.getValue(message.getGuild().getLongID(), "lockedRoles");

			if (lockedRoles.getClass() == String.class)
			{
				MessageQueue.sendMessage(message.getChannel(), "No roles are locked.");
				return;
			}

			List<String> lockedRoleList = Jeeves.listToStringList((List<?>)lockedRoles);

			if (lockedRoleList.size() == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "No roles are locked.");
				return;
			}

			String roleIdString = Long.toString(role.getLongID());
			boolean removed = lockedRoleList.remove(roleIdString);

			if (removed == false)
			{
				MessageQueue.sendMessage(message.getChannel(), "The role " + role.getName() + " is not locked.");
				return;
			}

			Jeeves.serverConfig.setValue(message.getGuild().getLongID(), "lockedRoles", lockedRoleList);

			try
			{
				Jeeves.serverConfig.saveConfig();
			}
			catch (ParserConfigurationException | TransformerException e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "Cannot store the setting.");
				return;
			}

			MessageQueue.sendMessage(message.getChannel(), "The following role has been unlocked: " + role.getName());
		}
	}

	public static class GetLockedRoles extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Get the list of locked roles.";
			return output;
		}

		@Override
		public String getParameters()
		{
			return null;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_SERVER;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String argumentString)
		{
			List<IRole> manageableRoles;

			try
			{
				manageableRoles = Roles.getManageableRoles(message.getGuild());
			}
			catch (Exception e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "The bot does not have the permission to manage roles.");
				return;
			}

			if (manageableRoles.size() == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "No manageable roles found.");
				return;
			}

			String output = "The following roles are locked:\n";
			int foundRoles = 0;

			for (int roleIndex = 0; roleIndex < manageableRoles.size(); roleIndex++)
			{
				IRole role = manageableRoles.get(roleIndex);

				if (Roles.isLocked(role) == false)
				{
					continue;
				}

				foundRoles++;
				output += "\t" + role.getName() + "\n";
			}

			if (foundRoles == 0)
			{
				MessageQueue.sendMessage(message.getChannel(), "None of the manageable roles are locked.");
				return;
			}

			MessageQueue.sendMessage(message.getChannel(), output);
		}
	}

	public static class AssignRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Assign a role to another user.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<user> : <role>";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_ROLES;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String argumentString)
		{
			String[] arguments = Jeeves.splitArguments(argumentString);

			if (arguments.length < 2)
			{
				MessageQueue.sendMessage(message.getChannel(), "Insufficient amount of parameters.\n" + this.getParameters());
				return;
			}

			String userName = arguments[0];
			IUser user;

			if (userName.isEmpty() == true)
			{
				user = message.getAuthor();
			}
			else
			{
				user = Jeeves.findUser(message.getGuild(), userName);
			}

			if (user == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the user " + userName);
			}

			String roleName = arguments[1];

			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
			}

			List<IRole> manageableRoles;

			try
			{
				manageableRoles = Roles.getManageableRoles(message.getGuild());
			}
			catch (Exception e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "The bot does not have the permission to manage roles.");
				return;
			}

			if (manageableRoles.contains(role) == false)
			{
				MessageQueue.sendMessage(message.getChannel(), "The bot is not allowed to manage the role " + role.getName());
				return;
			}

			if (user.hasRole(role) == true)
			{
				MessageQueue.sendMessage(message.getChannel(), user.getName() + " already has the role " + role.getName());
				return;
			}

			RoleQueue.addRoleToUser(role, user, message.getChannel());
		}
	}

	public static class UnassignRole extends BotCommand
	{
		@Override
		public String getHelp()
		{
			String output = "Remove a role from another user.";
			return output;
		}

		@Override
		public String getParameters()
		{
			String output = "<user> : <role>";
			return output;
		}

		@Override
		public Permissions[] permissions()
		{
			Permissions[] permissionList = new Permissions[1];
			permissionList[0] = Permissions.MANAGE_ROLES;
			return permissionList;
		}

		@Override
		public void execute(IMessage message, String argumentString)
		{
			String[] arguments = Jeeves.splitArguments(argumentString);

			if (arguments.length < 2)
			{
				MessageQueue.sendMessage(message.getChannel(), "Insufficient amount of parameters.\n" + this.getParameters());
				return;
			}

			String userName = arguments[0];
			IUser user;

			if (userName.isEmpty() == true)
			{
				user = message.getAuthor();
			}
			else
			{
				user = Jeeves.findUser(message.getGuild(), userName);
			}

			if (user == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the user " + userName);
			}

			String roleName = arguments[1];

			if (roleName.isEmpty() == true)
			{
				MessageQueue.sendMessage(message.getChannel(), "You need to provide a role name.");
				return;
			}

			IRole role = Jeeves.findRole(message.getGuild(), roleName);

			if (role == null)
			{
				MessageQueue.sendMessage(message.getChannel(), "Cannot find the role " + roleName);
			}

			List<IRole> manageableRoles;

			try
			{
				manageableRoles = Roles.getManageableRoles(message.getGuild());
			}
			catch (Exception e)
			{
				Jeeves.debugException(e);
				MessageQueue.sendMessage(message.getChannel(), "The bot does not have the permission to manage roles.");
				return;
			}

			if (manageableRoles.contains(role) == false)
			{
				MessageQueue.sendMessage(message.getChannel(), "The bot is not allowed to manage the role " + role.getName());
				return;
			}

			if (user.hasRole(role) == false)
			{
				MessageQueue.sendMessage(message.getChannel(), user.getName() + " does not have the role " + role.getName());
				return;
			}

			RoleQueue.removeRoleFromUser(role, user, message.getChannel());
		}
	}
}
