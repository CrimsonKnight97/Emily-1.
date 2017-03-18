/*
 * Copyright 2017 github.com/kaaz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package emily.command.administrative;

import emily.command.CommandVisibility;
import emily.core.AbstractCommand;
import emily.db.controllers.CGuild;
import emily.db.controllers.CGuildRoleAssignable;
import emily.handler.Template;
import emily.main.Config;
import emily.main.DiscordBot;
import emily.permission.SimpleRank;
import emily.role.RoleRankings;
import emily.templates.Templates;
import emily.util.DisUtil;
import emily.util.Misc;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.utils.PermissionUtil;

import java.util.List;

/**
 * !role
 * manages roles
 */
public class RoleAdminCommand extends AbstractCommand {
    public RoleAdminCommand() {
        super();
    }

    @Override
    public String getDescription() {
        return "Management of roles & general permissions " + Config.EOL +
                "You can give users the ability to self-assign roles. " + Config.EOL +
                "" + Config.EOL +
                "Note: " + Config.EOL +
                "self-assignable roles are not created by emily!" + Config.EOL +
                "To add an assignable role, you'll first have to add that role though discord." + Config.EOL +
                "" + Config.EOL +
                "" + Config.EOL +
                "Users can get/remove their own roles with the `getrole` command ";
    }

    @Override
    public String getCommand() {
        return "roleadmin";
    }

    @Override
    public String[] getUsage() {
        return new String[]{
                "You can specify which roles are self-assignable by users with the following commands: ",
                "",
                "roleadmin self                                 //check what roles are self-assignable",
                "roleadmin self add <rolename>                  //add a role to the list of assignable roles",
                "roleadmin self remove <rolename>               //remove a role from the list of assignable roles",
//				"roleadmin self describe <role> <description>   //add a description to what this role does",
                "",
                "//You can use everyone as <user> and it'll apply to everyone",
                "roleadmin give <user> <role>     //gives a user a role",
                "roleadmin take <user> <role>     //takes away role",
                "",
                "roleadmin                        //lists roles",
                "roleadmin cleanup                //cleans up the roles from the time-based rankings",
                "roleadmin setup                  //creates the roles for the time-based rankings",
//				"roleadmin bind BOT_ROLE <discordrole> //binds a discordrole to a botrole",
//				"roleadmin add @user <role>            //adds role to user",
//				"roleadmin remove @user <role>         //remove role from user",
        };
    }

    @Override
    public String[] getAliases() {
        return new String[]{
                "ra"
        };
    }

    @Override
    public CommandVisibility getVisibility() {
        return CommandVisibility.PUBLIC;
    }

    @Override
    public String execute(DiscordBot bot, String[] args, MessageChannel channel, User author) {
        Guild guild = ((TextChannel) channel).getGuild();
        SimpleRank rank = bot.security.getSimpleRank(author, channel);
        if (!rank.isAtLeast(SimpleRank.GUILD_ADMIN)) {
            return Template.get("command_no_permission");
        }
        if (args.length == 0 || args[0].equals("list")) {
            String out = "I found the following roles" + Config.EOL;
            List<Role> roles = guild.getRoles();
            for (Role role : roles) {
                if (role.getPosition() == -1) {
                    continue;
                }
                out += String.format("%s (%s)" + Config.EOL, role.getName(), role.getId());
            }
            return out;
        }
        if (!PermissionUtil.checkPermission(guild, guild.getSelfMember(), Permission.MANAGE_ROLES)) {
            return Template.get("permission_missing_manage_roles");
        }
        switch (args[0].toLowerCase()) {
            case "self":
                if (args.length == 1) {
                    return "self roles overview";
                }
                if (args.length < 3) {
                    return Template.get("command_invalid_use");
                }
                String roleName = Misc.joinStrings(args, 2);
                Role role = DisUtil.findRole(guild, roleName);
                if (role == null) {
                    return "role not found";
                }
                switch (args[1].toLowerCase()) {
                    case "add":
                    case "+":
                        CGuildRoleAssignable.insertOrUpdate(CGuild.getCachedId(guild.getId()), role.getId(), role.getName());
                        return Template.get("command_role_admin_adding", role.getName());
                    case "remove":
                    case "-":
                        CGuildRoleAssignable.delete(CGuild.getCachedId(guild.getId()), role.getId());
                        return Template.get("command_role_admin_removing", role.getName());
                    case "describe":
                        return Template.get("not_implemented_yet");
                }
            case "cleanup":
                RoleRankings.cleanUpRoles(guild, channel.getJDA().getSelfUser());
                return "Removed all the time-based roles";
            case "setup":
                if (RoleRankings.canModifyRoles(guild, channel.getJDA().getSelfUser())) {
                    RoleRankings.fixForServer(guild);
                    return "Set up all the required roles :smile:";
                }
                return "No permissions to manage roles";
            case "give":
                if (args.length < 3) {
                    return Templates.command.invalid_use.formatGuild(guild.getId());
                }
                return mutateRole((TextChannel) channel, args[1], args[2], true);
            case "take":
                if (args.length < 3) {
                    return Templates.command.invalid_use.formatGuild(guild.getId());
                }
                return mutateRole((TextChannel) channel, args[1], args[2], false);
            default:
                return ":face_palm: I expected you to know how to use it";
        }
    }

    private String mutateRole(TextChannel channel, String user, String role, boolean adding) {
        Role r = DisUtil.findRole(channel.getGuild(), role);
        if (r == null) {
            return "can't find a role matching **" + role + "**";
        }
        if ("everyone".equalsIgnoreCase(user)) {
            for (Member member : channel.getGuild().getMembers()) {
                _mutateRole(r, member, adding);
            }
            if (adding) {
                return "(trying) to give everyone " + r.getName();
            }
            return "(trying) to remove *" + r.getName() + "* from everyone ";
        }
        User u = DisUtil.findUser(channel, user);
        if (u == null) {
            return "cant find user matching " + user;
        }
        _mutateRole(r, channel.getGuild().getMember(u), adding);
        if (adding) {
            return String.format("adding %s to %s", r.getName(), u.getName());
        }
        return String.format("removing %s to %s", r.getName(), u.getName());
    }

    private void _mutateRole(Role role, Member member, boolean adding) {
        if (PermissionUtil.canInteract(role.getGuild().getSelfMember(), role)) {
            if (adding) {
                for (Role memberRole : member.getRoles()) {
                    if (memberRole.getId().equals(role.getId())) {
                        return;
                    }
                }
                role.getGuild().getController().addRolesToMember(member, role).complete();
            } else {
                boolean hasRole = false;
                for (Role memberRole : member.getRoles()) {
                    if (memberRole.getId().equals(role.getId())) {
                        hasRole = true;
                        break;
                    }
                }
                if (!hasRole) {
                    return;
                }
                role.getGuild().getController().removeRolesFromMember(member, role).complete();
            }
        }
    }
}