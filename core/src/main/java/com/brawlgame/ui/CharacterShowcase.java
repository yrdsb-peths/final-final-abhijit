package com.brawlgame.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.item.Inventory;
import com.brawlgame.model.MinecraftPlayerModel;

/**
 * Renders rigged 3D character previews into UI panels (intro cards, end-screen portraits). Replaces
 * broken 2D skin crops that showed the raw UV atlas as glitchy coloured blocks.
 */
public final class CharacterShowcase implements Disposable {

  public static final class Entry implements Disposable {
    final Model model;
    final ModelInstance instance;

    Entry(Texture skin) {
      model = MinecraftPlayerModel.build(skin);
      instance = new ModelInstance(model);
    }

    @Override
    public void dispose() {
      model.dispose();
    }
  }

  private final List<Entry> entries = new ArrayList<>();
  private ModelBatch batch;
  private PerspectiveCamera cam;
  private Environment env;

  public int add(Texture skin, Inventory gear) {
    // armor is ignored for character previews - base skin only
    entries.add(new Entry(skin));
    return entries.size() - 1;
  }

  public Entry get(int index) { return entries.get(index); }

  private void ensureGpu() {
    if (batch != null) return;
    batch = new ModelBatch();
    env = new Environment();
    env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.62f, 0.62f, 0.66f, 1f));
    env.add(new DirectionalLight().set(0.85f, 0.85f, 0.88f, -0.4f, -0.85f, -0.5f));
    cam = new PerspectiveCamera(38f, 1f, 1f);
    cam.near = 0.08f;
    cam.far = 40f;
  }

  /**
   * Draw entry {@code index} into a virtual-canvas rect. {@code spinY} rotates the model to face the
   * camera; {@code rollZ} tips the model sideways (defeat pose).
   */
  public void render(UiViewport uiv, int index, float vx, float vy, float vw, float vh,
                     float spinY, float rollZ, Color tint) {
    if (index < 0 || index >= entries.size()) return;
    ensureGpu();
    Entry e = entries.get(index);
    float[] r = uiv.toScreen(vx, vy, vw, vh);
    int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
    if (rw <= 2 || rh <= 2) return;

    Gdx.gl.glViewport(rx, ry, rw, rh);
    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glScissor(rx, ry, rw, rh);
    Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

    PreviewCamera.frame(cam, rw, rh, 0f, 2.1f, 0.55f);
    e.instance.transform.idt()
        .setToRotation(Vector3.Y, spinY + 180f)  // +180 so model faces camera (camera at +Z, model front at -Z)
        .rotate(Vector3.Z, rollZ);
    e.instance.calculateTransforms();

    batch.begin(cam);
    batch.render(e.instance, env);
    batch.end();

    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  @Override
  public void dispose() {
    for (Entry e : entries) e.dispose();
    entries.clear();
    if (batch != null) batch.dispose();
  }
}
