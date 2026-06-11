package com.brawlgame.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Bare-bones TCP game client that connects to a {@link GameServer}.
 *
 * <p>Runs a background read thread; the GL thread calls {@link #pollServerState()} each frame
 * to get the latest STATE line, and {@link #sendInput(String)} to push input.
 */
public final class GameClient {

    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;

    private volatile String  lastServerState = "";
    private volatile boolean connected = false;
    private volatile boolean closed    = false;

    /**
     * Attempt a connection (blocks briefly — call from a background thread or before the GL loop).
     * Returns {@code true} on success.
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            Thread readLoop = new Thread(() -> {
                try {
                    String line;
                    while (!closed && (line = in.readLine()) != null) {
                        lastServerState = line;
                    }
                } catch (Exception ignored) {
                } finally {
                    connected = false;
                }
            }, "mcbrawl-client-read");
            readLoop.setDaemon(true);
            readLoop.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isConnected() { return connected; }

    public void sendInput(String inputLine) {
        if (out != null) out.println(inputLine);
    }

    /** Returns the last STATE line from the server, or {@code ""} if none. */
    public String pollServerState() {
        String s = lastServerState;
        lastServerState = "";
        return s;
    }

    public void close() {
        closed = true;
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}
