package app_kvECS;

import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.util.*;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import logger.LogSetup;
import shared.ConsoleColors;

import org.apache.commons.cli.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;

import ecs.ECS;
import ecs.ECSNode;

/* ECSClient should focus on CLI interaction */
public class ECSClient implements IECSClient {

    private static Logger logger = Logger.getRootLogger();
    public boolean clientRunning = false; /* Represents the status of ECSClient not the ECS */
    public boolean ecsRunning = false; /* Represents the status of ECS */
    private ECS ecs;
    private static final String PROMPT = ConsoleColors.BLUE_BOLD_UNDERLINED + "eagerDB-service>" + ConsoleColors.RESET + " ";

    public ECSClient(String address, int port) {
        ecs = new ECS(address, port, logger);
        clientRunning = true;

        // Thread ecsThread = new Thread(new Runnable() {
        // @Override
        // public void run() {
        // ecs.run();
        // }
        // });

        // ecsThread.start();
    }

    public ECSClient(String address, int port, Logger logger) {
        this.logger = logger;
        ecs = new ECS(address, port, logger);
        clientRunning = true;
    }

    @Override
    public boolean start() {
        if (ecs == null)
            return false;
        if(!ecsRunning){
            System.out.println(ConsoleColors.PURPLE_BOLD_UNDERLINED + "[ECSClient] Starting ECS" + ConsoleColors.RESET);
            ecsRunning = ecs.start();
        } else{
            this.logger.info(ConsoleColors.PURPLE_BOLD_UNDERLINED + "[ECSClient] ECS already running" + ConsoleColors.RESET);
        }
        return ecsRunning;
    }

    public void run() {
        this.start();
        while (clientRunning) {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.print(PROMPT);
            try {
                this.handleUserCommands(br.readLine());
            } catch (IOException e) {
                this.shutdown();
                logger.error(ConsoleColors.RED_UNDERLINED + "Error: " + ConsoleColors.RESET, e);
            }
        }
    }

    @Override
    public boolean stop() {
        ecs.stop();
        ecsRunning = false;
        return true;
    }

    @Override
    public boolean shutdown() {
        ecs.shutdown();
        ecsRunning = false;
        return true;
    }

    @Override
    public ECSNode addNode(String cacheStrategy, int cacheSize) {
        return this.ecs.addNode(cacheStrategy, cacheSize);
    }

    public ECSNode addNode(String cacheStrategy, int cacheSize, int port) {
        return this.ecs.addNode(cacheStrategy, cacheSize, port);
    }

    @Override
    public Collection<ECSNode> addNodes(int count, String cacheStrategy, int cacheSize) {
        this.ecs.addNodes(count, cacheStrategy, cacheSize);
        return null;
    }

    @Override
    public Collection<ECSNode> setupNodes(int count, String cacheStrategy, int cacheSize) {
        return null;
    }

    @Override
    public boolean awaitNodes(int count, int timeout) throws Exception {
        return this.ecs.awaitNodes(count, timeout);
    }

    @Override
    public boolean removeNodes(Collection<String> nodeNames) {
        return this.ecs.removeNodes(nodeNames);
    }

    @Override
    public Map<String, ECSNode> getNodes() {
        return ecs.getNodes();
    }

    @Override
    public ECSNode getNodeByKey(String Key) {
        return ecs.getNodeByServerName(Key);
    }

    public void listNodes() {
        int counter = 1;
        for(Map.Entry<String, ECSNode> entry : this.ecs.nodes.entrySet()) {
            System.out.println(ConsoleColors.RED_UNDERLINED + counter + ". " + entry.getValue() + " " + entry.getValue().getNodeIdentifier() + ConsoleColors.RESET); 
            counter++; 
        }
    }

    public void setTesting(boolean testing) {
        ecs.setTesting(testing);
    }

    public ECS getECS() {
        return ecs;
    }

    private void displayHelperMessage() {
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("start", "Starts the ECS");
        commands.put("stop", "Stops the ECS");
        commands.put("quit", "Quits the ECS");
        commands.put("list", "List all available nodes on ECS");
        commands.put("addnode <cacheStrategy> <cacheSize>", "Adds a KVServer to the ECS");
        commands.put("addnodes <count> <cacheStrategy> <cacheSize>", "Adds multiple KVServers to the ECS.");
        commands.put("removenodes <nodename> <nodename> ...",
                "Removes specified nodes matching one or more nodenames.");
        commands.put("help", "Displays help information about the available commands.");

        // Print the usage information
        System.out.println(ConsoleColors.CYAN_BOLD_UNDERLINED + "Usage:" + ConsoleColors.RESET);
        for (Map.Entry<String, String> command : commands.entrySet()) {
            System.out.println(ConsoleColors.CYAN_BOLD_UNDERLINED + "\t" + command.getKey() + ConsoleColors.RESET);
            System.out.println(ConsoleColors.CYAN_BOLD_UNDERLINED + "\t\t" + command.getValue() + ConsoleColors.RESET);
        }
    }

    /* Helper Function to Evaluate User Command */
    /*
     * Recommandation: move logger messages like "success" to the function itself
     * not here for debugging.
     */
    private void handleUserCommands(String cmd) {
        String[] args = cmd.split("\\s+"); // split cmd into args
        Options options = new Options();

        if (args.length > 0) {
            switch (args[0]) {
                case "start":
                    this.start();
                    break;
                case "stop":
                    if (!this.ecsRunning) {
                        logger.info(ConsoleColors.RED_UNDERLINED + "[Error] ECSClient is not running in the first place." + ConsoleColors.RESET);
                        break;
                    }

                    this.stop();
                    break;
                case "quit":
                    if (!this.ecsRunning) {
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] ECSClient is not running in the first place." + ConsoleColors.RESET);
                        break;
                    }

                    clientRunning = !this.shutdown();
                    break;
                case "list":
                    if (!this.ecsRunning) {
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] ECSClient is not running in the first place." + ConsoleColors.RESET);
                        break;
                    }

                    this.listNodes();
                    break;
                case "addnode":
                    if (!this.ecsRunning) {
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] ECSClient is not running in the first place." + ConsoleColors.RESET);
                        break;
                    }

                    if (args.length >= 3) {
                        try {
                            int cacheSize = Integer.parseInt(args[2]);
                            this.addNode(args[1], cacheSize);
                        } catch (NumberFormatException e) {
                            System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] Invalid cache size. Please enter a valid integer." + ConsoleColors.RESET);
                        }
                    } else
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] Insufficient arguments for addnode." + ConsoleColors.RESET);
                    break;
                case "addnodes":
                    if (!this.ecsRunning) {
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] ECSClient is not running in the first place." + ConsoleColors.RESET);
                        break;
                    }

                    if (args.length >= 4) {
                        try {
                            int count = Integer.parseInt(args[1]);
                            int cacheSize = Integer.parseInt(args[3]);
                            this.addNodes(count, args[2], cacheSize);
                        } catch (NumberFormatException e) {
                            System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] Invalid count or cache size. Please enter valid integers." + ConsoleColors.RESET);
                        }
                    } else
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] Insufficient arguments for addnodes." + ConsoleColors.RESET);
                    break;
                case "removenodes":
                    if (!this.clientRunning) {
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] ECSClient is not running in the first place." + ConsoleColors.RESET);
                        break;
                    }

                    if (args.length > 1)
                        this.removeNodes(Arrays.asList(Arrays.copyOfRange(args, 1, args.length)));
                    else
                        System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] Insufficient arguments for removenode." + ConsoleColors.RESET);
                    break;
                case "help":
                    this.displayHelperMessage();
                    break;
                case "clear":
                    System.out.print("\033[H\033[2J");  
                    System.out.flush();  
                    break;
                case "":
                    break;
                default:
                    this.displayHelperMessage();
                    break;
            }
        } else {
            System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] No command provided." + ConsoleColors.RESET);
        }
    }

    /* Add all options */
    public static void addCommandOptions(Options options) {
        Option help = new Option("h", "help", false, "display help");
        help.setRequired(false);
        options.addOption(help);

        Option address = new Option("a", "address", true, "address to start ecs service");
        address.setRequired(false);
        options.addOption(address);

        Option port = new Option("p", "port", true, "server port");
        port.setRequired(false);
        options.addOption(port);

        Option logFile = new Option("l", "logFile", true, "log file path");
        logFile.setRequired(false);
        options.addOption(logFile);

        Option logLevel = new Option("ll", "logLevel", true, "log level");
        logLevel.setRequired(false);
        options.addOption(logLevel);

        Option cli = new Option("c", "cli", false, "run cli");
        logLevel.setRequired(false);
        options.addOption(cli);
    }

    public static void main(String[] args) throws IOException {
        Options options = new Options();
        ECSClient.addCommandOptions(options);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (Exception e) {
            System.out.println(ConsoleColors.RED_UNDERLINED + e.getMessage() + ConsoleColors.RESET);
            formatter.printHelp("eagerDB-service.jar", options);
            System.exit(1);
            return;
        }

        if (cmd.hasOption("help")) {
            formatter.printHelp("eagerDB-service.jar", options);
            System.exit(0);
        }

        // set defaults for options
        String ecsAddress = cmd.getOptionValue("address", "127.0.0.1");
        String ecsPort = cmd.getOptionValue("port", String.valueOf(ECS.getDefaultECSPort()));
        String ecsLogFile = cmd.getOptionValue("logFile", "logs/ecs.log");
        String ecsLogLevel = cmd.getOptionValue("logLevel", "ALL");

        if (!LogSetup.isValidLevel(ecsLogLevel)) {
            ecsLogLevel = "ALL";
        }

        try {
            new LogSetup(ecsLogFile, LogSetup.getLogLevel(ecsLogLevel));
            logger.info(ConsoleColors.PURPLE_BOLD_UNDERLINED + "logger setup is complete." + ConsoleColors.RESET);
            ECSClient ecsClient = new ECSClient(ecsAddress, Integer.parseInt(ecsPort));
            ecsClient.run();

        } catch (Exception e) {
            System.out.println(ConsoleColors.RED_UNDERLINED + "[Error] Unable to setup logger: " + ConsoleColors.RESET);
            e.printStackTrace();
        }
    }
}
