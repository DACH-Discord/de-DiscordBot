package de.nikos410.discordBot.util.modular;

public class CommandPermissions {
    public final static int EVERYONE = 0;
    public final static int MODERATOR = 1;
    public final static int ADMIN = 2;
    public final static int OWNER = 3;

    public static String getPermissionLevelName(final int permissionLevel) {
        switch (permissionLevel) {
            case 0: return "Everyone";
            case 1: return "Moderator";
            case 2: return "Admin";
            case 3: return "Owner";
            default: return "Ungültige Rolle";
        }
    }
}
