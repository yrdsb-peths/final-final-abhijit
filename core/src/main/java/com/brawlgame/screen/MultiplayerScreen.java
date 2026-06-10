package com.brawlgame.screen;

import java.util.List;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.brawlgame.map.GameMap;
import com.brawlgame.map.MapSerializer;
import com.brawlgame.audio.AudioManager;
import com.brawlgame.net.GameServer;
import com.brawlgame.net.LanDiscovery;
import com.brawlgame.net.LanDiscovery.ServerInfo;
import com.brawlgame.ui.BedrockWidgets;
import com.brawlgame.ui.UiViewport;

/**
 * Local multiplayer lobby: <b>Create Match</b> or <b>Join Match</b>.
 *
 * <h3>Create flow</h3>
 * Opens a {@link GameServer}, broadcasts via LAN, then launches {@link GameScreen} as host
 * once a client connects (or immediately if the user hits Start Anyway).
 *
 * <h3>Join flow</h3>
 * Scans for LAN broadcasts using {@link LanDiscovery}. Shows discovered servers as a list.
 * Clicking one connects and transitions to {@link GameScreen}.
 */
public final class MultiplayerScreen implements Screen {

    private enum Panel { HOME, CREATE_WAIT, JOIN_SCAN }

    private static final float BTN_W = 320f, BTN_H = 60f;
    private static final float ROW_W = 500f, ROW_H = 52f, ROW_GAP = 8f;

    private final Game game;
    private final UiViewport    uv = new UiViewport();
    private final ShapeRenderer sh = new ShapeRenderer();
    private final SpriteBatch   bt = new SpriteBatch();
    private final BitmapFont    fn = new BitmapFont();
    private final GlyphLayout   gl = new GlyphLayout();

    private Panel panel = Panel.HOME;

    // Create side
    private GameServer server;
    private float waitTimer = 0f;

    // Join side
    private final LanDiscovery discovery = new LanDiscovery();
    private List<ServerInfo> servers;
    private float scanTimer = 0f;
    private static final float SCAN_DURATION = 6f;
    private boolean scanning = false;

    // Default map path for hosting
    private static final String DEFAULT_MAP = "maps/sand_small_1.map";

    public MultiplayerScreen(Game game) { this.game = game; }

    @Override public void show() { panel = Panel.HOME; }

    @Override
    public void render(float delta) {
        float W = uv.width(), H = uv.height();

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        uv.apply();
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());

        Vector2 m = uv.unproject(Gdx.input.getX(), Gdx.input.getY());

        switch (panel) {
            case HOME:       renderHome(m, W, H, delta); break;
            case CREATE_WAIT: renderCreate(m, W, H, delta); break;
            case JOIN_SCAN:   renderJoin(m, W, H, delta);   break;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) goBack();
    }

    // ---- HOME panel ----

    private void renderHome(Vector2 m, float W, float H, float delta) {
        float cx = W * 0.5f;
        float createY = H * 0.5f + BTN_H + 10f;
        float joinY   = H * 0.5f - BTN_H - 10f;
        float backY   = 40f;

        boolean chov = btn(m, cx, createY, BTN_W, BTN_H);
        boolean jhov = btn(m, cx, joinY,   BTN_W, BTN_H);
        boolean bhov = btn(m, cx, backY, 200f, BTN_H);

        sh.begin(ShapeType.Filled);
        BedrockWidgets.button(sh, cx - BTN_W*0.5f, createY, BTN_W, BTN_H,
            chov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        BedrockWidgets.button(sh, cx - BTN_W*0.5f, joinY, BTN_W, BTN_H,
            jhov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        BedrockWidgets.button(sh, cx - 100f, backY, 200f, BTN_H,
            bhov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        sh.end();

        bt.begin();
        titleText("LOCAL MULTIPLAYER", cx, H - 60f);
        btnText("Create Match", cx, createY, BTN_W, BTN_H);
        btnText("Join Match",   cx, joinY,   BTN_W, BTN_H);
        btnText("Back",         cx, backY,   200f,  BTN_H);
        fn.getData().setScale(1.1f);
        fn.setColor(0.55f, 0.55f, 0.60f, 1f);
        fn.draw(bt, "LAN only  ·  same Wi-Fi or Ethernet network required",
            cx - 240f, H * 0.5f - BTN_H * 3f);
        fn.getData().setScale(1f);
        bt.end();

        if (Gdx.input.justTouched()) {
            if (chov) { AudioManager.get().click(); startCreate(); }
            if (jhov) { AudioManager.get().click(); startJoin(); }
            if (bhov) { AudioManager.get().click(); goBack(); }
        }
    }

    // ---- CREATE panel ----

    private void startCreate() {
        server = new GameServer();
        try {
            server.open();
            panel = Panel.CREATE_WAIT;
            waitTimer = 0f;
        } catch (Exception e) {
            server = null;
        }
    }

    private void renderCreate(Vector2 m, float W, float H, float delta) {
        waitTimer += delta;
        float cx = W * 0.5f;
        float cancelY = 40f;
        boolean cancHov = btn(m, cx, cancelY, 220f, BTN_H);

        sh.begin(ShapeType.Filled);
        BedrockWidgets.button(sh, cx - 110f, cancelY, 220f, BTN_H,
            cancHov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        sh.end();

        bt.begin();
        titleText("WAITING FOR PLAYER...", cx, H - 60f);
        fn.getData().setScale(1.3f);
        fn.setColor(0.8f, 0.8f, 0.85f, 1f);
        fn.draw(bt, "Tell your friend to click  Join Match", cx - 210f, H * 0.55f);
        fn.getData().setScale(1.1f);
        fn.setColor(0.5f, 0.8f, 0.5f, 1f);
        int port = server != null ? server.getPort() : GameServer.GAME_PORT;
        fn.draw(bt, "Hosting on port  " + port, cx - 120f, H * 0.45f);
        // animated dots
        int dots = (int)(waitTimer * 2f) % 4;
        String anim = "Scanning" + "...".substring(0, Math.min(dots, 3));
        fn.getData().setScale(1.0f);
        fn.setColor(0.55f, 0.55f, 0.60f, 1f);
        fn.draw(bt, anim, cx - 60f, H * 0.38f);
        btnText("Cancel", cx, cancelY, 220f, BTN_H);
        fn.getData().setScale(1f);
        bt.end();

        // Poll for client connection
        if (server != null && server.isClientConnected()) {
            launchGameAsHost();
        }
        if (Gdx.input.justTouched() && cancHov) { AudioManager.get().click(); server.close(); server = null; panel = Panel.HOME; }
    }

    private void launchGameAsHost() {
        GameMap map = loadDefaultMap();
        if (map != null) {
            GameServer handoff = server;
            server = null;
            AudioManager.get().stopMenuMusic();
            game.setScreen(new GameScreen(game, map, handoff));
        }
        else panel = Panel.HOME;
    }

    // ---- JOIN panel ----

    private void startJoin() {
        panel = Panel.JOIN_SCAN;
        scanTimer = 0f;
        scanning = true;
        servers = null;
        discovery.startScan();
    }

    private void renderJoin(Vector2 m, float W, float H, float delta) {
        if (scanning) {
            scanTimer += delta;
            if (scanTimer >= SCAN_DURATION) {
                scanning = false;
                discovery.stopScan();
                servers = discovery.getServers();
            }
        }

        float cx = W * 0.5f;
        float cancelY = 40f;
        boolean cancHov = btn(m, cx, cancelY, 220f, BTN_H);

        sh.begin(ShapeType.Filled);
        if (servers != null) {
            float topY = H * 0.70f;
            for (int i = 0; i < servers.size(); i++) {
                float ry = topY - i * (ROW_H + ROW_GAP);
                boolean hov = m.x >= cx - ROW_W*0.5f && m.x <= cx + ROW_W*0.5f
                           && m.y >= ry && m.y <= ry + ROW_H;
                BedrockWidgets.button(sh, cx - ROW_W*0.5f, ry, ROW_W, ROW_H,
                    hov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
            }
        }
        BedrockWidgets.button(sh, cx - 110f, cancelY, 220f, BTN_H,
            cancHov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        sh.end();

        bt.begin();
        titleText("JOIN MATCH", cx, H - 60f);
        if (scanning) {
            int dots = (int)(scanTimer * 2f) % 4;
            fn.getData().setScale(1.5f);
            fn.setColor(0.8f, 0.8f, 0.85f, 1f);
            String s = "Scanning" + "...".substring(0, Math.min(dots, 3));
            gl.setText(fn, s);
            fn.draw(bt, s, cx - gl.width*0.5f, H * 0.55f);
        } else if (servers != null && servers.isEmpty()) {
            fn.getData().setScale(1.3f);
            fn.setColor(0.7f, 0.4f, 0.4f, 1f);
            fn.draw(bt, "No games found on this network.", cx - 200f, H * 0.55f);
        } else if (servers != null) {
            float topY = H * 0.70f;
            for (int i = 0; i < servers.size(); i++) {
                float ry = topY - i * (ROW_H + ROW_GAP);
                fn.getData().setScale(1.2f);
                fn.setColor(BedrockWidgets.TEXT_LIGHT);
                fn.draw(bt, servers.get(i).toString(), cx - ROW_W*0.5f + 12f, ry + ROW_H*0.5f + 8f);
            }
        }
        btnText("Cancel", cx, cancelY, 220f, BTN_H);
        fn.getData().setScale(1f);
        bt.end();

        if (Gdx.input.justTouched()) {
            if (cancHov) { AudioManager.get().click(); discovery.stopScan(); panel = Panel.HOME; }
            if (servers != null) {
                float topY = H * 0.70f;
                for (int i = 0; i < servers.size(); i++) {
                    float ry = topY - i * (ROW_H + ROW_GAP);
                    if (m.x >= cx - ROW_W*0.5f && m.x <= cx + ROW_W*0.5f
                     && m.y >= ry && m.y <= ry + ROW_H) {
                        AudioManager.get().click();
                        connectToServer(servers.get(i));
                    }
                }
            }
        }
    }

    private void connectToServer(ServerInfo si) {
        com.brawlgame.net.GameClient client = new com.brawlgame.net.GameClient();
        if (client.connect(si.host, si.port)) {
            GameMap map = loadDefaultMap();
            discovery.stopScan();
            if (map != null) {
                AudioManager.get().stopMenuMusic();
                game.setScreen(new GameScreen(game, map, client));
            } else {
                client.close();
            }
        } else {
            client.close();
        }
        // If connection fails, stay on Join panel with error message
    }

    // ---- helpers ----

    private static GameMap loadDefaultMap() {
        try {
            com.badlogic.gdx.files.FileHandle f = Gdx.files.local(DEFAULT_MAP);
            if (!f.exists()) f = Gdx.files.internal(DEFAULT_MAP);
            return MapSerializer.load(f);
        } catch (Exception e) { return null; }
    }

    private void goBack() {
        if (server != null) { server.close(); server = null; }
        discovery.stopScan();
        game.setScreen(new MainMenuScreen(game));
    }

    private boolean btn(Vector2 m, float cx, float y, float w, float h) {
        return m.x >= cx - w*0.5f && m.x <= cx + w*0.5f && m.y >= y && m.y <= y + h;
    }

    private void titleText(String t, float cx, float y) {
        fn.getData().setScale(2.5f);
        fn.setColor(1f, 0.86f, 0.16f, 1f);
        gl.setText(fn, t);
        fn.draw(bt, t, cx - gl.width*0.5f, y);
    }

    private void btnText(String t, float cx, float y, float w, float h) {
        fn.getData().setScale(1.4f);
        fn.setColor(BedrockWidgets.TEXT_LIGHT);
        gl.setText(fn, t);
        fn.draw(bt, t, cx - gl.width*0.5f, y + (h + gl.height)*0.5f);
    }

    @Override public void resize(int w, int h) { uv.resize(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { if (server != null) server.close(); discovery.stopScan(); }

    @Override
    public void dispose() {
        sh.dispose();
        bt.dispose();
        fn.dispose();
        if (server != null) server.close();
        discovery.stopScan();
    }
}
