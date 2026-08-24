package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.ui.PaneWidgets;
import catchrelease.ui.ShopUi;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class AquariumTankPanel extends BaseCustomUIPanelPlugin {

    public static final float WALL_PAD = 6f;
    public static final float FISH_LENGTH_MIN = 12f;
    public static final float FISH_LENGTH_MAX = 76f;
    public static final int WARP_SEGMENTS = 10;

    public static final float TURNOVER_BAND = 0.15f;
    public static final float TURNOVER_RATE = 4f;

    public static final float TURN_RATE = 240f;
    public static final float MAX_PITCH = 30f;

    public static final float DRIFT_TILT = 8f;
    public static final float DRIFT_TILT_RATE = 0.5f;

    public static final float CRAB_BED_PX = 4f;
    public static final float CRAB_BOB = 0.03f;
    public static final float CRAB_DASH_MIN = 0.05f;
    public static final float CRAB_DASH_MAX = 0.24f;
    public static final float CRAB_SPEED = 2.2f;
    public static final float CRAB_ROCK = 5f;

    public static final Color WATER_DEEP = new Color(8, 24, 38);
    public static final Color WATER_SHALLOW = new Color(18, 60, 84);

    public static final Color GLASS = new Color(120, 200, 230);
    public static final Color KELP = new Color(24, 74, 52);
    public static final float FADE_IN = 0.4f;

    protected final BreachConservatory conservatory;
    protected final InteractionDialogAPI dialog;
    protected PositionAPI pos;

    protected float time = 0f;
    protected Backdrop preview;
    protected float shown = 0f;
    protected final List<TankFish> fish = new ArrayList<>();
    protected int stockStamp = -1;
    protected final List<Bubble> bubbles = new ArrayList<>();
    protected final List<float[]> pebbles = new ArrayList<>();
    protected final List<float[]> kelp = new ArrayList<>();
    protected boolean furnished = false;

    protected static class TankFish {

        protected enum Build {

            SWIMMER,
            DRIFTER,
            CRAWLER
        }

        protected final FishCatch data;
        protected final FishSpec spec;
        protected final Build build;
        protected final float lengthPx;
        protected final float aspect;
        protected final Vector2f loc = new Vector2f();
        protected final Vector2f vel = new Vector2f();
        protected final Vector2f target = new Vector2f();
        protected float heading;
        protected float pause = 0f;
        protected boolean darting = false;
        protected float pitch = 0f;
        protected float age = 0f;

        protected float floorMin = 0f;
        protected float floorMax = 0f;

        protected float turnover;
        protected float turnoverTarget;

        protected FishMotion mode;
        protected float modeLeft = 0f;
        protected final float wavePhase;
        protected transient SpriteAPI sprite;

        public TankFish(FishCatch data) {
            this.data = data;
            this.spec = data.getSpec();
            this.build = buildOf(spec);

            lengthPx = lengthOnGlass(data);

            SpriteAPI art = SpriteLoader.loadSprite(spec.icon);
            sprite = art;
            aspect = art != null && art.getWidth() > 0f
                    ? art.getHeight() / art.getWidth() : 0.5f;

            mode = nextMode();
            wavePhase = MathUtils.getRandomNumberInRange(0f, 6.28f);

            // started level and already round the right way, so opening the menu is not a room of fish spinning to face where they are going
            boolean rightward = MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f;
            heading = rightward ? 0f : 180f;
            turnover = turnoverTarget = rightward ? 1f : -1f;

            // scattered in, so a fresh tank does not start as a firing squad; a crab scatters along the floor, since that is the only place a crab is ever going to be
            loc.set(MathUtils.getRandomNumberInRange(0.15f, 0.85f),
                    build == Build.CRAWLER ? 0.1f
                            : MathUtils.getRandomNumberInRange(0.2f, 0.8f));
            target.set(loc);
        }

        protected static Build buildOf(FishSpec spec) {
            if (spec.tags.contains("crab")) return Build.CRAWLER;
            if (spec.tags.contains("mollusc")) return Build.DRIFTER;
            if (spec.tags.contains("fish")) return Build.SWIMMER;

            return Build.DRIFTER;
        }

        protected FishMotion nextMode() {
            FishMotion rolled = spec.motion == FishMotion.MIXED ? rollMixedMode() : spec.motion;

            // nothing built like a jellyfish bolts or lunges
            return build == Build.DRIFTER
                    && (rolled == FishMotion.DARTER || rolled == FishMotion.LUNGER)
                    ? FishMotion.SMOOTH : rolled;
        }

        protected FishMotion rollMixedMode() {
            FishMotion[] pool = {FishMotion.SMOOTH, FishMotion.DARTER, FishMotion.SINKER,
                    FishMotion.FLOATER, FishMotion.WEAVER, FishMotion.TWITCHER, FishMotion.LUNGER};

            return pool[(int) MathUtils.getRandomNumberInRange(0f, pool.length - 0.01f)];
        }

        public void advance(float amount, float tankW, float tankH) {
            age += amount;

            // the stones, plus half of however tall this one is, so it stands on the bed and not through it
            floorMin = MathUtils.clamp((CRAB_BED_PX + lengthPx * aspect * 0.5f)
                    / Math.max(tankH, 1f), 0.05f, 0.5f);
            floorMax = floorMin + CRAB_BOB;

            if (spec.motion == FishMotion.MIXED && build != Build.CRAWLER) {
                modeLeft -= amount;
                if (modeLeft <= 0f) {
                    mode = nextMode();
                    modeLeft = MathUtils.getRandomNumberInRange(6f, 12f);
                }
            }

            float speed = 14f + 24f * spec.motionSpeed;
            float restless = Math.max(0.3f, spec.restlessness);

            if (pause > 0f) {
                pause -= amount;
                vel.scale(Math.max(0f, 1f - amount * 3f));
            } else if (reachedTarget(tankW, tankH)) {
                pickNext(restless);
            } else {
                float mult = build == Build.CRAWLER ? CRAB_SPEED
                        : mode == FishMotion.DARTER && darting ? 3.2f : 1f;

                Vector2f desired = new Vector2f((target.x - loc.x) * tankW,
                        (target.y - loc.y) * tankH);
                if (desired.lengthSquared() > 0f) {
                    desired.normalise(desired);
                    desired.scale(speed * mult);
                }
                desired.x /= Math.max(tankW, 1f);
                desired.y /= Math.max(tankH, 1f);

                float ease = 1f - (float) Math.pow(0.05f, amount);
                vel.x += (desired.x - vel.x) * ease;
                vel.y += (desired.y - vel.y) * ease;
            }

            boolean floored = build == Build.CRAWLER;

            loc.x = MathUtils.clamp(loc.x + vel.x * amount, 0.04f, 0.96f);
            loc.y = MathUtils.clamp(loc.y + vel.y * amount,
                    floored ? floorMin : 0.06f, floored ? floorMax : 0.94f);

            // face the way it is actually going on the glass, by the shortest turn, never snapping
            if (vel.lengthSquared() > 1e-8f) {
                float toward = (float) Math.toDegrees(
                        Math.atan2(vel.y * tankH, vel.x * tankW));
                float diff = Misc.getAngleDiff(heading, toward);
                float sign = Misc.normalizeAngle(toward - heading) < 180f ? 1f : -1f;

                float turn = Math.min(Math.abs(diff), TURN_RATE * amount);
                heading = Misc.normalizeAngle(heading + turn * sign);
            }

            advanceBearing(amount, speed / Math.max(tankW, 1f));
        }

        protected void pickNext(float restless) {
            if (build == Build.CRAWLER) {
                pause = MathUtils.getRandomNumberInRange(0.4f, 2.2f) / restless;

                float dash = MathUtils.getRandomNumberInRange(CRAB_DASH_MIN, CRAB_DASH_MAX)
                        * (Math.random() < 0.5 ? -1f : 1f);

                target.set(MathUtils.clamp(loc.x + dash, 0.08f, 0.92f),
                        MathUtils.getRandomNumberInRange(floorMin, floorMax));
                return;
            }

            switch (mode) {
                case DARTER:
                    if (darting) {
                        darting = false;
                        pause = MathUtils.getRandomNumberInRange(1.5f, 4.5f) / restless;
                    } else {
                        darting = true;
                        pickTarget(0.1f, 0.9f);
                    }
                    break;
                case SINKER:
                    pause = MathUtils.getRandomNumberInRange(0.5f, 2f) / restless;
                    pickTarget(0.05f, 0.4f);
                    break;
                case FLOATER:
                    pause = MathUtils.getRandomNumberInRange(0.5f, 2f) / restless;
                    pickTarget(0.6f, 0.95f);
                    break;
                case WEAVER:
                    // no pause and always the far wall: laps, not errands
                    target.set(loc.x < 0.5f ? 0.9f : 0.1f,
                            MathUtils.getRandomNumberInRange(0.3f, 0.7f));
                    break;
                case TWITCHER:
                    pause = MathUtils.getRandomNumberInRange(0.15f, 0.5f) / restless;
                    target.set(MathUtils.clamp(loc.x
                                    + MathUtils.getRandomNumberInRange(-0.12f, 0.12f), 0.08f, 0.92f),
                            MathUtils.clamp(loc.y
                                    + MathUtils.getRandomNumberInRange(-0.1f, 0.1f), 0.15f, 0.9f));
                    break;
                case LUNGER:
                    pause = MathUtils.getRandomNumberInRange(3f, 6f) / restless;
                    pickTarget(0.1f, 0.9f);
                    break;
                default:
                    pause = MathUtils.getRandomNumberInRange(0.2f, 1.5f) / restless;
                    pickTarget(0.1f, 0.9f);
                    break;
            }
        }

        protected void advanceBearing(float amount, float speed) {
            switch (build) {
                case DRIFTER:
                    pitch = DRIFT_TILT * (float) Math.sin(age * DRIFT_TILT_RATE + wavePhase);
                    return;

                case CRAWLER:
                    pitch = CRAB_ROCK * (float) Math.sin(age * 9f + wavePhase)
                            * Math.min(1f, vel.length() / Math.max(speed, 1e-5f));
                    break;

                default:
                    double radians = Math.toRadians(heading);

                    pitch = MathUtils.clamp((float) Math.toDegrees(Math.atan2(
                            Math.sin(radians), Math.abs(Math.cos(radians)))),
                            -MAX_PITCH, MAX_PITCH);
                    break;
            }

            advanceTurnover(amount);
        }

        protected void advanceTurnover(float amount) {
            float facing = (float) Math.cos(Math.toRadians(heading));

            if (facing > TURNOVER_BAND) turnoverTarget = 1f;
            else if (facing < -TURNOVER_BAND) turnoverTarget = -1f;

            float step = TURNOVER_RATE * amount;
            turnover += MathUtils.clamp(turnoverTarget - turnover, -step, step);
        }

        protected boolean reachedTarget(float tankW, float tankH) {
            float dx = (target.x - loc.x) * tankW;
            float dy = (target.y - loc.y) * tankH;

            return dx * dx + dy * dy < 144f;
        }

        protected void pickTarget(float yMin, float yMax) {
            target.set(MathUtils.getRandomNumberInRange(0.08f, 0.92f),
                    MathUtils.getRandomNumberInRange(yMin, yMax));
        }

        public void render(float tankX, float tankY, float tankW, float tankH,
                           float alphaMult, float time) {
            if (sprite == null) {
                sprite = SpriteLoader.loadSprite(spec.icon);
                if (sprite == null) return;
            }

            float cx = tankX + loc.x * tankW;
            float cy = tankY + loc.y * tankH;

            float length = lengthPx;
            float breadth = length * aspect;

            float breathe = 1f + 0.025f * (float) Math.sin(time * 1.7f + wavePhase);
            float breatheAcross = 1f + 0.035f * (float) Math.sin(time * 1.7f + wavePhase + 1.1f);

            float renderAngle = pitch * turnover;

            GL11.glPushMatrix();
            GL11.glTranslatef(cx, cy, 0f);
            GL11.glRotatef(renderAngle, 0f, 0f, 1f);
            GL11.glScalef(turnover * breathe, breatheAcross, 1f);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            sprite.bindTexture();
            GL11.glColor4f(1f, 1f, 1f, alphaMult);

            float waveAmp = breadth * 0.09f * Math.max(0.5f, spec.jitter);
            float waveSpeed = 5f + 3f * spec.motionSpeed;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= WARP_SEGMENTS; i++) {
                float along = i / (float) WARP_SEGMENTS;

                float tailness = 1f - along;
                float sway = (float) Math.sin(wavePhase + time * waveSpeed + tailness * 3.4f)
                        * waveAmp * (0.15f + 0.85f * tailness);

                float x = -length * 0.5f + along * length;

                uv(along, 1f);
                GL11.glVertex2f(x, breadth * 0.5f + sway);
                uv(along, 0f);
                GL11.glVertex2f(x, -breadth * 0.5f + sway);
            }
            GL11.glEnd();

            GL11.glPopMatrix();
        }

        protected void uv(float along, float across) {
            float facing = Misc.normalizeAngle(spec.spriteDirection);

            if (facing >= 45f && facing < 135f) {
                GL11.glTexCoord2f(1f - across, along);
            } else if (facing >= 135f && facing < 225f) {
                GL11.glTexCoord2f(1f - along, across);
            } else if (facing >= 225f && facing < 315f) {
                GL11.glTexCoord2f(across, 1f - along);
            } else {
                // painted facing right: the art already agrees with the local frame
                GL11.glTexCoord2f(along, across);
            }
        }
    }

    protected static class Bubble {

        protected float x, y, speed, radius, wobblePhase;

        public void render(float tankX, float tankY, float alphaMult, float time) {
            float wobble = (float) Math.sin(time * 2.2f + wobblePhase) * 2f;

            catchrelease.rendering.helper.Disc.draw(tankX + x + wobble, tankY + y,
                    radius, GLASS, 0.25f * alphaMult, 0.05f * alphaMult, false);
        }
    }

    public AquariumTankPanel(BreachConservatory conservatory, InteractionDialogAPI dialog) {
        this.conservatory = conservatory;
        this.dialog = dialog;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    protected String backdropPath() {
        Backdrop hanging = preview != null ? preview : Backdrops.getHanging(conservatory);

        return hanging == null || Backdrops.isBare(hanging) ? null : hanging.sprite;
    }

    public void setPreview(Backdrop backdrop) {
        preview = backdrop;
    }

    @Override
    public void advance(float amount) {
        time += amount;
        shown = Math.min(1f, shown + amount / FADE_IN);

        syncStock();

        float w = tankWidth();
        float h = tankHeight();
        if (w <= 0f || h <= 0f) return;

        for (TankFish swimmer : fish) swimmer.advance(amount, w, h);

        advanceBubbles(amount, w, h);
    }

    protected void syncStock() {
        // null on a preview pane opened somewhere there is no colony at all - the man in the coat will sell you a scene long before you have anywhere to hang it
        List<String> stock = conservatory == null
                ? java.util.Collections.emptyList() : conservatory.getAquariumFish();

        int stamp = stock.hashCode();
        if (stamp == stockStamp) return;
        stockStamp = stamp;

        fish.clear();

        for (String encoded : stock) {
            FishCatch data = FishCatch.decode(encoded);
            if (data == null || data.getSpec() == null) continue;

            fish.add(new TankFish(data));
        }

        // boring order in, size order out - big ones read as closer when drawn last
        fish.sort((a, b) -> Float.compare(b.lengthPx, a.lengthPx));
    }

    protected float tankWidth() {
        return pos == null ? 0f : pos.getWidth() - WALL_PAD * 2f;
    }

    protected float tankHeight() {
        return pos == null ? 0f : pos.getHeight() - WALL_PAD * 2f;
    }

    protected void furnish(float w, float h) {
        if (furnished) return;
        furnished = true;

        float x = 8f;
        while (x < w - 8f) {
            pebbles.add(new float[]{x, MathUtils.getRandomNumberInRange(1.6f, 3.4f),
                    MathUtils.getRandomNumberInRange(0.5f, 1f)});
            x += MathUtils.getRandomNumberInRange(5f, 14f);
        }

        int strands = Math.max(2, (int) (w / 90f));
        for (int i = 0; i < strands; i++) {
            kelp.add(new float[]{MathUtils.getRandomNumberInRange(0.08f, 0.92f) * w,
                    MathUtils.getRandomNumberInRange(0.35f, 0.7f) * h,
                    MathUtils.getRandomNumberInRange(0f, 6.28f),
                    MathUtils.getRandomNumberInRange(-6f, 6f)});
        }
    }

    protected void advanceBubbles(float amount, float w, float h) {
        if (bubbles.size() < 7 && Math.random() < amount * 0.6) {
            Bubble bubble = new Bubble();
            bubble.x = MathUtils.getRandomNumberInRange(6f, w - 6f);
            bubble.y = 4f;
            bubble.speed = MathUtils.getRandomNumberInRange(14f, 30f);
            bubble.radius = MathUtils.getRandomNumberInRange(1f, 2.6f);
            bubble.wobblePhase = MathUtils.getRandomNumberInRange(0f, 6.28f);
            bubbles.add(bubble);
        }

        for (int i = bubbles.size() - 1; i >= 0; i--) {
            Bubble bubble = bubbles.get(i);
            bubble.y += bubble.speed * amount;

            if (bubble.y > h - 4f) bubbles.remove(i);
        }
    }

    @Override
    public void render(float parentAlpha) {
        if (pos == null) return;

        float alphaMult = parentAlpha * shown;
        if (alphaMult <= 0f) return;

        float x = pos.getX() + WALL_PAD;
        float y = pos.getY() + WALL_PAD;
        float w = tankWidth();
        float h = tankHeight();
        if (w <= 0f || h <= 0f) return;

        furnish(w, h);

        float scale = Global.getSettings().getScreenScaleMult();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) (y * scale),
                (int) (w * scale), (int) (h * scale));

        drawBackdrop(x, y, w, h, alphaMult);
        drawWater(x, y, w, h, alphaMult);
        drawKelp(x, y, alphaMult);
        drawFloor(x, y, w, alphaMult);

        for (Bubble bubble : bubbles) bubble.render(x, y, alphaMult, time);
        for (TankFish swimmer : fish) swimmer.render(x, y, w, h, alphaMult, time);

        drawLight(x, y, w, h, alphaMult);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawGlass(x, y, w, h, alphaMult);

        // a preview is about the scene, and an empty tank is not news about a scene
        if (fish.isEmpty() && preview == null) drawEmptyLine(x, y, w, h, alphaMult);
    }

    protected void drawBackdrop(float x, float y, float w, float h, float alphaMult) {
        SpriteAPI backdrop = SpriteLoader.loadSprite(backdropPath());
        if (backdrop == null || backdrop.getWidth() <= 0f) return;

        // cover, not fit: the glass is the crop and the art fills every corner of it
        float scale = Math.max(w / backdrop.getWidth(), h / backdrop.getHeight());

        backdrop.setSize(backdrop.getWidth() * scale, backdrop.getHeight() * scale);
        backdrop.setColor(Color.WHITE);
        backdrop.setNormalBlend();
        backdrop.setAlphaMult(alphaMult);
        backdrop.renderAtCenter(x + w * 0.5f, y + h * 0.5f);
    }

    protected void drawWater(float x, float y, float w, float h, float alphaMult) {
        boolean backdropped = SpriteLoader.loadSprite(backdropPath()) != null;
        float body = (backdropped ? 0.62f : 0.92f) * alphaMult;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ShopUi.drawVerticalGradient(x, y, w, h, WATER_DEEP, WATER_SHALLOW, body, body);

        for (int i = 0; i < 3; i++) {
            float drift = time * (5f + i * 3.5f);
            float bandY = y + h * (0.3f + 0.18f * i)
                    + (float) Math.sin(time * 0.5f + i * 2.1f) * h * 0.06f;
            float thickness = h * 0.1f;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            int steps = 14;
            for (int s = 0; s <= steps; s++) {
                float px = x + w * s / steps;
                float wave = (float) Math.sin((px + drift * (i % 2 == 0 ? 1f : -1f)) * 0.045f
                        + i * 1.7f);
                float lift = wave * thickness * 0.5f;
                float glow = 0.05f + 0.04f * wave;

                setColor(GLASS, Math.max(0f, glow) * alphaMult);
                GL11.glVertex2f(px, bandY + lift + thickness * 0.5f);
                setColor(GLASS, 0f);
                GL11.glVertex2f(px, bandY + lift - thickness * 0.5f);
            }
            GL11.glEnd();
        }

        float shimmer = 0.16f + 0.05f * (float) Math.sin(time * 0.9f);
        ShopUi.drawVerticalGradient(x, y + h - 14f, w, 9f, GLASS, 0f, shimmer * alphaMult);
    }

    protected void drawLight(float x, float y, float w, float h, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (int i = 0; i < 3; i++) {
            float anchor = x + w * (0.2f + 0.3f * i)
                    + (float) Math.sin(time * 0.17f + i * 2.3f) * w * 0.06f;
            float half = w * 0.045f;
            float lean = w * 0.07f;
            float strength = 0.05f + 0.02f * (float) Math.sin(time * 0.4f + i * 1.3f);

            GL11.glBegin(GL11.GL_QUADS);
            setColor(GLASS, strength * alphaMult);
            GL11.glVertex2f(anchor - half, y + h);
            GL11.glVertex2f(anchor + half, y + h);
            setColor(GLASS, 0f);
            GL11.glVertex2f(anchor + half * 2.2f + lean, y);
            GL11.glVertex2f(anchor - half * 2.2f + lean, y);
            GL11.glEnd();
        }

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    protected void drawKelp(float x, float y, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (float[] blade : kelp) {
            float baseX = x + blade[0];
            float height = blade[1];
            float phase = blade[2];
            float lean = blade[3];

            int segments = 8;
            float width = 3.2f;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int s = 0; s <= segments; s++) {
                float up = s / (float) segments;

                // sway grows with height off the floor; the root never moves
                float sway = (float) Math.sin(time * 0.8f + phase + up * 2.2f)
                        * 6f * up * up + lean * up;
                float px = baseX + sway;
                float py = y + 4f + height * up;
                float half = width * (1f - up * 0.55f);

                setColor(KELP, (0.75f - up * 0.3f) * alphaMult);
                GL11.glVertex2f(px - half, py);
                GL11.glVertex2f(px + half, py);
            }
            GL11.glEnd();
        }
    }

    protected void drawFloor(float x, float y, float w, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ShopUi.drawVerticalGradient(x, y, w, 10f, Color.BLACK, 0.5f * alphaMult, 0f);

        for (float[] stone : pebbles) {
            float shade = stone[2];

            catchrelease.rendering.helper.Disc.draw(x + stone[0], y + 3f, stone[1],
                    new Color((int) (40 * shade), (int) (52 * shade), (int) (60 * shade)),
                    0.9f * alphaMult, 0.9f * alphaMult, false);
        }
    }

    protected void drawGlass(float x, float y, float w, float h, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1.5f);

        setColor(Misc.getDarkPlayerColor(), 0.9f * alphaMult);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        setColor(GLASS, 0.35f * alphaMult);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 1f, y + h - 1f);
        GL11.glVertex2f(x + w - 1f, y + h - 1f);
        GL11.glEnd();
    }

    protected void drawEmptyLine(float x, float y, float w, float h, float alphaMult) {
        PaneWidgets.drawNote("The tank is empty.", x, y, w, h, alphaMult);
    }

    protected static float lengthOnGlass(FishCatch data) {
        float span = FISH_LENGTH_MAX - FISH_LENGTH_MIN;

        float shortest = Float.MAX_VALUE;
        float longest = 0f;

        for (FishSpec other : FishSpecLoader.getAllFishSpecs()) {
            if (other.lengthMin > 0f) shortest = Math.min(shortest, other.lengthMin);
            longest = Math.max(longest, other.lengthMax);
        }

        if (shortest == Float.MAX_VALUE || longest <= shortest) {
            return FISH_LENGTH_MIN + span * data.getSizeFraction();
        }

        float floor = (float) Math.log(shortest);
        float reach = (float) Math.log(longest) - floor;
        float here = (float) Math.log(Math.max(data.length, shortest)) - floor;

        return FISH_LENGTH_MIN + span * MathUtils.clamp(here / reach, 0f, 1f);
    }

    protected static void setColor(Color color, float alpha) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, Math.max(0f, Math.min(1f, alpha)));
    }
}
