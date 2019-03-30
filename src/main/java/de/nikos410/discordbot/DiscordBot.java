package de.nikos410.discordbot;

import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.framework.CommandWrapper;
import de.nikos410.discordbot.framework.ModuleWrapper;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.framework.annotations.CommandParameter;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
import de.nikos410.discordbot.modules.BotSetup;
import de.nikos410.discordbot.service.DiscordMessageService;
import de.nikos410.discordbot.service.impl.DiscordMessageServiceImpl;
import de.nikos410.discordbot.util.discord.Authorization;
import de.nikos410.discordbot.util.discord.UserUtils;
import de.nikos410.discordbot.util.io.IOUtil;
import org.json.JSONArray;
import org.json.JSONObject;
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
import sx.blah.discord.util.DiscordException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static de.nikos410.discordbot.framework.ModuleWrapper.ModuleStatus;

/**
 * The bots main class, containing most of the modular framework
 */
public class DiscordBot {
    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    private static final Path CONFIG_PATH = Paths.get("config/config.json");
    public final JSONObject rolesJSON;
    private static final Path ROLES_PATH = Paths.get("data/roles.json");
    public final JSONObject configJSON;

    private static final DiscordMessageService MESSAGE_SERVICE = new DiscordMessageServiceImpl();

    private final Map<String, ModuleWrapper> modules = new HashMap<>();
    private final Map<String, CommandWrapper> acticeCommands = new HashMap<>();

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
        discoverModules();
        loadModules();
    }

    public IDiscordClient getClient() {
        return this.client;
    }

    /**
     * Finds all classes in the package {@link de.nikos410.discordbot.modules} that extend {@link CommandModule}
     * and populates the module map using the module names as keys.
     */
    private void discoverModules() {
        // Search in package 'de.nikos410.discordbot.modules'
        final Reflections reflections = new Reflections("de.nikos410.discordbot.modules");
        // Find classes that are subtypes of CommandModule
        final Set<Class<? extends CommandModule>> foundModuleClasses = reflections.getSubTypesOf(CommandModule.class);

        for (Class<? extends CommandModule> currentModuleClass : foundModuleClasses) {
            final ModuleWrapper module = new ModuleWrapper(currentModuleClass);
            modules.put(module.getName(), module);
        }

    }

    /**
     * Loads modules from classes that are located in the package 'de.nikos410.discordbot.modules' and are annotated
     * with @CommandModule.
     */
    private void loadModules() {
        LOG.debug("Loading modules.");

        LOG.info("Found {} total module(s).", this.modules.size());

        // Load modules from all found classes
        for (ModuleWrapper wrapper : this.modules.values()) {
            try {
                loadModule(wrapper);
            } catch (Exception e) {
                LOG.error("Failed to load module " + wrapper.getName() + ".", e);
                wrapper.setStatus(ModuleStatus.FAILED);
            }
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
        for (ModuleWrapper wrapper : this.modules.values()) {
            final CommandModule module = wrapper.getInstance();
            if (wrapper.getStatus().equals(ModuleStatus.ACTIVE)) {
                module.initWhenReady();
            }

        }

        // Create command map
        this.makeCommandMap();
        LOG.info("{} module(s) with {} command(s) active.", this.modules.size(), this.acticeCommands.size());
    }

    /**
     * Load a module from a class specified in a {@link ModuleWrapper}
     *
     * @param wrapper The
     */
    private CommandModule loadModule(final ModuleWrapper wrapper) {
        LOG.debug("Loading module {}.", wrapper.getName());

        // Check if module is deactivated in config
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
        if (!wrapper.getModuleClass().equals(BotSetup.class)
                && jsonUnloadedModules.toList().contains(wrapper.getName())) {
            LOG.info("Module '{}' is deactivated. Skipping.", wrapper.getName());

            wrapper.setStatus(ModuleStatus.INACTIVE);
            return null;
        }

        LOG.debug("Loading module '{}'.", wrapper.getName());
        final CommandModule moduleInstance = instantiateModule(wrapper.getModuleClass());
        wrapper.setInstance(moduleInstance);

        if (moduleInstance == null) {
            // Module could not be created -> Add to failed modules
            wrapper.setStatus(ModuleStatus.FAILED);
            return null;
        }

        // Fill wrapper fields
        wrapper.setDisplayName(moduleInstance.getDisplayName());

        // Set bot field and run initialization for module
        moduleInstance.setBot(this);
        moduleInstance.setMessageService(MESSAGE_SERVICE);
        moduleInstance.init();

        // Register EventListener if needed
        if (moduleInstance.hasEvents()) {
            final EventDispatcher dispatcher = this.client.getDispatcher();
            dispatcher.registerListener(moduleInstance);
        }

        // Register all commands
        wrapper.setCommands(discoverCommands(wrapper));

        wrapper.setStatus(ModuleStatus.ACTIVE);

        LOG.info("Successfully loaded module '{}'.", wrapper.getName());
        return moduleInstance;
    }

    private void unloadModule(final ModuleWrapper wrapper) {
        LOG.debug("Unloading module '{}'.", wrapper.getName());

        wrapper.getInstance().shutdown();
        client.getDispatcher().unregisterListener(wrapper.getClass());

        wrapper.setStatus(ModuleStatus.INACTIVE);
        wrapper.setInstance(null);
        wrapper.setCommands(null);

        final JSONArray unloadedModulesJSON = configJSON.getJSONArray("unloadedModules");
        removeFromJSONArray(unloadedModulesJSON, wrapper.getName());
        unloadedModulesJSON.put(wrapper.getName());
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

    private List<CommandWrapper> discoverCommands(final ModuleWrapper moduleWrapper) {
        LOG.debug("Registering command(s) for module '{}'.", moduleWrapper.getName());

        final List<CommandWrapper> commands = new LinkedList<>();

        final Method[] allMethods = moduleWrapper.getModuleClass().getMethods();
        final List<Method> commandMethods = Arrays.stream(allMethods)
                .filter(module -> module.isAnnotationPresent(CommandSubscriber.class))
                .collect(Collectors.toList());

        for (final Method method : commandMethods) {
            // Register methods with the @CommandSubscriber as commands

            // All annotations of type CommandSubscriber declared for that Method. Should be exactly 1
            final CommandSubscriber annotation = method.getDeclaredAnnotationsByType(CommandSubscriber.class)[0];

            // Get command properties from annotation
            final String commandName = annotation.command();
            final String commandHelp = annotation.help();
            final boolean pmAllowed = annotation.pmAllowed();
            final PermissionLevel permissionLevel = annotation.permissionLevel();
            final boolean passContext = annotation.passContext();
            final boolean ignoreParameterCount = annotation.ignoreParameterCount();

            final int parameterCount = method.getParameterCount()-1;

            // Read parameter help from annotations
            final Map<String, String> parametersDescriptions = new HashMap<>();
            for (Parameter parameter : Arrays.stream(method.getParameters())
                    .filter(m -> m.getType() == String.class)
                    .collect(Collectors.toList())) {
                    final CommandParameter[] commandParameterAnnotations = parameter.getAnnotationsByType(CommandParameter.class);

                    if (commandParameterAnnotations.length < 1) {
                        LOG.warn(String.format("A parameter without a @CommandParameter annotation was found for the method %s in class %s. The help for this command will be incomplete.",
                                method.getName(), method.getDeclaringClass()));
                    }
                    else if (commandParameterAnnotations.length > 1) {
                        LOG.warn(String.format("Multiple @CommandParameter annotations were found for the method %s in class %s",
                                method.getName(), method.getDeclaringClass()));
                    }
                    else {
                        parametersDescriptions.put(commandParameterAnnotations[0].name(), commandParameterAnnotations[0].help());
                    }
            }

            if ((parameterCount >= 0 && parameterCount <= 5) || ignoreParameterCount) {
                final CommandWrapper commandWrapper = new CommandWrapper(commandName, commandHelp, parametersDescriptions, moduleWrapper, method, pmAllowed,
                        permissionLevel, parameterCount, passContext, ignoreParameterCount);

                commands.add(commandWrapper);

                LOG.debug("Saved command '{}'.", commandName);
            }
            else {
                LOG.warn("Method '{}' has an invalid number of arguments. Skipping", commandName);
            }

        }

        return commands;
    }

    /**
     * Populates global command map, maps a 'CommandWrapper' instance, containing a commands attributes, to each command.
     */
    private void makeCommandMap() {
        LOG.debug("Creating command map.");

        LOG.debug("Clearing old commands.");
        this.acticeCommands.clear();

        final List<ModuleWrapper> loadedModules = getLoadedModules();

        for (final ModuleWrapper moduleWrapper : loadedModules) {
            for (final CommandWrapper commandWrapper : moduleWrapper.getCommands()) {
                this.acticeCommands.put(commandWrapper.getName().toLowerCase(), commandWrapper);
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
        if (!acticeCommands.containsKey(commandName)) {
            return;
        }

        final CommandWrapper command = acticeCommands.get(commandName);

        LOG.info("User {} used command {}", UserUtils.makeUserString(message.getAuthor(), message.getGuild()), commandName);

        // The command was received in a PM but is only available on guilds
        if (message.getChannel().isPrivate() && !command.isPmAllowed()) {
            MESSAGE_SERVICE.sendMessage(message.getChannel(), "Dieser Befehl ist nicht in Privatnachrichten verfügbar!");
            LOG.info("CommandWrapper {} is not available in PMs.", commandName);
            return;
        }

        // Check if the user is allowed to use that command
        final PermissionLevel userPermissionLevel = this.getUserPermissionLevel(message.getAuthor(), message.getGuild());
        LOG.debug("Checking permissions. User: {} | Required: {}", userPermissionLevel, command.getPermissionLevel());

        if (userPermissionLevel.getLevel() < command.getPermissionLevel().getLevel()) {
            MESSAGE_SERVICE.sendMessage(message.getChannel(), String.format("Dieser Befehl ist für deine Gruppe (%s) nicht verfügbar.",
                    userPermissionLevel.getName()));
            LOG.info("User {} doesn't have the required permissions for using the command {}.",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    commandName);
            return;
        }

        final int expectedParameterCount = command.getExpectedParameterCount();
        final List<String> parameters = parseParameters(messageContent, commandName, expectedParameterCount, command.isPassContext());

        // Check if the user used the correct number of parameters
        if (parameters.size() < expectedParameterCount) {
            if (command.isIgnoreParameterCount()) {
                while (parameters.size() < expectedParameterCount) {
                    parameters.add(null);
                }
            }
            else {
                MESSAGE_SERVICE.sendMessage(message.getChannel(), String.format("Dieser Befehl benötigt mindestens %s Parameter! (Gegeben: %s)", expectedParameterCount, parameters.size()));
                LOG.info("Wrong number of arguments. Expected number: {} Actual number: {}",
                        expectedParameterCount, parameters.size());
                return;
            }
        }

        executeCommand(command, parameters, message);
    }

    /**
     * Invoke the method specified in a CommandWrapper with the parameters in a List
     *
     * @param command The instance containing the attributes of the command, specifically the method to invoke
     * @param parameters The parameters to use while invoking the method
     * @param message The message that triggered the command
     */
    private void executeCommand(final CommandWrapper command, final List<String> parameters, final IMessage message) {
        LOG.debug("Executing command {} with {} parameters.", command.getName(), parameters.size());

        final Method method = command.getMethod();
        final CommandModule instance = command.getModule().getInstance();

        try {
            switch (parameters.size()) {
                case 0:
                    method.invoke(instance, message);
                    break;
                case 1:
                    method.invoke(instance, message, parameters.get(0));
                    break;
                case 2:
                    method.invoke(instance, message, parameters.get(0), parameters.get(1));
                    break;
                case 3:
                    method.invoke(instance, message, parameters.get(0), parameters.get(1), parameters.get(2));
                    break;
                case 4:
                    method.invoke(instance, message, parameters.get(0), parameters.get(1), parameters.get(2),
                            parameters.get(3));
                    break;
                case 5:
                    method.invoke(instance, message, parameters.get(0), parameters.get(1), parameters.get(2),
                            parameters.get(3), parameters.get(4));
                    break;
                default:
                    throw new IllegalArgumentException("Command has an invalid number of arguments. This should never happen.");
            }
        }
        catch (IllegalAccessException | InvocationTargetException e) {
            final Throwable cause = e.getCause();

            LOG.error("Command '{}' could not be executed.", command.getName(), e.getCause());
            MESSAGE_SERVICE.errorNotify(cause.toString(), message.getChannel());
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
     *
     * @param event The event that triggers this method.
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
    public List<ModuleWrapper> getLoadedModules() {
        return modules.values()
                .stream()
                .filter(module -> module.getStatus().equals(ModuleStatus.ACTIVE))
                .collect(Collectors.toList());
    }

    /**
     * Returns the list containing the unloaded modules
     *
     * @return The list containing the unloaded modules
     */
    public List<ModuleWrapper> getUnloadedModules() {
        return modules.values()
                .stream()
                .filter(module -> module.getStatus().equals(ModuleStatus.INACTIVE))
                .collect(Collectors.toList());
    }

    /**
     * Returns the list containing the failed modules
     *
     * @return The list containing the failed modules
     */
    public List<ModuleWrapper> getFailedModules() {
        return modules.values()
                .stream()
                .filter(module -> module.getStatus().equals(ModuleStatus.FAILED))
                .collect(Collectors.toList());
    }

    public Map<String, CommandWrapper> getActiveCommands() {
        return acticeCommands;
    }

    /**
     * Activate a module so the bot loads it
     *
     * @param moduleName The name of the module
     * @return true if everything went fine, false if the module does not exist or is already actived
     */
    public ModuleWrapper activateModule(final String moduleName) {
        if (!modules.containsKey(moduleName)) {
            // Module does not exist
            return null;
        }

        final ModuleWrapper module = modules.get(moduleName);
        if (module.getStatus().equals(ModuleStatus.ACTIVE)) {
            // Module is already active
            return null;
        }

        LOG.debug("Removing module from unloaded JSON array");
        removeFromJSONArray(configJSON.getJSONArray("unloadedModules"), moduleName);
        this.saveConfig();

        LOG.info("Activating module '{}'.", moduleName);
        final CommandModule moduleInstance = loadModule(module);
        if (moduleInstance == null) {
            LOG.error("Module {} could not be loaded.", moduleName);
            return module;
        }

        // Run init tasks
        LOG.debug("Running init tasks.");
        moduleInstance.init();
        moduleInstance.initWhenReady(); // This method can only be executed by a command, so we don't have to check if the bot is ready

        LOG.info("Rebuilding command map to include commands from module '{}'.", moduleName);
        makeCommandMap();

        // Everything went fine
        return module;
    }

    /**
     * Deactivate a module so the bot unloads it
     *
     * @param moduleName The name of the module
     * @return true if everything went fine, false if the module does not exist or is already deactivated
     */
    public ModuleWrapper deactivateModule(final String moduleName) {
        if (!modules.containsKey(moduleName)) {
            // Module does not exist
            return null;
        }

        final ModuleWrapper module = modules.get(moduleName);
        if (module.getStatus().equals(ModuleStatus.INACTIVE)) {
            // Module is already inactive
            return null;
        }

        LOG.info("Deactivating module '{}'.", moduleName);
        unloadModule(module);

        LOG.info("Rebuilding command map to exclude commands from module \"{}\"", moduleName);
        makeCommandMap();

        // Everything went fine
        return module;
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
