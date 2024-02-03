package app_kvServer;

import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.log4j.Logger; // import Logger

import app_kvServer.ClientConnection;
import static app_kvServer.Caches.*;
import app_kvServer.IKVServer.CacheStrategy;

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
    private int port; // Port number
    private int cacheSize; // Cache size
    private CacheStrategy strategy; // Strategy (given by definition in ./IKVServer.java)
    private boolean running; // Check whether the server is currently running or not
    private Caches.Cache<String, String> cache;
    private static Logger logger = Logger.getRootLogger();
    private final String dirPath;

    public KVServer(int port, int cacheSize, String strategy) {
        // TODO Auto-generated method stub
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("Port number is out of range.");

        this.port = port; // Set port
        this.cacheSize = cacheSize; // Set cache size

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
        if (!dir.mkdir())
            new Exception("[Exception] Unable to create a directory.");
    }

    @Override
    public int getPort() {
        // TODO Auto-generated method stub
        return port; // Return port
    }

    @Override
    public String getHostname() {
        // TODO Auto-generated method stub
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
        // TODO Auto-generated method stub
        return strategy;
    }

    @Override
    public int getCacheSize() {
        // TODO Auto-generated method stub
        return cacheSize; // Return cache size
    }

    private File getStorageAddressOfKey(String key) {
        File file = new File(dirPath + File.separator + key);
        return file;
    }

    @Override
    public boolean inStorage(String key) {
        // TODO Auto-generated method stub
        File file = getStorageAddressOfKey(key);
        return file.exists();
    }

    @Override
    public boolean inCache(String key) {
        // TODO Auto-generated method stub
        return cache != null && cache.containsKey(key);
    }

    @Override
    public String getKV(String key) throws Exception {
        // TODO Auto-generated method stub
        if (!inStorage(key))
            throw new Exception("[Exception] Key not in storage.");

        if (!inCache(key)) {
            File path = getStorageAddressOfKey(key);
            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = reader.readLine()) != null)
                    contentBuilder.append(line).append("\n");
            }

            return contentBuilder.toString().trim();
        }

        return cache.get(key);
    }

    @Override
    public void putKV(String key, String value) throws Exception {
        // TODO Auto-generated method stub

        File file = new File(dirPath + File.separator + key);
        if (inStorage(key)) { // Key is already in storage
            try (FileWriter writer = new FileWriter(file, false)) { // overwrite
                writer.write(value);
            } catch (IOException e) {
                logger.error("[Error] Unable to write to file: " + file.getName(), e);
            }
        } else {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(value);
            } catch (IOException e) {
                logger.error("[Error] Unable to write to file: " + file.getName(), e);
            }
        }

        if (this.cache != null)
            cache.put(key, value);
    }

    // Helper Function to Monitor the State of Current KVs in Storage and Cache
    private void printStorageAndCache() {
        File dir = new File(dirPath);
        File[] db = dir.listFiles();

        System.out.println("Storage: ");
        if (db != null){
            for (File kv: db){
                System.out.print("\t" + "Key: " + kv.getName() +  ", "); // key
                try {
                    String content = new String(Files.readAllBytes(kv.toPath()));
                    System.out.println("Value: " + content); // value
                } catch (IOException e){
                    System.out.println("<Error>"); // could not access value for whatever reason
                }
            }
        } else {
            System.out.println("\tStorage is Empty.");
        }

        System.out.println("Cache: ");
        if (cache == null)
            System.out.println("\tCache is Empty.");
        for (Map.Entry<String, String> kv: cache.entrySet())
            System.out.println("\t" + "Key: " + kv.getKey() + ", Value: " + kv.getValue());
    }

    @Override
    public void clearCache() {
        // TODO Auto-generated method stub
        if (cache == null)
            return;

        for (String key : cache.keySet())
            cache.remove(key);
    }

    @Override
    public void clearStorage() {
        // TODO Auto-generated method stub
        File dir = new File(dirPath);
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                for (File kv : file.listFiles())
                    kv.delete();
            }
            file.delete();
        }
    }

    private boolean initServer() {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("[Success] Server is listening on port: " + serverSocket.getLocalPort());
            return true;
        } catch (IOException e) {
            logger.error("[Error] Server Socket cannot be opened: ");
            if (e instanceof BindException)
                logger.error("[Error] Port " + port + " is already bound.");
            return false;
        }
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        running = initServer();

        if (serverSocket != null){
            while (running){
                try {
                    Socket client = serverSocket.accept();
                    ClientConnection connection = new ClientConnection(client);
                    new Thread(connection).start();
                    logger.info("[Success] Connected to " + client.getInetAddress().getHostName() + " on port "
                            + client.getPort());
                } catch (IOException e){
                    logger.error("[Error] Unable to establish connection.\n", e);
                }
            }
        }
        logger.info("Server is stopped.");
    }

    @Override
    public void kill() {
        // TODO Auto-generated method stub
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("[Error] Unable to close socket on port: " + port, e);
        }
    
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("[Error] Unable to close socket on port: " + port, e);
        }
    }

    public static void main(String[] args) {
        KVServer server = new KVServer(20010, 3, "LRU");
        try {
            server.clearStorage();
            server.putKV("W", "indeed");
            server.putKV("key0", "value0");
            server.putKV("key1", "value1");
            server.printStorageAndCache();
            server.putKV("W", "bruh");
            server.printStorageAndCache();
            server.putKV("key2", "value2");
            server.printStorageAndCache();
            server.putKV("eagagawgg", "srgseGgwse");
            System.out.println(server.getKV("W"));
            server.printStorageAndCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}