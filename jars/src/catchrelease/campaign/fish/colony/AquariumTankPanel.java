package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopUi;
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

/**
 * The tank: the conservatory's aquarium, live on the colony's main menu. Pure GL - dark water,
 * a glass line, a few bubbles, and every fish in the stock swimming its own way.
 * <p>
 * Each specimen keeps its species' minigame manners: {@link FishMotion#SMOOTH} wanders,
 * {@link FishMotion#DARTER} sits and bolts, sinkers keep to the floor and floaters to the
 * light, and {@link FishMotion#MIXED} cycles through the lot. The sprite is drawn as a strip
 * of segments ridden by a travelling wave - stronger at the tail, with a slow pulse over the
 * whole body - so the art breathes rather than gliding like a decal. Heading uses
 * {@link FishSpec#spriteDirection}, and a fish swimming the "wrong" way is mirrored across its
 * own spine rather than rotated onto its back.
 * <p>
 * Size on screen scales with the individual catch's size stat, so the tank shows off exactly
 * what the log would brag about.
 */
public class AquariumTankPanel extends BaseCustomUIPanelPlugin {

    /** The strip below the water reserved for the add/remove buttons. */
    public static final float BUTTON_STRIP = 34f;

    public static final float WALL_PAD = 6f;

    /** On-screen fish length in px, walked by the catch's size-fraction. */
    public static final float FISH_LENGTH_MIN = 26f;
    public static final float FISH_LENGTH_MAX = 54f;

    public static final int WARP_SEGMENTS = 10;

    public static final Color WATER_DEEP = new Color(8, 24, 38);
    public static final Color WATER_SHALLOW = new Color(18, 60, 84);
    public static final Color GLASS = new Color(120, 200, 230);

    protected final BreachConservatory conservatory;
    protected final InteractionDialogAPI dialog;

    protected PositionAPI pos;
    protected float time = 0f;

    protected final List<TankFish> fish = new ArrayList<>();
    protected int stockStamp = -1;

    protected final List<Bubble> bubbles = new ArrayList<>();

    /** Set by whoever builds the button row; presses come back through buttonPressed. */
    public Object addButtonId;
    public Object removeButtonId;

    /** Guards against a picker being opened twice before the first resolves. */
    protected boolean pickerOpen = false;

    public AquariumTankPanel(BreachConservatory conservatory, InteractionDialogAPI dialog) {
        this.conservatory = conservatory;
        this.dialog = dialog;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (dialog == null || pickerOpen) return;

        if (buttonId == addButtonId) {
            pickerOpen = true;
            AquariumTransfers.openAddPicker(dialog, conservatory, moved -> pickerOpen = false);
        } else if (buttonId == removeButtonId) {
            pickerOpen = true;
            AquariumTransfers.openTakePicker(dialog, conservatory, moved -> pickerOpen = false);
        }
    }

    @Override
    public void advance(float amount) {
        time += amount;

        syncStock();

        float w = tankWidth();
        float h = tankHeight();
        if (w <= 0f || h <= 0f) return;

        for (TankFish swimmer : fish) swimmer.advance(amount, w, h);

        advanceBubbles(amount, w, h);
    }

    /** Rebuilds the sim when the stock changes under it - the buttons, the office, anything. */
    protected void syncStock() {
        List<String> stock = conservatory.getAquariumFish();

        int stamp = stock.hashCode();
        if (stamp == stockStamp) return;
        stockStamp = stamp;

        fish.clear();

        for (String encoded : stock) {
            FishCatch data = FishCatch.decode(encoded);
            if (data == null || data.getSpec() == null) continue;

            fish.add(new TankFish(data));
        }

        //boring order in, size order out - big ones read as closer when drawn last
        fish.sort((a, b) -> Float.compare(b.lengthPx, a.lengthPx));
    }

    protected float tankWidth() {
        return pos == null ? 0f : pos.getWidth() - WALL_PAD * 2f;
    }

    protected float tankHeight() {
        return pos == null ? 0f : pos.getHeight() - BUTTON_STRIP - WALL_PAD * 2f;
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
    public void render(float alphaMult) {
        if (pos == null || alphaMult <= 0f) return;

        float x = pos.getX() + WALL_PAD;
        float y = pos.getY() + BUTTON_STRIP + WALL_PAD;
        float w = tankWidth();
        float h = tankHeight();
        if (w <= 0f || h <= 0f) return;

        drawWater(x, y, w, h, alphaMult);

        //the water clips its contents; anything mid-warp past the glass stays behind it
        float scale = Global.getSettings().getScreenScaleMult();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale), (int) (y * scale),
                (int) (w * scale), (int) (h * scale));

        for (Bubble bubble : bubbles) bubble.render(x, y, alphaMult, time);
        for (TankFish swimmer : fish) swimmer.render(x, y, w, h, alphaMult, time);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawGlass(x, y, w, h, alphaMult);

        if (fish.isEmpty()) drawEmptyLine(x, y, w, h, alphaMult);
    }

    /** Dark at the floor, faintly lit at the surface, with a light-line under the rim. */
    protected void drawWater(float x, float y, float w, float h, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glBegin(GL11.GL_QUADS);
        setColor(WATER_DEEP, 0.92f * alphaMult);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        setColor(WATER_SHALLOW, 0.92f * alphaMult);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        //the surface shimmer: a thin brighter band that breathes with the tank
        float shimmer = 0.16f + 0.05f * (float) Math.sin(time * 0.9f);
        GL11.glBegin(GL11.GL_QUADS);
        setColor(GLASS, shimmer * alphaMult);
        GL11.glVertex2f(x, y + h - 5f);
        GL11.glVertex2f(x + w, y + h - 5f);
        setColor(GLASS, 0f);
        GL11.glVertex2f(x + w, y + h - 14f);
        GL11.glVertex2f(x, y + h - 14f);
        GL11.glEnd();
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
    }

    protected void drawEmptyLine(float x, float y, float w, float h, float alphaMult) {
        LazyFont font = ShopUi.getSmallFont();
        if (font == null) return;

        LazyFont.DrawableString line = font.createText("The tank is empty.",
                ShopUi.withAlpha(Misc.getGrayColor(), alphaMult), font.getBaseHeight());
        line.draw(Math.round(x + (w - line.getWidth()) * 0.5f),
                Math.round(y + (h + line.getHeight()) * 0.5f));
    }

    protected static void setColor(Color color, float alpha) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, Math.max(0f, Math.min(1f, alpha)));
    }

    //---------------------------------------------------------------- the swimmers

    /**
     * One fish in the water: its species' motion archetype driving a target point, a smoothed
     * heading, and the warped-strip render that keeps it breathing.
     */
    protected static class TankFish {

        protected final FishCatch data;
        protected final FishSpec spec;

        protected final float lengthPx;
        protected final float aspect;

        protected final Vector2f loc = new Vector2f();
        protected final Vector2f vel = new Vector2f();
        protected final Vector2f target = new Vector2f();

        protected float heading;
        protected float pause = 0f;
        protected boolean darting = false;

        /** What MIXED is currently being; everyone else keeps their own. */
        protected FishMotion mode;
        protected float modeLeft = 0f;

        protected final float wavePhase;

        protected transient SpriteAPI sprite;

        public TankFish(FishCatch data) {
            this.data = data;
            this.spec = data.getSpec();

            lengthPx = FISH_LENGTH_MIN
                    + (FISH_LENGTH_MAX - FISH_LENGTH_MIN) * data.getSizeFraction();

            SpriteAPI art = SpriteLoader.loadSprite(spec.icon);
            sprite = art;
            aspect = art != null && art.getWidth() > 0f
                    ? art.getHeight() / art.getWidth() : 0.5f;

            mode = spec.motion == FishMotion.MIXED ? rollMixedMode() : spec.motion;
            wavePhase = MathUtils.getRandomNumberInRange(0f, 6.28f);
            heading = MathUtils.getRandomNumberInRange(0f, 360f);

            //scattered in, so a fresh tank does not start as a firing squad
            loc.set(MathUtils.getRandomNumberInRange(0.15f, 0.85f),
                    MathUtils.getRandomNumberInRange(0.2f, 0.8f));
            target.set(loc);
        }

        protected FishMotion rollMixedMode() {
            FishMotion[] pool = {FishMotion.SMOOTH, FishMotion.DARTER,
                    FishMotion.SINKER, FishMotion.FLOATER};

            return pool[(int) MathUtils.getRandomNumberInRange(0f, pool.length - 0.01f)];
        }

        /** Positions are kept as fractions of the tank so a resize never beaches anyone. */
        public void advance(float amount, float tankW, float tankH) {
            if (spec.motion == FishMotion.MIXED) {
                modeLeft -= amount;
                if (modeLeft <= 0f) {
                    mode = rollMixedMode();
                    modeLeft = MathUtils.getRandomNumberInRange(6f, 12f);
                }
            }

            float speed = (14f + 24f * spec.motionSpeed) / Math.max(tankW, 1f);
            float restless = Math.max(0.3f, spec.restlessness);

            if (pause > 0f) {
                pause -= amount;
                vel.scale(Math.max(0f, 1f - amount * 3f));
            } else if (reachedTarget()) {
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
                    default:
                        pause = MathUtils.getRandomNumberInRange(0.2f, 1.5f) / restless;
                        pickTarget(0.1f, 0.9f);
                        break;
                }
            } else {
                float mult = mode == FishMotion.DARTER && darting ? 3.2f : 1f;

                Vector2f desired = Vector2f.sub(target, loc, null);
                if (desired.lengthSquared() > 0f) {
                    desired.normalise(desired);
                    desired.scale(speed * mult);
                }

                float ease = 1f - (float) Math.pow(0.05f, amount);
                vel.x += (desired.x - vel.x) * ease;
                vel.y += (desired.y - vel.y) * ease;
            }

            loc.x = MathUtils.clamp(loc.x + vel.x * amount, 0.04f, 0.96f);
            loc.y = MathUtils.clamp(loc.y + vel.y * amount, 0.06f, 0.94f);

            //face the way it is actually going, by the shortest turn, never snapping
            if (vel.lengthSquared() > 1e-6f) {
                float toward = (float) Math.toDegrees(Math.atan2(vel.y, vel.x));
                float diff = Misc.getAngleDiff(heading, toward);
                float sign = Misc.normalizeAngle(toward - heading) < 180f ? 1f : -1f;

                float turn = Math.min(Math.abs(diff), 240f * amount);
                heading = Misc.normalizeAngle(heading + turn * sign);
            }
        }

        protected boolean reachedTarget() {
            float dx = target.x - loc.x;
            float dy = target.y - loc.y;

            return dx * dx + dy * dy < 0.003f;
        }

        protected void pickTarget(float yMin, float yMax) {
            target.set(MathUtils.getRandomNumberInRange(0.08f, 0.92f),
                    MathUtils.getRandomNumberInRange(yMin, yMax));
        }

        /**
         * The strip render. The body lies along local +X with the head at the +X end; the art
         * is mapped onto that axis from its own {@link FishSpec#spriteDirection}, and a fish
         * heading anywhere leftish is mirrored across its spine so it never swims on its back.
         * A travelling wave walks head to tail - light at the jaw, loose at the fin - and the
         * whole body pulses a couple of percent, which is the breathing.
         */
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

            //breathing: a slow pulse, slightly out of phase between length and breadth
            float breathe = 1f + 0.025f * (float) Math.sin(time * 1.7f + wavePhase);
            float breatheAcross = 1f + 0.035f * (float) Math.sin(time * 1.7f + wavePhase + 1.1f);

            boolean mirrored = Math.cos(Math.toRadians(heading)) < 0;
            float renderAngle = mirrored ? 180f - heading : heading;

            GL11.glPushMatrix();
            GL11.glTranslatef(cx, cy, 0f);
            if (mirrored) GL11.glScalef(-1f, 1f, 1f);
            GL11.glRotatef(renderAngle, 0f, 0f, 1f);
            GL11.glScalef(breathe, breatheAcross, 1f);

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

                //head at +X rides nearly still; the tail at -X carries the wave
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

        /**
         * Texture coordinate for a point on the strip: {@code along} runs tail (0) to head (1)
         * on local X, {@code across} spine to back (0..1) on local Y. The art's own facing
         * decides which texture axis is the body.
         */
        protected void uv(float along, float across) {
            //snap the art's facing to the nearest cardinal; the art is drawn on one axis anyway
            float facing = Misc.normalizeAngle(spec.spriteDirection);

            if (facing >= 45f && facing < 135f) {
                //painted head-up: body runs along v, belly/back along u
                GL11.glTexCoord2f(1f - across, along);
            } else if (facing >= 135f && facing < 225f) {
                //painted facing left: mirror u so the head sits at local +X, upright
                GL11.glTexCoord2f(1f - along, across);
            } else if (facing >= 225f && facing < 315f) {
                //painted head-down
                GL11.glTexCoord2f(across, 1f - along);
            } else {
                //painted facing right: the art already agrees with the local frame
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
}
