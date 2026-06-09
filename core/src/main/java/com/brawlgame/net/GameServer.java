package com.brawlgame.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Bare-bones TCP game server for local 1-v-1 matches.
 *
 * <p>Tries to bind on {@value #GAME_PORT}. If that port is already in use it automatically steps
 * through up to 20 consecutive ports (7777–7796) until an open one is found.
 *
 * <p>State / input lines use a simple text protocol:
 * <pre>
 *   STATE &lt;px&gt; &lt;py&gt; &lt;pz&gt; &lt;ph&gt; &lt;pf&gt; &lt;pe&gt; &lt;rx&gt; &lt;ry&gt; &lt;rz&gt; &lt;rh&gt; &lt;rf&gt; &lt;re&gt;
 *   INPUT &lt;forward&gt; &lt;back&gt; &lt;left&gt; &lt;right&gt; &lt;jump&gt; &lt;sprint&gt; &lt;attack&gt; &lt;gun&gt; &lt;aimDeg&gt;
 * </pre>
 */
public final class GameServer {

    public static final int GAME_PORT = 7777;
    private static final int MAX_PORT_TRIES = 20;

    private ServerSocket serverSocket;
    private Socket       clientSocket;
    private PrintWriter  out;
    private BufferedReader in;
    private int actualPort = GAME_PORT;

    private volatile String  lastClientInput = "";
    private volatile boolean connected = false;
    private volatile boolean closed    = false;

    private final LanDiscovery discovery = new LanDiscovery();

    /** Returns the port that was successfully bound (may differ from {@value #GAME_PORT}). */
    public int getPort() { return actualPort; }

    /**
     * Start listening (non-blocking — opens sockets then returns).
     * Automatically finds the next free port if the default is already in use.
     */
    public void open() throws java.io.IOException {
        // Find an available port, stepping from GAME_PORT upward.
        for (int attempt = 0; attempt < MAX_PORT_TRIES; attempt++) {
            try {
                serverSocket = new ServerSocket(GAME_PORT + attempt);
                actualPort = GAME_PORT + attempt;
                break;
            } catch (BindException e) {
                if (attempt == MAX_PORT_TRIES - 1) throw e; // give up after max tries
            }
        }

        discovery.startBroadcast(actualPort);

        Thread accept = new Thread(() -> {
            try {
                clientSocket = serverSocket.accept();
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                connected = true;
                discovery.stopBroadcast();
                String line;
                while (!closed && (line = in.readLine()) != null) {
                    lastClientInput = line;
                }
            } catch (Exception ignored) {
            } finally {
                connected = false;
            }
        }, "mcbrawl-accept");
        accept.setDaemon(true);
        accept.start();
    }

    public boolean isClientConnected() { return connected; }

    public void sendState(String stateLine) {
        if (out != null) out.println(stateLine);
    }

    public String pollClientInput() {
        String s = lastClientInput;
        lastClientInput = "";
        return s;
    }

    public void close() {
        closed = true;
        connected = false;
        discovery.stopBroadcast();
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }
}
