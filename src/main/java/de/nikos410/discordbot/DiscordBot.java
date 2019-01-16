package de.nikos410.discordbot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.*;
import de.nikos410.discordbot.framework.annotations.*;
import de.nikos410.discordbot.modules.BotSetup;
import de.nikos410.discordbot.util.discord.*;
import de.nikos410.discordbot.util.io.IOUtil;

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
import sx.blah.discord.util.DiscordException;

/**
 * The bots main class, containing most of the modular framework
 */
public class DiscordBot {
    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    private static final Path CONFIG_PATH = Paths.get("config/config.json");
    public final JSONObject rolesJSON;
    private static final Path ROLES_PATH = Paths.get("data/roles.json");
    public final JSONObject configJSON;

    private final Map<String, Class<? extends CommandModule>> moduleClasses = new HashMap<>();
    private final Map<String, CommandModule> loadedModules = new HashMap<>();
    private final List<String> unloadedModules = new ArrayList<>();
    private final List<String> failedModules = new ArrayList<>();

    private final Map<String, Command> commands = new HashMap<>();

    private final String prefix;
    private final long ownerID;
    private IDiscordClient client;

    /**
     * Sets up the bot, loads configuration.
     */
    private DiscordBot() {
        // Read config file
        final String configFileContent = IOUtil.readFile(CONFIG_PATH);
        if (configFileContent == null) {
            throw new InitializationException("Could not read configuration file.", DiscordBot.class);
        }
        this.configJSON = new JSONObject(configFileContent);
        LOG.info("Loaded configuration with {} entries.", configJSON.keySet().size());

        // Read roles file
        final String rolesFileContent = IOUtil.readFile(ROLES_PATH);
        if (rolesFileContent == null) {
            throw new InitializationException("Could not read roles file.", DiscordBot.class);
        }
        this.rolesJSON = new JSONObject(rolesFileContent);
        LOG.info("Loaded roles file for {} guilds.", rolesJSON.keySet().size());

        // Get prefix and owner ID from config
        if (!configJSON.has("prefix")) {
            throw new InitializationException("No prefix configured.", DiscordBot.class);
        }
        this.prefix = configJSON.getString("prefix");
        if (!configJSON.has("owner")) {
            throw new InitializationException("No owner configured.", DiscordBot.class);
        }
        this.ownerID = configJSON.getLong("owner");

        // Get unloaded modules
        if (!configJSON.has("unloadedModules")) {
            throw new InitializationException("Could not find unloaded modules.", DiscordBot.class);
        }
        final JSONArray unloadedModulesJSON = this.configJSON.getJSONArray("unloadedModules");

        for (int i = 0; i < unloadedModulesJSON.length(); i++) {
            this.unloadedModules.add(unloadedModulesJSON.getString(i));
        }
    }

    private void start() {
        // Get token from config
        if (!configJSON.has("token")) {
            throw new InitializationException("No token configured.", DiscordBot.class);
        }
        final String token = configJSON.getString("token");
        // Authorize using token
        try {
            this.client = Authorization.createClient(token, true);
        }
        catch (DiscordException e) {
            throw new InitializationException("Could not log in client.", e, DiscordBot.class);
        }
        LOG.info("Bot authorized.");

        // Register Eventlistener
        try {
            this.client.getDispatcher().registerListener(this);
        }
        catch (NullPointerException e) {
            throw new InitializationException("Could not get EventDispatcher.", e, DiscordBot.class);
        }

        // Initialize Modules
        this.loadModules();
    }

    public IDiscordClient getClient() {
        return this.client;
    }

    /**
     * Loads modules from classes that are located in the package 'de.nikos410.discordbot.modules' and are annotated
     * with @CommandModule.
     */
    private void loadModules() {
        LOG.debug("Loading modules.");

        LOG.debug("Clearing old modules.");
        this.loadedModules.clear();

        findModules();
        LOG.info("Found {} total module(s).", this.moduleClasses.size());

        // Load modules from all found classes
        for (final Class<? extends CommandModule> moduleClass : moduleClasses.values()) {
            loadModule(moduleClass);
        }

        // Wait until the bot is ready to run initializations
        LOG.debug("Waiting until the bot is ready.");
        while (!client.isReady()) {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            }
            catch(InterruptedException e) {
                LOG.warn("Sleep was interrupted");
                Thread.currentThread().interrupt();
            }
        }

        LOG.debug("Bot is ready. Running Inits.");
        for (Map.Entry<String, CommandModule> entry : loadedModules.entrySet()) {
            LOG.debug("Running ready-Init for module {}.", entry.getKey());
            entry.getValue().initWhenReady();
        }

        // Create command map
        this.makeCommandMap();
        LOG.info("{} module(s) with {} command(s) active.", this.loadedModules.size(), this.commands.size());
    }

    /**
     * Finds all classes in the package {@link de.nikos410.discordbot.modules} that extend {@link CommandModule}
     * and populates the moduleClasses field using the class names as keys.
     *
     */
    private void findModules() {
        // Search in package 'de.nikos410.discordbot.modules'
        final Reflections reflections = new Reflections("de.nikos410.discordbot.modules");
        // Find classes that are subtypes of CommandModule
        final Set<Class<? extends CommandModule>> foundModuleClasses = reflections.getSubTypesOf(CommandModule.class);

        for (Class<? extends CommandModule> currentModuleClass : foundModuleClasses) {
            this.moduleClasses.put(currentModuleClass.getSimpleName(), currentModuleClass);
        }

    }

    /**
     * Loads a module from a class. The specified class has to be annotated with @CommandModule.
     *
     * @param moduleClass The class containing the module
     */
    private void loadModule(final Class<? extends CommandModule> moduleClass) {
        LOG.debug("Loading module information from class {}.", moduleClass);

        // Use class name as module name
        final String moduleName = moduleClass.getSimpleName();

        // First, shut down all instance of this module that are already loaded
        while(loadedModules.containsKey(moduleName)) {
            unloadModule(moduleName);
        }

        // Check if module is deactivated in config
        if (this.unloadedModules.contains(moduleName)) {
            // BotSetup Module must always be loaded
            if (moduleClass.equals(BotSetup.class)) {
                LOG.info("Module \"{}\" can not be deactivated. Loading anyways.",
                        moduleName);

                // Remove entry from unloaded list and JSON
                LOG.debug("Removing module from unloaded list.");
                unloadedModules.remove(moduleName);
                LOG.debug("Removing module from unloaded JSON array");
                final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
                for (int i = 0; i < jsonUnloadedModules.length(); i++) {
                    if (jsonUnloadedModules.getString(i).equals(moduleName)) {
                        jsonUnloadedModules.remove(i);
                    }
                }
                this.saveConfig();
            }
            else {
                LOG.info("Module \"{}\" is deactivated. Skipping.", moduleName);
                return;
            }
        }

        LOG.debug("Loading module \"{}\".", moduleName);
        final CommandModule moduleInstance = instantiateModule(moduleClass);

        if (moduleInstance == null) {
            // Module could not be created -> Add to failed modules
            if (!failedModules.contains(moduleName)) {
                failedModules.add(moduleName);
            }

            return;
        }

        // Set bot field and run initialization for module
        moduleInstance.setBot(this);
        moduleInstance.init();

        // Register EventListener if needed
        if (moduleInstance.hasEvents()) {
            final EventDispatcher dispatcher = this.client.getDispatcher();
            dispatcher.registerListener(moduleInstance);
        }

        loadedModules.put(moduleName, moduleInstance);
        failedModules.remove(moduleName);
        LOG.info("Successfully loaded module \"{}\".", moduleName);
    }

    private void unloadModule(final String moduleName) {
        if(!loadedModules.containsKey(moduleName)) {
            throw new IllegalArgumentException("Invalid module specified: " + moduleName);
        }

        final CommandModule module = loadedModules.get(moduleName);

        // Shutdown the module
        module.shutdown();
        // Unregister the module with the EventListener
        client.getDispatcher().unregisterListener(module.getClass());

        loadedModules.remove(moduleName);
        unloadedModules.remove(moduleName);
        unloadedModules.add(moduleName);
        removeFromJSONArray(configJSON.getJSONArray("unloadedModules"), moduleName);
    }

    /**
     * Creates an instance of a class containing a module.
     *
     * @param moduleClass The class containing the module
     * @return The created module instance
     */
    private CommandModule instantiateModule(final Class<? extends CommandModule> moduleClass) {
        try {
            LOG.debug("Instantiating module class \"{}\".", moduleClass.getName());
            return moduleClass.getConstructor().newInstance();
        }
        catch (InstantiationException | IllegalAccessException e) {
            LOG.warn("Something went wrong while instantiating class \"{}\". Skipping.", moduleClass.getName(), e);
            return null;
        }
        catch (InvocationTargetException e) {
            LOG.warn("Something went wrong while instantiating class \"{}\". Skipping.", moduleClass.getName(), e.getCause());
            return null;
        }
        catch (NoSuchMethodException e) {
            LOG.error("Could not instantiate module. Constructor does not exist.", e);
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

        for (final Map.Entry<String, CommandModule> entry : this.loadedModules.entrySet()) {
            final CommandModule module = entry.getValue();

            LOG.debug("Registering command(s) for module \"{}\".", entry.getKey());

            for (final Method method : module.getClass().getMethods()) {
                // Register methods with the @CommandSubscriber as commands
                if (method.isAnnotationPresent(CommandSubscriber.class)) {

                    // All annotations of type CommandSubscriber declared for that Method. Should be exactly 1
                    final CommandSubscriber[] annotations = method.getDeclaredAnnotationsByType(CommandSubscriber.class);

                    // Get command properties from annotation
                    final String command = annotations[0].command();
                    final boolean pmAllowed = annotations[0].pmAllowed();
                    final PermissionLevel permissionLevel = annotations[0].permissionLevel();
                    final int parameterCount = method.getParameterCount();
                    final boolean passContext = annotations[0].passContext();
                    final boolean ignoreParameterCount = annotations[0].ignoreParameterCount();

                    // At least 1 (message), max 6 (message + 5 parameter)
                    if ((parameterCount > 0 && parameterCount <= 6) || ignoreParameterCount) {
                        final Command cmd = new Command(module, method, pmAllowed, permissionLevel,
                                parameterCount-1, passContext, ignoreParameterCount);
                        this.commands.put(command.toLowerCase(), cmd);

                        LOG.debug("Registered command \"{}\".", command);
                    }
                    else {
                        LOG.warn("Command \"{}\" has an invalid number of arguments. Skipping", command);
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
            LOG.debug("Received PM from user with ID {}. Message length: {}",
                    event.getAuthor().getStringID(),
                    event.getMessage().getContent().length());
        }
        else {
            LOG.debug("Guild {}: Received message from user with ID {} in channel with ID {}. Message length: {}",
                    event.getGuild().getStringID(),
                    event.getAuthor().getStringID(),
                    event.getChannel().getStringID(),
                    event.getMessage().getContent().length());
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
            final Instant messageTimestamp = message.getTimestamp();
            final Instant editTimestamp = message.getEditedTimestamp().get();

            final long messageAge = messageTimestamp.until(editTimestamp, ChronoUnit.SECONDS);

            if (event.getChannel().isPrivate()) {
                LOG.debug("PM from user with ID {} was edited. Message length: {} | Message age: {} seconds",
                        event.getAuthor().getStringID(),
                        event.getMessage().getContent().length(),
                        messageAge);
            }
            else {
                LOG.debug("Guild {}: Message in channel with ID {} by user {} was edited. Message length: {} | Message age: {} seconds",
                        event.getGuild().getStringID(),
                        event.getAuthor().getStringID(),
                        event.getChannel().getStringID(),
                        event.getMessage().getContent().length(),
                        messageAge);
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

        LOG.info("User {} used command {}", UserUtils.makeUserString(message.getAuthor(), message.getGuild()), commandName);

        // The command was received in a PM but is only available on guilds
        if (message.getChannel().isPrivate() && !command.pmAllowed) {
            DiscordIO.sendMessage(message.getChannel(), "Dieser Befehl ist nicht in Privatnachrichten verfügbar!");
            LOG.info("Command {} is not available in PMs.", commandName);
            return;
        }

        // Check if the user is allowed to use that command
        final PermissionLevel userPermissionLevel = this.getUserPermissionLevel(message.getAuthor(), message.getGuild());
        LOG.debug("Checking permissions. User: {} | Required: {}", userPermissionLevel, command.permissionLevel);

        if (userPermissionLevel.getLevel() < command.permissionLevel.getLevel()) {
            DiscordIO.sendMessage(message.getChannel(), String.format("Dieser Befehl ist für deine Gruppe (%s) nicht verfügbar.",
                    userPermissionLevel.getName()));
            LOG.info("User {} doesn't have the required permissions for using the command {}.",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    commandName);
            return;
        }

        final int expectedParameterCount = command.expectedParameterCount;
        final boolean passContext = command.passContext;
        final boolean ignoreParameterCount = command.ignoreParameterCount;
        final List<String> parameters = parseParameters(messageContent, commandName, expectedParameterCount, passContext);

        // Check if the user used the correct number of parameters
        if (parameters.size() < expectedParameterCount) {
            if (ignoreParameterCount) {
                while (parameters.size() < expectedParameterCount) {
                    parameters.add(null);
                }
            }
            else {
                DiscordIO.sendMessage(message.getChannel(), String.format("Dieser Befehl benötigt mindestens %s Parameter! (Gegeben: %s)", expectedParameterCount, parameters.size()));
                LOG.info("Wrong number of arguments. Expected number: {} Actual number: {}",
                        expectedParameterCount, parameters.size());
                return;
            }
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
        LOG.debug("Executing command {} with {} parameters.", commandName, parameters.size());

        try {
            switch (parameters.size()) {
                case 0:
                    command.method.invoke(command.object, message);
                    break;
                case 1:
                    command.method.invoke(command.object, message, parameters.get(0));
                    break;
                case 2:
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1));
                    break;
                case 3:
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1), parameters.get(2));
                    break;
                case 4:
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1), parameters.get(2),
                            parameters.get(3));
                    break;
                case 5:
                    command.method.invoke(command.object, message, parameters.get(0), parameters.get(1), parameters.get(2),
                            parameters.get(3), parameters.get(4));
                    break;
                default:
                    LOG.error("Command \"{}\" has an invalid number of arguments. This should never happen.",
                            commandName);
                    DiscordIO.errorNotify("Befehl kann wegen einer ungültigen Anzahl an Argumenten nicht " +
                            "ausgeführt werden. Dies sollte niemals passieren!", message.getChannel());
            }
        }
        catch (IllegalAccessException | InvocationTargetException e) {
            final Throwable cause = e.getCause();

            LOG.error("Command \"{}\" could not be executed.", commandName, e.getCause());
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
    private List<String> parseParameters(final String messageContent, final String command, int parameterCount, boolean passContext) {
        final LinkedList<String> parameters = new LinkedList<>();

        // Remove prefix and command
        final String content = messageContent.substring(prefix.length() + command.length());

        // Remove leading whitespace
        int parameterStart = 0;
        while (parameterStart < content.length() && (content.charAt(parameterStart) == '\n' || content.charAt(parameterStart) == ' ')) {
            parameterStart++;
        }
        final String parameterContent = content.substring(parameterStart);   // The String containing only the parameters

        // Seperate the individual parameters
        final String[] contentParts = parameterContent.split("[\\t ]+");

        int contentPartIndex;
        for (contentPartIndex = 0; contentPartIndex < parameterCount; contentPartIndex++) {
            if (contentPartIndex < contentParts.length) {
                final String currentParameter = contentParts[contentPartIndex];
                if (!currentParameter.isEmpty()) {
                    parameters.add(currentParameter);
                }
            }
        }

        // If passContext is true, append possible additional words to last parameter
        if (passContext && contentPartIndex < contentParts.length && !parameters.isEmpty()) {
            final StringBuilder builder = new StringBuilder();
            final String last = parameters.removeLast();
            builder.append(last);


            while (contentPartIndex < contentParts.length) {
                builder.append(' ');
                builder.append(contentParts[contentPartIndex]);
                contentPartIndex++;
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
    public PermissionLevel getUserPermissionLevel(final IUser user, final IGuild guild) {
        // User is the configured owner of the bot
        if (user.getLongID() == this.ownerID) {
            return PermissionLevel.OWNER;
        }

        // No guild (maybe PM)
        if (guild == null) {
            return PermissionLevel.EVERYONE;
        }

        // If no roles are configured for this guild return the lowest level
        if (!rolesJSON.has(guild.getStringID())) {
            LOG.warn("Roles for guild {} (ID: {}) are not configured!", guild.getName(), guild.getStringID());
            return PermissionLevel.EVERYONE;
        }

        final JSONObject guildRoles = rolesJSON.getJSONObject(guild.getStringID());

        if (guildRoles.has("adminRole")) {
            final long adminRoleID = guildRoles.getLong("adminRole");
            if (UserUtils.hasRole(user, adminRoleID, guild)) {
                return PermissionLevel.ADMIN;
            }
        }
        else {
            LOG.warn("Admin role for guild {} (ID: {}) is not configured!", guild.getName(), guild.getStringID());
        }

        if (guildRoles.has("modRole")) {
            final long adminRoleID = guildRoles.getLong("modRole");
            if (UserUtils.hasRole(user, adminRoleID, guild)) {
                return PermissionLevel.ADMIN;
            }
        }
        else {
            LOG.warn("Moderator role for guild {} (ID: {}) is not configured!", guild.getName(), guild.getStringID());
        }

        return PermissionLevel.EVERYONE;
    }

    /**
     * Print out some text and change the playing-text when bot is ready
     */
    @EventSubscriber
    public void onReady(final ReadyEvent event) {
        LOG.info("[INFO] Bot ready. Prefix: {}", this.prefix);
        LOG.info("Add this bot to a server: https://discordapp.com/oauth2/authorize?client_id={}&scope=bot", client.getApplicationClientID());
        client.changePresence(StatusType.ONLINE, ActivityType.PLAYING, String.format("%shelp | WIP", this.prefix));
    }

    /**
     * Returns the map containing the loaded modules
     *
     * @return The map containing the loaded modules
     */
    public Map<String, CommandModule> getLoadedModules() {
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
     * Returns the list containing the names of the unloaded modules
     *
     * @return The list containing the names of the unloaded modules
     */
    public List<String> getFailedModules() {
        return failedModules;
    }

    /**
     * Activate a module so the bot loads it
     *
     * @param moduleName The name of the module
     * @return true if everything went fine, false if the module does not exist or is already actived
     */
    public boolean activateModule(final String moduleName) {
        if (!moduleClasses.containsKey(moduleName)) {
            // Module does not exist
            return false;
        }
        if (!unloadedModules.contains(moduleName) && !failedModules.contains(moduleName)) {
            // Module is not unloaded/failed already loaded
            return false;
        }

        LOG.debug("Removing module from unloaded and failed list, if present");
        this.unloadedModules.remove(moduleName);
        this.failedModules.remove(moduleName);

        LOG.debug("Removing module from unloaded JSON array");
        removeFromJSONArray(configJSON.getJSONArray("unloadedModules"), moduleName);
        this.saveConfig();

        LOG.info("Activating module \"{}\".", moduleName);
        loadModule(moduleClasses.get(moduleName));
        LOG.info("Rebuilding command map to include commands from module \"{}\"", moduleName);
        makeCommandMap();

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

        final CommandModule moduleInstance = loadedModules.get(moduleName);

        LOG.info("Deactivating module \"{}\".", moduleName);

        // Unregister module from EventListener if neccessary
        if (moduleInstance.hasEvents()) {
            LOG.debug("Unregistering module {} from EventListener", moduleName);
            final EventDispatcher dispatcher = client.getDispatcher();
            dispatcher.unregisterListener(moduleInstance);
        }

        LOG.debug("Adding module to unloaded list");
        this.unloadedModules.remove(moduleName);
        this.unloadedModules.add(moduleName);

        LOG.debug("Adding module to unloaded JSON array");
        final JSONArray jsonUnloadedModules = configJSON.getJSONArray("unloadedModules");
        removeFromJSONArray(jsonUnloadedModules, moduleName);
        jsonUnloadedModules.put(moduleName);
        saveConfig();

        LOG.debug("Shutting down and unloading module \"{}\"", moduleName);
        unloadModule(moduleName);

        LOG.info("Rebuilding command map to exclue commands from module \"{}\"", moduleName);
        makeCommandMap();

        // Everything went fine
        return true;
    }

    /**
     * Remove a value from a {@link JSONArray}
     *
     * @param array The JSONArray to remove the value from.
     * @param value The value to remove from the array.
     */
    private void removeFromJSONArray(final JSONArray array, final Object value) {
        for (int i = 0; i < array.length(); i++) {
            if (array.get(i).equals(value)) {
                array.remove(i);
            }
        }
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
        // Create an empty config file if it does not exist.
        if (!Files.exists(CONFIG_PATH)) {
            LOG.info("Creating sample config file at {}. Please fill in the appropriate values.", CONFIG_PATH);

            final JSONObject sampleConfig = new JSONObject();
            sampleConfig.put("owner", 165857945471418368L);
            sampleConfig.put("prefix", "%");
            sampleConfig.put("unloadedModules", new JSONArray());
            sampleConfig.put("token", "<your token>");

            IOUtil.writeToFile(CONFIG_PATH, sampleConfig.toString(4));

            return;
        }

        new DiscordBot().start();
    }
}
