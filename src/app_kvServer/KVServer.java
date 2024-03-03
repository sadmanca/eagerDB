package app_kvServer;

import java.io.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.ServerSocket;
import java.net.Socket;
import logger.LogSetup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger; // import Logger
import org.apache.commons.cli.*;

import app_kvServer.Caches.LRUCache;
import app_kvServer.ClientConnection;
import app_kvServer.IKVServer.CacheStrategy;
import shared.messages.KVMessage;
import shared.messages.KVMessage.StatusType;
import shared.*;
import static app_kvServer.Caches.*;

import ecs.ECS;
import ecs.ECSHashRing;
import ecs.ECSNode;

public class KVServer implements IKVServer {
    /**
     * Start KV Server at given port
     * 
     * @param port      given port for storage server to operate
     * @param cacheSize specifies how many key-value pairs the server is allowed
     *                  to keep in-memory
     * @param strategy  specifies the cache replacement strategy in case the cache
     *                  is full and there is a GET- or PUT-request on a key that is
     *                  currently not contained in the cache. Options are "FIFO",
     *                  "LRU",
     *                  and "LFU".
     */

    private ServerSocket serverSocket; // Socket IPC
    private Socket clientSocket;
    private static final List<ClientConnection> connections = new CopyOnWriteArrayList<>();

    private int port; // Port number
    private int cacheSize; // Cache size
    private CacheStrategy strategy; // Strategy (given by definition in ./IKVServer.java)
    private boolean running; // Check whether the server is currently running or not
    private Caches.Cache<String, String> cache;

    private KVMessage.StatusType status;

    private static Logger logger = Logger.getRootLogger();
    
    private final String dirPath;
    private String ecsHost = null;
    private int ecsPort = -1;
    private Socket ecsSocket;

    /* Meta Data */
    private ECSHashRing hashRing = null;
    private ECSNode metadata = null;

    public KVServer(int port, int cacheSize, String strategy) {
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("port is out of range.");
        if (cacheSize < 0)
            throw new IllegalArgumentException("cacheSize is out of range.");

        this.port = port; // Set port
        this.cacheSize = cacheSize; // Set cache size
        this.status = KVMessage.StatusType.SERVER_ACTIVE;

        if (strategy == null) {
            this.strategy = CacheStrategy.None;
            this.cache = null;
        } else {
            switch (strategy) { // Set cache strategy
                case "LRU":
                    this.strategy = CacheStrategy.LRU;
                    this.cache = new Caches.LRUCache(this.cacheSize);
                    break;
                case "LFU":
                    this.strategy = CacheStrategy.LFU;
                    this.cache = new Caches.LFUCache(this.cacheSize);
                    break;
                case "FIFO":
                    this.strategy = CacheStrategy.FIFO;
                    this.cache = new Caches.FIFOCache(this.cacheSize);
                    break;
                default:
                    this.strategy = CacheStrategy.None;
                    this.cache = null;
            }
        }

        dirPath = System.getProperty("user.dir") + File.separator + "db";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            try {
                if (!dir.mkdir()) {
                    throw new Exception("Unable to create a directory.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                KVServer.this.run();
            }
        });

        serverThread.start(); // Start the thread
    }

    public KVServer(int port, int cacheSize, String strategy, String dbPath) {
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("port is out of range.");
        if (cacheSize < 0)
            throw new IllegalArgumentException("cacheSize is out of range.");

        this.port = port; // Set port
        this.cacheSize = cacheSize; // Set cache size
        this.status = KVMessage.StatusType.SERVER_ACTIVE;
    
        if (strategy == null) {
            this.strategy = CacheStrategy.None;
            this.cache = null;
        } else {
            switch (strategy) { // Set cache strategy
                case "LRU":
                    this.strategy = CacheStrategy.LRU;
                    this.cache = new Caches.LRUCache(this.cacheSize);
                    break;
                case "LFU":
                    this.strategy = CacheStrategy.LFU;
                    this.cache = new Caches.LFUCache(this.cacheSize);
                    break;
                case "FIFO":
                    this.strategy = CacheStrategy.FIFO;
                    this.cache = new Caches.FIFOCache(this.cacheSize);
                    break;
                default:
                    this.strategy = CacheStrategy.None;
                    this.cache = null;
            }
        }

        dirPath = System.getProperty("user.dir") + File.separator + dbPath;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            try {
                if (!dir.mkdir()) {
                    throw new Exception("Unable to create a directory.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                KVServer.this.run();
            }
        });

        serverThread.start(); // Start the thread
    }

    public KVServer(int port, int cacheSize, String strategy, String dbPath, String ecsHost, int ecsPort) {
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("port is out of range.");
        if (cacheSize < 0)
            throw new IllegalArgumentException("cacheSize is out of range.");

        this.port = port; // Set port
        this.cacheSize = cacheSize; // Set cache size
        this.status = KVMessage.StatusType.SERVER_ACTIVE;
        this.ecsHost = ecsHost;
        this.ecsPort = ecsPort;

        if (strategy == null) {
            this.strategy = CacheStrategy.None;
            this.cache = null;
        } else {
            switch (strategy) { // Set cache strategy
                case "LRU":
                    this.strategy = CacheStrategy.LRU;
                    this.cache = new Caches.LRUCache(this.cacheSize);
                    break;
                case "LFU":
                    this.strategy = CacheStrategy.LFU;
                    this.cache = new Caches.LFUCache(this.cacheSize);
                    break;
                case "FIFO":
                    this.strategy = CacheStrategy.FIFO;
                    this.cache = new Caches.FIFOCache(this.cacheSize);
                    break;
                default:
                    this.strategy = CacheStrategy.None;
                    this.cache = null;
            }
        }

        dirPath = System.getProperty("user.dir") + File.separator + dbPath;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            try {
                if (!dir.mkdir()) {
                    throw new Exception("Unable to create a directory.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                KVServer.this.run();
            }
        });

        serverThread.start(); // Start the thread
    }

    public void setHashRing(ECSHashRing hashRing) {
        this.hashRing = hashRing;
    }

    public ECSHashRing getHashRing() {
        return hashRing;
    }

    public void setMetadata(ECSNode metadata) {
        this.metadata = metadata;
    }

    public ECSHashRing getMetadata() {
        return hashRing;
    }

    @Override
    public int getPort() {
        return port; // Return port
    }

    @Override
    public String getHostname() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            return inetAddress.getHostName(); // Return hostname
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "Unknown Host";
        }
    }

    @Override
    public CacheStrategy getCacheStrategy() {
        return strategy; // Return strategy
    }

    @Override
    public int getCacheSize() {
        return cacheSize; // Return cache size
    }

    private File getStorageAddressOfKey(String key) {
        File file = new File(dirPath + File.separator + key);
        return file;
    }

    @Override
    public boolean inStorage(String key) {
        File file = getStorageAddressOfKey(key);
        return file.exists();
    }

    @Override
    public boolean inCache(String key) {
        return cache != null && cache.containsKey(key);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("\'", "\\'")
                .replace("\"", "\\\"")
                .replace(" ", "\\ ");
    }

    public static String unescape(String s) {
        return s.replace("\\\\", "\\")
                .replace("\\t", "\t")
                .replace("\\b", "\b")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\f", "\f")
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\ ", " ");
    }

    @Override
    public String getKV(String key) throws Exception {
        if (!inStorage(escape(key)))
            throw new Exception("tuple not found");

        if (!inCache(escape(key))) {
            File path = getStorageAddressOfKey(escape(key));
            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = reader.readLine()) != null)
                    contentBuilder.append(line).append("\n");
            }

            return contentBuilder.toString().trim();
        }

        String value = cache.get(escape(key));
        if (value == null)
            throw new Exception("tuple not found");

        return value;
    }

    @Override
    public synchronized StatusType putKV(String key, String value) throws Exception {
        if (value.equals(""))
            throw new Exception("unable to delete tuple");

        File file = new File(dirPath + File.separator + escape(key));

        if (value.equals("null")) {
            File fileToDel = new File(dirPath, escape(key));
            if (!fileToDel.exists() || fileToDel.isDirectory() || !fileToDel.delete())
                throw new Exception("unable to delete tuple");

            cache.remove(escape(key));

            return StatusType.DELETE_SUCCESS;
        }

        if (inStorage(escape(key))) { // Key is already in storage (i.e. UPDATE)
            try (FileWriter writer = new FileWriter(file, false)) { // overwrite
                writer.write(value);
                if (this.cache != null)
                    cache.put(escape(key), value);
            }

            return StatusType.PUT_UPDATE;
        }

        // Key is not in storage (i.e. PUT)
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(value);
            if (this.cache != null)
                cache.put(escape(key), value);
        }
        return StatusType.PUT_SUCCESS;
    }

    /*
     * Helper Function to Monitor the State of Current KVs in Storage and Cache
     */
    public void printStorageAndCache() {
        File dir = new File(dirPath);
        File[] db = dir.listFiles();

        System.out.println("Storage: ");
        if (db == null || db.length == 0)
            System.out.println("\tStorage is Empty.");
        else {
            for (File kv : db) {
                System.out.print("\t" + "Key: " + kv.getName() + ", "); // key
                try {
                    String content = new String(Files.readAllBytes(kv.toPath()));
                    System.out.println("Value: " + content); // value
                } catch (IOException e) {
                    System.out.println("<Error>"); // could not access value for whatever reason
                }
            }
        }

        System.out.println("Cache: ");
        if (cache == null || cache.size() == 0)
            System.out.println("\tCache is Empty.");
        for (Map.Entry<String, String> kv : cache.entrySet())
            System.out.println("\t" + "Key: " + kv.getKey() + ", Value: " + kv.getValue());

        for (int i = 0; i < 40; ++i) // Divider for readability
            System.out.print("-");
        System.out.println();
    }

    @Override
    public void clearCache() {
        if (cache == null)
            return;

        cache.removeAll();
    }

    @Override
    public void clearStorage() {
        File dir = new File(dirPath);
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                for (File kv : file.listFiles())
                    kv.delete();
            }
            file.delete();
        }
    }

    public void connectECS() {
        if (ecsHost != null && ecsPort > -1) {     
            try {
                ecsSocket = new Socket(ecsHost, ecsPort);
                logger.info("Connected to ECS at " + ecsHost + " on port " + ecsPort);
    
                ObjectInputStream in = new ObjectInputStream(ecsSocket.getInputStream());
                hashRing = (ECSHashRing) in.readObject();
                in.close();

                metadata = hashRing.getNodeForKey(getHostname() + ":" + getPort());
                
            } catch (Exception e) {
                System.err.println("Error connecting to ECS");
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void run() {
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            if (ecsHost != null && ecsPort >= 0)
                logger.info("Started server listening on port: " + serverSocket.getLocalPort() + "; cache size: "
                        + cacheSize + "; cache strategy: " + strategy + "; ECS set to: " + ecsHost + ":" + ecsPort);            
        } catch (IOException e) {
            logger.error("Server Socket cannot be opened: ");
            if (e instanceof BindException)
                logger.error("Port " + port + " is already bound.");
            return;
        }

        connectECS();

        if (serverSocket != null) {
            while (running) {
                try {
                    clientSocket = serverSocket.accept();
                    ClientConnection connection = new ClientConnection(this, clientSocket);
                    connections.add(connection);
                    new Thread(connection).start();
                    logger.info("Connected to " + clientSocket.getInetAddress().getHostName() + " on port "
                            + clientSocket.getPort());
                } catch (IOException e) {
                    logger.error("Unable to establish connection.\n", e);
                }
            }
        }
        logger.info("Server is stopped.");
        close();
    }

    @Override
    public void kill() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Unable to close socket on port: " + port, e);
        }

    }

    @Override
    public void close() {
        for (ClientConnection conn : connections)
            conn.close();
        clearCache();
        // clearStorage(); // are not supposed to clear storage on server start/quit
        kill();
    }

    public List<String> getKeysToTransfer(BigInteger start, BigInteger end) {
        List<String> keys = new ArrayList<>();
        File dir = new File(dirPath);
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                for (File kv : file.listFiles()) {
                    if (metadata.isKeyInRange(kv.getName())) {
                        keys.add(kv.getName());
                    }
                }
            }
        }
        return keys;
    }

    public static void main(String[] args) throws IOException {
        Options options = new Options();

        Option help = new Option("h", "help", false, "display help");
        help.setRequired(false);
        options.addOption(help);

        Option address = new Option("a", "address", true, "address to listen to");
        address.setRequired(false);
        options.addOption(address);

        Option port = new Option("p", "port", true, "server port");
        port.setRequired(false);
        options.addOption(port);

        Option cacheSize = new Option("cs", "cacheSize", true, "cache size");
        cacheSize.setRequired(false);
        options.addOption(cacheSize);

        Option cacheStrategy = new Option("s", "cacheStrategy", true, "cache strategy");
        cacheStrategy.setRequired(false);
        options.addOption(cacheStrategy);

        Option logFile = new Option("l", "logFile", true, "log file path");
        logFile.setRequired(false);
        options.addOption(logFile);

        Option logLevel = new Option("ll", "logLevel", true, "log level");
        logLevel.setRequired(false);
        options.addOption(logLevel);

        Option dataPath = new Option("d", "dir", true, "directory to persist data");
        dataPath.setRequired(false);
        options.addOption(dataPath);

        Option ecsHostAddress = new Option("eh", "ecsHostAddress", true, "ecs host");
        ecsHostAddress.setRequired(false);
        options.addOption(ecsHostAddress);

        Option ecsPortAddress = new Option("ep", "ecsPortAddress", true, "ecs port");
        ecsPortAddress.setRequired(false);
        options.addOption(ecsPortAddress);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
            return;
        }

        if (cmd.hasOption("help")) {
            formatter.printHelp("m2-server.jar", options);
            System.exit(0);
        }

        // set defaults for options
        String serverAddress = cmd.getOptionValue("address", "localhost");
        String serverPort = (cmd.getOptionValue("port", "20010"));
        String serverCacheSize = (cmd.getOptionValue("cacheSize", "10"));
        String serverCacheStrategy = (cmd.getOptionValue("cacheStrategy", "FIFO"));
        String serverLogFile = cmd.getOptionValue("logFile", "logs/server.log");
        String serverLogLevel = cmd.getOptionValue("logLevel", "ALL");
        String dbPath = cmd.getOptionValue("dir", "db");
        String ecsHost = cmd.getOptionValue("ecsHostAddress", ECS.getDefaultECSAddr());
        String ecsPort = cmd.getOptionValue("ecsPortAddress", String.valueOf(ECS.getDefaultECSPort()));

        if (!LogSetup.isValidLevel(serverLogLevel)) {
            serverLogLevel = "ALL";
        }

        try {
            new LogSetup(serverLogFile, LogSetup.getLogLevel(serverLogLevel));
            KVServer server;
            
            if (ecsHost != null && ecsPort != null) // Utilizing ECS
                server = new KVServer(Integer.parseInt(serverPort), Integer.parseInt(serverCacheSize), serverCacheStrategy, dbPath, ecsHost, Integer.parseInt(ecsPort));
            else
                server = new KVServer(Integer.parseInt(serverPort), Integer.parseInt(serverCacheSize), serverCacheStrategy, dbPath);
            // server.clearStorage(); // are not supposed to clear storage
            // on server start/quit
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}