package de.nikos410.discordBot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.nikos410.discordBot.modular.*;
import de.nikos410.discordBot.modular.annotations.*;
import de.nikos410.discordBot.util.discord.*;
import de.nikos410.discordBot.util.io.IOUtil;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.*;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * The bots main class, containing most of the modular framework
 */
public class DiscordBot {
    private final List<String> unloadedModules = new ArrayList<>();
    private final Map<String, Object> loadedModules = new HashMap<>();

    private final Map<String, Command> commands = new HashMap<>();

    public final IDiscordClient client;

    private final static Path CONFIG_PATH = Paths.get("config/config.json");
    public final JSONObject rolesJSON;
    private final static Path ROLES_PATH = Paths.get("data/roles.json");
    public final JSONObject configJSON;

    private final String prefix;
    private final long ownerID;

    private final static Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    /**
     * Sets up the bot, loads configuration.
     */
    private DiscordBot() {
        // Read config file
        final String configFileContent = IOUtil.readFile(CONFIG_PATH);
        if (configFileContent == null) {
            LOG.error("Could not read configuration file.");
            System.exit(1);
        }
        this.configJSON = new JSONObject(configFileContent);
        LOG.info(String.format("Loaded configuration with %s entries.", configJSON.keySet().size()));

        // Read roles file
        final String rolesFileContent = IOUtil.readFile(ROLES_PATH);
        if (rolesFileContent == null) {
            LOG.error("Could not read roles file.");
            System.exit(1);
        }
        this.rolesJSON = new JSONObject(rolesFileContent);
        LOG.info(String.format("Loaded roles file for %s guilds.", rolesJSON.keySet().size()));

        // Get token from config
        final String token = configJSON.getString("token");
        // Authorize using token
        this.client = Authorization.createClient(token, true);
        LOG.info("Bot authorized.");

        // Get prefix and owner ID from config
        this.prefix = configJSON.getString("prefix");
        this.ownerID = configJSON.getLong("owner");

        // Register Eventlistener
        try {
            this.client.getDispatcher().registerListener(this);
        }
        catch (NullPointerException e) {
            LOG.error("Could not get EventDispatcher", e);
        }

        // Get unloaded modules
        final JSONArray unloadedModulesJSON = this.configJSON.getJSONArray("unloadedModules");

        for (int i = 0; i < unloadedModulesJSON.length(); i++) {
            final String unloadedModuleName = unloadedModulesJSON.getString(i);
            this.unloadedModules.add(unloadedModuleName);
        }

        this.loadModules();
    }

    /**
     * Loads modules from classes that are located in the package 'de.nikos410.discordBot.modules' and are annotated
     * with @CommandModule.
     */
    private void loadModules() {
        LOG.debug("Loading modules.");

        LOG.debug("Clearing old modules.");
        this.loadedModules.clear();

        // Search in package 'de.nikos410.discordBot.modules'
        final Reflections reflections = new Reflections("de.nikos410.discordBot.modules");
        // Find classes that are annotated with @CommandModule
        final Set<Class<?>> moduleClasses = reflections.getTypesAnnotatedWith(CommandModule.class);

        LOG.info(String.format("Found %s total module(s).", moduleClasses.size()));

        // Load modules from all found classes
        for (final Class<?> moduleClass : moduleClasses) {
            loadModule(moduleClass);
        }

        // Create command map
        this.makeCommandMap();
        LOG.info(String.format("%s module(s) with %s command(s) active.", this.loadedModules.size(), this.commands.size()));
    }

    /**
     * Loads a module from a class. The specified class has to be annotated with @CommandModule.
     *
     * @param moduleClass The class containing the module
     */
    private void loadModule(final Class<?> moduleClass) {
        LOG.debug(String.format("Loading module information from class %s.", moduleClass));

        // Get module name from annotation parameters
        final CommandModule moduleAnnotation = moduleClass.getDeclaredAnnotationsByType(CommandModule.class)[0];
        final String moduleName = moduleAnnotation.moduleName();

        // Check if module is deactivated in config
        if (this.unloadedModules.contains(moduleName)) {
            LOG.info(String.format("Module \"%s\" is deactivated. Skipping.", moduleName));
            return;
        }

        LOG.debug(String.format("Loading module \"%s\".", moduleName));
        // Create an instance of the class
        final Object moduleObject = makeModuleObject(moduleClass);
        if (moduleObject != null) {
            this.loadedModules.put(moduleName, moduleObject);

            // Register EventListener if needed
            if (!moduleAnnotation.commandOnly()) {
                final EventDispatcher dispatcher = this.client.getDispatcher();
                dispatcher.registerListener(moduleObject);
            }

            LOG.info(String.format("Successfully loaded module \"%s\".", moduleName));
        }
    }

    /**
     * Creates an instance of a class containing a module.
     *
     * @param moduleClass The class containing the module
     * @return The created Object
     */
    private Object makeModuleObject (Class<?> moduleClass) {
        LOG.debug(String.format("Creating object from class %s.", moduleClass));

        try {
            // Using try-catch to differentiate between two possible constructors
            Object moduleObject;
            try {
                // Constructor with one parameter of type 'DiscordBot'
                LOG.debug(String.format("Trying to create an object from class %s with parameter.", moduleClass.getName()));
                moduleObject = moduleClass.getDeclaredConstructor(DiscordBot.class).newInstance(this);

                LOG.debug(String.format("Successfully created object from class %s.", moduleClass));
                return moduleObject;
            }
            catch (NoSuchMethodException e) {
                // Constructor without parameters
                LOG.debug(String.format("Failed to create an object from class %s with parameter. Trying without parameters.",
                        moduleClass.getName()));
                moduleObject = moduleClass.newInstance();

                LOG.debug(String.format("Successfully created object from class %s.", moduleClass));
                return moduleObject;
            }
        }
        catch (InstantiationException | IllegalAccessException e) {
            LOG.warn(String.format("Something went wrong while creating object from class \"%s\". Skipping.", moduleClass.getName()), e);
            return null;
        }
        catch (InvocationTargetException e) {
            LOG.warn(String.format("Something went wrong while creating object from class \"%s\". Skipping.", moduleClass.getName()), e.getCause());
            return null;
        }
    }

    /**
     * Populates global command map, maps a 'Command' object, containing a commands attributes, to each command.
     */
    private void makeCommandMap() {
        LOG.debug("Creating command map.");

        LOG.debug("Clearing old commands.");
        this.commands.clear();

        for (final String key : this.loadedModules.keySet()) {
            Object module = this.loadedModules.get(key);

            LOG.debug(String.format("Registering command(s) for module \"%s\".", key));

            for (final Method method : module.getClass().getMethods()) {

                // Register methods with the @CommandSubscriber as commands
                if (method.isAnnotationPresent(CommandSubscriber.class)) {

                    // All annotations of type CommandSubscriber declared for that Method. Should be exactly 1
                    final CommandSubscriber[] annotations = method.getDeclaredAnnotationsByType(CommandSubscriber.class);

                    // Get command properties from annotation
                    final String command = annotations[0].command();
                    final boolean pmAllowed = annotations[0].pmAllowed();
                    final int permissionLevel = annotations[0].permissionLevel();
                    final int parameterCount = method.getParameterCount();
                    final boolean passContext = annotations[0].passContext();

                    // At least 1 (message), max 6 (message + 5 parameter)
                    if (parameterCount > 0 && parameterCount <= 6) {
                        final Command cmd = new Command(module, method, pmAllowed, permissionLevel, parameterCount-1, passContext);
                        this.commands.put(command.toLowerCase(), cmd);

                        LOG.debug(String.format("Registered command \"%s\".", command));
                    }
                    else {
                        LOG.warn(String.format("Command \"%s\" has an invalid number of arguments. Skipping", command));
                    }
                }
            }
        }
    }

    /**
     * Gets called when a message is received.
     *
     * @param event The event containing the message
     */
    @EventSubscriber
    public void onMessageReceived(final MessageReceivedEvent event) {
        if (event.getChannel().isPrivate()) {
            LOG.debug(String.format("Received PM from user with ID %s. Message length: %s",
                    event.getAuthor().getStringID(),
                    event.getMessage().getContent().length()));
        }
        else {
            LOG.debug(String.format("Guild %s: Received message from user with ID %s in channel with ID %s. Message length: %s",
                    event.getGuild().getStringID(),
                    event.getAuthor().getStringID(),
                    event.getChannel().getStringID(),
                    event.getMessage().getContent().length()));
        }

        handleMessage(event.getMessage());
    }

    /**
     * Gets called when a message is edited. If it is less than 20 seconds old, handle like a newly received message.
     *
     * @param event The event containing the message
     */
    @EventSubscriber
    public void onMessageEdited(final MessageUpdateEvent event) {
        final IMessage message = event.getNewMessage();

        if (message.getEditedTimestamp().isPresent()) {
            final LocalDateTime messageTimestamp = message.getTimestamp();
            final LocalDateTime editTimestamp = message.getEditedTimestamp().get();

            final long messageAge = messageTimestamp.until(editTimestamp, ChronoUnit.SECONDS);

            if (event.getChannel().isPrivate()) {
                LOG.debug(String.format("PM from user with ID %s was edited. Message length: %s | Message age: %s seconds",
                        event.getAuthor().getStringID(),
                        event.getMessage().getContent().length(),
                        messageAge));
            }
            else {
                LOG.debug(String.format("Guild %s: Message in channel with ID %s by user %s was edited. Message length: %s | Message age: %s seconds",
                        event.getGuild().getStringID(),
                        event.getAuthor().getStringID(),
                        event.getChannel().getStringID(),
                        event.getMessage().getContent().length(),
                        messageAge));
            }

            if (messageAge < 20) {
                LOG.debug("Message age < 20 seconds. Processing message.");

                handleMessage(message);
            }
            else {
                LOG.debug("Message age >= 20 seconds. Ignoring message.");
            }
        }
    }

    /**
     * Process a received or edited message. Check if it contains a command and execute the corresponding method.
     *
     * @param message The received/edited message
     */
    private void handleMessage(final IMessage message) {
        final String messageContent = message.getContent();

        // Check if the message starts with the configured prefix
        if (!messageContent.startsWith(this.prefix)) {
            return;
        }

        // Get only the command in lower case without prefix/parameters
        final String commandName = (
                    messageContent.contains(" ") ?
                    messageContent.substring(this.prefix.length(), messageContent.indexOf(' ')) :   // Message contains parameters
                    messageContent.substring(this.prefix.length())  // Message doesn't contain parameters
                ).toLowerCase();

        // Check if a command with that name is known
        if (!commands.containsKey(commandName)) {
            return;
        }

        final Command command = commands.get(commandName);

        LOG.info(String.format("User %s used command %s", UserOperations.makeUserString(message.getAuthor(), message.getGuild()), commandName));

        // Check if the user is allowed to use that command
        final int userPermissionLevel = this.getUserPermissionLevel(message.getAuthor(), message.getGuild());
        LOG.debug(String.format("Checking permissions. User: %s | Required: %s", userPermissionLevel, command.permissionLevel));
        if (userPermissionLevel < command.permissionLevel) {
            DiscordIO.sendMessage(message.getChannel(), String.format("Dieser Befehl ist für deine Gruppe (%s) nicht verfügbar.",
                    CommandPermissions.getPermissionLevelName(userPermissionLevel)));
            LOG.info(String.format("User %s doesn't have the required permissions for using the command %s.",
                    UserOperations.makeUserString(message.getAuthor(), message.getGuild()),
                    commandName));
            return;
        }

        // The command was received in a PM but is only available on guilds
        if (message.getChannel().isPrivate() && !command.pmAllowed) {
            DiscordIO.sendMessage(message.getChannel(), "Dieser Befehl ist nicht in Privatnachrichten verfügbar!");
            LOG.info(String.format("Command %s is not available in PMs.", commandName));
            return;
        }

        final int parameterCount = command.parameterCount;
        final boolean passContext = command.passContext;
        final List<String> parameters = parseParameters(messageContent, parameterCount, passContext);

        // Check if the user used the correct number of parameters
        if (parameters.size() < parameterCount) {
            DiscordIO.sendMessage(message.getChannel(), String.format("Dieser Befehl benötigt mindestens %s Parameter! (Gegeben: %s)", parameterCount, parameters.size()));
            LOG.info(String.format("Wrong number of arguments. Expected number: %s Actual number: %s",
                    parameterCount, parameters.size()));
            return;
        }

        executeCommand(commandName, command, parameters, message);
    }

    /**
     * Invoke the method specified in a Command object with the parameters in a List
     *
     * @param commandName The name of the command
     * @param command The object containing the attributes of the command, specifically the method to invoke
     * @param parameters The parameters to use while invoking the method
     * @param message The message that triggered the command
     */
    private void executeCommand(final String commandName, final Command command, final List<String> parameters, final IMessage message) {
        LOG.debug(String.format("Executing command %s with %s parameters.", commandName, parameters.size()));

        try {
            switch (parameters.size()) {
                case 0: {
                    command.method.invoke(command.object, message);
                    break;
                }
                case 1: {
                    command.method.invoke(command.object, message, parameters.get(0));
                    break;
                }
                case 2: {
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1));
                    break;
                }
                case 3: {
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1), parameters.get(2));
                    break;
                }
                case 4: {
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1), parameters.get(2),
                            parameters.get(3));
                    break;
                }
                case 5: {
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1), parameters.get(2),
                            parameters.get(3), parameters.get(4));
                    break;
                }
                default: {
                    LOG.error(String.format("Command \"%s\" has an invalid number of arguments. This should never happen.",
                            commandName));
                    DiscordIO.errorNotify("Befehl kann wegen einer ungültigen Anzahl an Argumenten nicht " +
                            "ausgeführt werden. Dies sollte niemals passieren!", message.getChannel());
                }
            }
        }
        catch (IllegalAccessException | InvocationTargetException e) {
            final Throwable cause = e.getCause();

            LOG.error(String.format("Command \"%s\" could not be executed.", commandName), e.getCause());
            DiscordIO.errorNotify(cause.toString(), message.getChannel());
        }
    }

    /**
     * Parse the parameters from message input. Every word (after prefix+command) is one parameter.
     *
     * @param messageContent The full input message content
     * @param parameterCount The number of parameters to parse
     * @param passContext If true, additional words are appended to last parameter. If false, additional words are ignored
     * @return The list containing the parsed parameters
     */
    private List<String> parseParameters(final String messageContent, int parameterCount, boolean passContext) {
        final LinkedList<String> parameters = new LinkedList<>();

        // Remove prefix
        // Necessary because prefix might contain spaces
        final String content = messageContent.substring(prefix.length());

        // Remove command
        final Pattern pattern = Pattern.compile("(\\S*)\\s+(.*)");
        final Matcher matcher = pattern.matcher(content);
        if (!matcher.matches() || matcher.groupCount() != 2) {
            return parameters;
        }
        final String parameterContent = matcher.group(2);   // The String containing only the parameters

        // Seperate the individual parameters
        final String[] contentParts = parameterContent.split("\\s+");

        int i;
        for (i = 0; i < parameterCount; i++) {
            if (i < contentParts.length) {
                parameters.add(contentParts[i]);
            }
        }

        // If passContext is true, append possible additional words to last parameter
        if (passContext && i < contentParts.length) {
            final String last = parameters.removeLast();
            final StringBuilder builder = new StringBuilder();
            builder.append(last);

            for (; i < contentParts.length; i++) {
                builder.append(' ');
                builder.append(contentParts[i]);
            }

            parameters.add(builder.toString());
        }

        return parameters;
    }

    /**
     * Get the permission level of a user on a guild. Returns 0 (level EVERYONE) if no roles are configured for the
     * specified guild
     *
     * @param user The user whose permission level gets returned
     * @param guild The guild on which the permission level counts
     * @return The user's permission level on the guiild
     */
    public int getUserPermissionLevel(final IUser user, final IGuild guild) {
        // User is the configured owner of the bot
        if (user.getLongID() == this.ownerID) {
            return CommandPermissions.OWNER;
        }

        // If no roles are configured for this guild return the lowest level
        if (!rolesJSON.has(guild.getStringID())) {
            LOG.warn(String.format("Rollen für Server %s (ID: %s) nicht konfiguriert!", guild.getName(), guild.getStringID()));
            return CommandPermissions.EVERYONE;
        }

        final JSONObject guildRoles = rolesJSON.getJSONObject(guild.getStringID());

        final long adminRoleID = guildRoles.getLong("adminRole");
        if (UserOperations.hasRoleByID(user, adminRoleID, guild)) {
            return CommandPermissions.ADMIN;
        }

        final long modRoleID = guildRoles.getLong("modRole");
        if (UserOperations.hasRoleByID(user, modRoleID, guild)) {
            return CommandPermissions.MODERATOR;
        }

        return CommandPermissions.EVERYONE;
    }

    /**
     * Print out some text and change the playing-text when bot is ready
     */
    @EventSubscriber
    public void onStartup(final ReadyEvent event) {
        LOG.info(String.format("[INFO] Bot ready. Prefix: %s", this.prefix));
        LOG.info(String.format("Add this bot to a server: https://discordapp.com/oauth2/authorize?client_id=%s&scope=bot", client.getApplicationClientID()));
        client.changePlayingText(String.format("%shelp | WIP", this.prefix));
    }

    /**
     * Returns the map containing the loaded modules
     *
     * @return The map containing the loaded modules
     */
    public Map<String, Object> getLoadedModules() {
        return loadedModules;
    }

    /**
     * Returns the list containing the names of the unloaded modules
     *
     * @return The list containing the names of the unloaded modules
     */
    public List<String> getUnloadedModules() {
        return unloadedModules;
    }

    /**
     * Activate a module so the bot loads it
     *
     * @param moduleName The name of the module
     * @return true if everything went fine, false if the module does not exist or is already actived
     */
    public boolean activateModule(final String moduleName) {
        if (!this.unloadedModules.contains(moduleName)) {
            // Module either doesn't exist or is already loaded
            return false;
        }

        LOG.info(String.format("Activating module \"%s\".", moduleName));

        LOG.debug("Removing module from unloaded list");
        this.unloadedModules.remove(moduleName);

        LOG.debug("Removing module from unloaded JSON array");
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
        for (int i = 0; i < jsonUnloadedModules.length(); i++) {
            if (jsonUnloadedModules.getString(i).equals(moduleName)) {
                jsonUnloadedModules.remove(i);
            }
        }
        this.saveConfig();

        LOG.info(String.format("Reloading modules to include %s", moduleName));
        this.loadModules();

        // Everything went fine
        return true;
    }

    /**
     * Deactivate a module so the bot loads it
     *
     * @param moduleName The name of the module
     * @return true if everything went fine, false if the module does not exist or is already deactivated
     */
    public boolean deactivateModule(final String moduleName) {
        if (!this.loadedModules.containsKey(moduleName)) {
            // Module either doesn't exist or is already unloaded
            return false;
        }

        LOG.info(String.format("Deactivating module \"%s\".", moduleName));

        // Unregister module from EventListener
        final Object moduleObject = loadedModules.get(moduleName);
        final Class<?> moduleClass = moduleObject.getClass();

        final CommandModule moduleAnnotation = moduleClass.getDeclaredAnnotationsByType(CommandModule.class)[0];
        if (!moduleAnnotation.commandOnly()) {
            LOG.debug("Unregistering module from EventListener");
            final EventDispatcher dispatcher = this.client.getDispatcher();
            dispatcher.registerListener(moduleObject);
        }

        LOG.debug("Adding module to unloaded list");
        this.unloadedModules.add(moduleName);

        LOG.debug("Adding module to unloaded JSON array");
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
        jsonUnloadedModules.put(moduleName);
        this.saveConfig();

        LOG.info(String.format("Reloading modules to remove %s", moduleName));
        this.loadModules();

        // Everything went fine
        return true;
    }

    /**
     * Save the JSON file containing the basic bot configuration
     */
    private void saveConfig() {
        LOG.debug("Saving config file.");

        final String jsonOutput = this.configJSON.toString(4);
        IOUtil.writeToFile(CONFIG_PATH, jsonOutput);
    }

    /**
     * Save the JSON file containing the role configurations
     */
    public void saveRoles() {
        LOG.debug("Saving roles file.");

        final String jsonOutput = this.rolesJSON.toString(4);
        IOUtil.writeToFile(ROLES_PATH, jsonOutput);
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}
