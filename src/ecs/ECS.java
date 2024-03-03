package ecs;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.*;

import logger.LogSetup;
import shared.messages.BasicKVMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import app_kvECS.ECSClient;
import app_kvServer.ClientConnection;

import org.apache.zookeeper.*;
/* 
    ECSClient should initialize ECS. 
    If needed, integrate zookeeper here.
*/

public class ECS {
    private static Logger logger;

    private static final int DEFAULT_ECS_PORT = 9999;

    private String address;
    private int port;

    private ServerSocket ecsSocket;

    JSONObject config;

    /*
     * Integrity Constraint:
     * IECSNode in availableNodes = values of nodes
     */
    private Map<String, IECSNode> nodes = new HashMap<>(); /* maps server name -> node */
    private ArrayList<IECSNode> availableNodes;

    public ECS(String address, int port, Logger logger) {
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("port is out of range.");

        this.address = address;
        this.port = port;
        this.logger = logger;

        try {
            JSONTokener tokener = new JSONTokener(new FileInputStream("./ecs_config.json"));
            this.config = new JSONObject(tokener);
        } catch (FileNotFoundException e) {
            System.err.println("Failed to find the ecs_config.json file.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An error occurred.");
            e.printStackTrace();
        }

        logger.info("ECS initialized at " + this.address + ":" + this.port);
        /* zookeeper if needed */
    }

    public ECS(String address, int port) { // no logger
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("port is out of range.");

        this.address = address;
        this.port = port;

        try {
            JSONTokener tokener = new JSONTokener(new FileInputStream("./ecs_config.json"));
            this.config = new JSONObject(tokener);
        } catch (FileNotFoundException e) {
            System.err.println("Failed to find the ecs_config.json file.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An error occurred.");
            e.printStackTrace();
        }

        logger.info("ECS initialized at " + this.address + ":" + this.port);
        /* zookeeper if needed */
    }

    public ECS(Logger logger) {
        try {
            JSONTokener tokener = new JSONTokener(new FileInputStream("./ecs_config.json"));
            this.config = new JSONObject(tokener);
        } catch (FileNotFoundException e) {
            System.err.println("Failed to find the ecs_config.json file.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An error occurred.");
            e.printStackTrace();
        }

        this.address = this.config.getJSONObject("ecs").getString("address");
        this.port = this.config.getJSONObject("ecs").getInt("port");
        this.logger = logger;

        logger.info("ECS initialized at " + this.address + ":" + this.port);

        /* zookeeper if needed */
    }

    public boolean start() {
        try {
            ecsSocket = new ServerSocket(port, 10, InetAddress.getByName(address));
            logger.info("ECS is listening at " + address + ":" + port);

            Thread serverConnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    ECS.this.acceptServerConnections();
                }
            });

            serverConnThread.start();

            return true;
        } catch (IOException e) {
            logger.error("ECS Socket cannot be opened: ");
            // Could be a connection binding issue from server side
            if (e instanceof BindException)
                logger.error("Port " + port + " at address " + address + " is already bound.");
            return false;
        }
    }

    public void acceptServerConnections() {
        // logger.info("<<< INSERT NEW ECS-SERVER CONNECTION HERE >>>");
        JSONArray servers = config.getJSONArray("servers");

        for (int i = 0; i < servers.length(); i++) {
            List<String> command = new ArrayList<>();

            JSONObject server = servers.getJSONObject(i);
            int port = server.getInt("port");
            int cacheSize = server.getInt("cacheSize");
            String strategy = server.getString("strategy");
            
            try {
                command.add("java");
                command.add("-jar");
                command.add("m2-server.jar");
                command.add("-p");
                command.add(String.valueOf(port));
                command.add("-cs");
                command.add(String.valueOf(cacheSize));
                command.add("-s");
                command.add(strategy);
                command.add("-eh");
                command.add(this.address);
                command.add("-ep");
                command.add(String.valueOf(this.port));

                logger.info("Executing command: " + String.join(" ", command));

                ProcessBuilder builder = new ProcessBuilder(command);
                builder.inheritIO(); // forwards the output of the process to the current Java process
                Process process = builder.start();
            } catch (IOException e) {
                logger.error("Failed to start server on port " + port, e);
            }
        }

        if (ecsSocket != null) {
            while (true) {
                try {
                    logger.info("<<< INSERT NEW ECS-SERVER CONNECTION HERE >>>");
                    Socket kvServerSocket = ecsSocket.accept();
                    String serverAddress = kvServerSocket.getInetAddress().getHostAddress();
                    int serverPort = kvServerSocket.getPort();
                    String serverName = serverAddress + ":" + Integer.toString(serverPort);
                    ECSNode newNode = new ECSNode(serverName, serverAddress, serverPort, kvServerSocket);
                    nodes.put(serverName, newNode); // append to the table
                    setNodeAvailability(newNode, true); // set the node available

                    logger.info("Connected to " + kvServerSocket.getInetAddress().getHostName() + " on port "
                            + kvServerSocket.getPort());
                } catch (IOException e) {
                    logger.error("Unable to establish connection.\n", e);
                }
            }
        }
    }

    public void shutdown() {
        try {
            ecsSocket.close();
        } catch (IOException e) {
            logger.error("Unable to close socket at " + address + ":" + port, e);
        }
    }

    public void stop() {
        try {
            for (Map.Entry<String, IECSNode> entry : nodes.entrySet()) {
                ECSNode node = (ECSNode) entry.getValue();
                nodes.remove(entry);
                node.closeConnection();
            }
        } catch (Exception e) {
            logger.error("Error closing connection", e);
        }

        this.shutdown();
    }

    public boolean initServer(ECSNode newServer, String cacheStrategy, int cacheSize) {
        try {
            newServer.setCacheStrategy(cacheStrategy);
            newServer.setCacheSize(cacheSize);
            this.setNodeAvailability(newServer, false);
            return true;
        } catch (Exception e) {
            logger.error("Can't initialize server: ", e);
        }
        return false;
    }

    /*
     * Set the node's availability to true (in availableNodes) or false (rm from availableNodes)
     */
    private void setNodeAvailability(String nodeIdentifier, boolean isAvailable) {
        if (nodes.containsKey(nodeIdentifier)) {
            ECSNode node = (ECSNode) nodes.get(nodeIdentifier);
            if (isAvailable && !availableNodes.contains(node)) {
                availableNodes.add(node);
                availableNodes.remove(node);
            }
            else 
                availableNodes.remove(node);
        }
    }
    private void setNodeAvailability(ECSNode node, boolean isAvailable) {
       if (isAvailable && !availableNodes.contains(node)) {
           availableNodes.add(node);
           availableNodes.remove(node);
       } else
           availableNodes.remove(node);
    }

    public Collection<IECSNode> addNodes(int count, String cacheStrategy, int cacheSize) {
        if (count > availableNodes.size()) {
            throw new IllegalArgumentException("Not enough available servers to fulfill the request.");
        }

        Collection<IECSNode> addedNodes = new ArrayList<>();

        for (int i = 0; i < count; ++i) {
            /* Select and remove a server from the pool */
            IECSNode newServer = availableNodes.get(0);

            if (initServer((ECSNode) newServer, cacheStrategy, cacheSize))
                addedNodes.add(newServer);

            // update Zookeeper or another coordination service here if needed
        }

        // Rebalance the key space among all nodes
        // rebalanceKeyspace();

        return addedNodes;
    }

    public Collection<IECSNode> setupNodes(int count, String cacheStrategy, int cacheSize) {
        // TODO
        return null;
    }

    public boolean awaitNodes(int count, int timeout) throws Exception {
        // TODO
        return false;
    }

    public boolean removeNodes(Collection<String> nodeNames) {
        boolean removedAll = true;

        for (String nodeName : nodeNames) {
            ECSNode removedNode = (ECSNode) nodes.remove(nodeName);

            // Node not found in the server name to ECSNode map
            if (removedNode == null) {
                removedAll = false;
                continue;
            }

            try {
                removedNode.closeConnection();
            } catch (Exception e) {
                logger.error("Error closing connection with server " + nodeName, e);
            }
        }

        return removedAll;
    }

    public Map<String, IECSNode> getNodes() {
        return this.nodes;
    }

    public IECSNode getNodeByServerName(String serverName) {
        return nodes.get(serverName);
    }

    public static int getDefaultECSPort() {
        return DEFAULT_ECS_PORT;
    }
}
