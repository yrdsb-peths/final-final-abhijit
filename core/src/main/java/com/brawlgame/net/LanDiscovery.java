package com.brawlgame.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LAN server discovery using UDP broadcast.
 *
 * <h3>Server side</h3>
 * Call {@link #startBroadcast(int)} to begin announcing this machine every 2 s on port
 * {@value #DISCOVERY_PORT}.  Call {@link #stopBroadcast()} when the match starts.
 *
 * <h3>Client side</h3>
 * Call {@link #startScan()} to collect announcements.  {@link #getServers()} returns a snapshot
 * of discovered entries (thread-safe copy).  Call {@link #stopScan()} when done.
 *
 * <p>Packet payload: {@code "MCBRAWL <gamePort> <displayName>"}.
 */
public final class LanDiscovery {

    public static final int DISCOVERY_PORT = 7779;
    private static final String MAGIC = "MCBRAWL ";
    private static final int BUF = 256;

    // ---- shared discovered-server list (written from listener thread, read from GL thread) ----
    private final List<ServerInfo> servers = new CopyOnWriteArrayList<>();

    private volatile Thread broadcastThread;
    private volatile Thread listenThread;
    private volatile boolean broadcastRunning;
    private volatile boolean listenRunning;

    // ---- public API ----

    public static final class ServerInfo {
        public final String host;
        public final int    port;
        public final String name;
        ServerInfo(String host, int port, String name) {
            this.host = host; this.port = port; this.name = name;
        }
        @Override public String toString() { return name + "  (" + host + ":" + port + ")"; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ServerInfo)) return false;
            ServerInfo s = (ServerInfo) o;
            return host.equals(s.host) && port == s.port;
        }
        @Override public int hashCode() { return host.hashCode() * 31 + port; }
    }

    /** Start broadcasting this machine as a server. */
    public void startBroadcast(final int gamePort) {
        broadcastRunning = true;
        String msg = MAGIC + gamePort + " " + getLocalHostName();
        byte[] data = msg.getBytes();
        broadcastThread = new Thread(() -> {
            try (DatagramSocket sock = new DatagramSocket()) {
                sock.setBroadcast(true);
                DatagramPacket pkt = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
                while (broadcastRunning) {
                    sock.send(pkt);
                    Thread.sleep(2000);
                }
            } catch (Exception ignored) {}
        }, "mcbrawl-broadcast");
        broadcastThread.setDaemon(true);
        broadcastThread.start();
    }

    public void stopBroadcast() {
        broadcastRunning = false;
        if (broadcastThread != null) broadcastThread.interrupt();
    }

    /** Start listening for server announcements. */
    public void startScan() {
        servers.clear();
        listenRunning = true;
        listenThread = new Thread(() -> {
            try (DatagramSocket sock = new DatagramSocket(DISCOVERY_PORT)) {
                sock.setSoTimeout(500); // non-blocking-ish via timeout
                byte[] buf = new byte[BUF];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                while (listenRunning) {
                    try {
                        sock.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength()).trim();
                        if (msg.startsWith(MAGIC)) {
                            String rest = msg.substring(MAGIC.length());
                            String[] parts = rest.split(" ", 2);
                            if (parts.length >= 1) {
                                int port = Integer.parseInt(parts[0].trim());
                                String name = parts.length > 1 ? parts[1] : pkt.getAddress().getHostAddress();
                                ServerInfo si = new ServerInfo(pkt.getAddress().getHostAddress(), port, name);
                                if (!servers.contains(si)) servers.add(si);
                            }
                        }
                    } catch (java.net.SocketTimeoutException ignored) {}
                }
            } catch (Exception ignored) {}
        }, "mcbrawl-scan");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    public void stopScan() {
        listenRunning = false;
        if (listenThread != null) listenThread.interrupt();
    }

    /** Thread-safe snapshot of discovered servers. */
    public List<ServerInfo> getServers() { return new ArrayList<>(servers); }

    private static String getLocalHostName() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "Player"; }
    }
}
