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

/**
 * The tank: the conservatory's aquarium, live on the colony's main menu. Pure GL - water with a
 * caustic weave and leaning light shafts, a stone bed with kelp swaying off it, a glass line
 * with a lit rim, a few bubbles, and every fish in the stock swimming its own way, with whichever
 * {@link Backdrop} the conservatory is showing hung behind the water's tint. Just the glass -
 * stocking, the display switch and the choice of scene all live in the aquarium office.
 * <p>
 * Each specimen keeps its species' minigame manners: {@link FishMotion#SMOOTH} wanders,
 * {@link FishMotion#DARTER} sits and bolts, sinkers keep to the floor and floaters to the
 * light, and {@link FishMotion#MIXED} cycles through the lot. The sprite is drawn as a strip
 * of segments ridden by a travelling wave - stronger at the tail, with a slow pulse over the
 * whole body - so the art breathes rather than gliding like a decal. Heading uses
 * {@link FishSpec#spriteDirection}, and a fish swimming the "wrong" way turns over through
 * edge-on rather than rotating onto its back.
 * <p>
 * <b>How a specimen carries itself is a separate question from where it goes</b>, and it comes
 * off the same crab/mollusc/fish tags the codex names a type by - see {@link TankFish.Build}.
 * Fish point where they are going but never more than {@link #MAX_PITCH} off level; molluscs and
 * the odds and ends never turn at all and only list; crabs live on the stones and dash along
 * them. The drawn angle staying inside that band is also what keeps a fish coming about from
 * looking like a fish looping: the course still swings the whole way round, but the body never
 * rotates up through the vertical to follow it.
 * <p>
 * Size on screen is the specimen's real length read against the whole table's range, so a waller
 * dwarfs a pipechovy and a good one of either is visibly the better fish - the tank shows off
 * exactly what the log would brag about.
 */
public class AquariumTankPanel extends BaseCustomUIPanelPlugin {

    public static final float WALL_PAD = 6f;

    /**
     * On-screen fish length in px, at the two ends of everything the table can hold - the
     * smallest larva at one end and the largest waller at the other, with every specimen of
     * every species between them.
     */
    public static final float FISH_LENGTH_MIN = 12f;
    public static final float FISH_LENGTH_MAX = 76f;

    public static final int WARP_SEGMENTS = 10;

    /**
     * How far past straight up or straight down a heading has to get before the fish commits to
     * turning over, as the cosine of the heading. A fish nosing near-vertical sits on the line
     * between facing left and facing right, and without a band to hold it there the smallest
     * wobble in its course would have it turning over and back every few frames.
     */
    public static final float TURNOVER_BAND = 0.15f;

    /** How fast a fish turns over, across the whole -1 to 1 sweep - so a full reversal in half a second. */
    public static final float TURNOVER_RATE = 4f;

    /** Degrees a second a fish will swing its nose round towards where it is actually going. */
    public static final float TURN_RATE = 240f;

    /**
     * How far off the horizontal a specimen is ever <i>drawn</i>, in degrees.
     * <p>
     * The course is not clamped - a fish rising still rises, and the heading still swings the
     * whole way round when it turns - but a fish rising does not stand on its tail to do it. It
     * swims up at a slant. Drawing the body along the raw heading had specimens vertical several
     * times a minute in a tank where crossing the glass takes a couple of seconds, and it is what
     * made coming about look like a loop: the nose went up through straight-up on the way.
     */
    public static final float MAX_PITCH = 30f;

    /** How far a drifter leans, and how slowly. A mollusc does not point anywhere; it lists. */
    public static final float DRIFT_TILT = 8f;
    public static final float DRIFT_TILT_RATE = 0.5f;

    /**
     * How far a crab's underside clears the stone bed, in px, and the slice of tank it wanders
     * up and down within.
     * <p>
     * In pixels off the specimen's own size rather than a flat fraction, because the tank is
     * 170px tall and a large specimen is 76 of them: a band that stands a small crab neatly on
     * the stones puts a big one's legs through the floor, where the scissor cuts them off.
     */
    public static final float CRAB_BED_PX = 4f;
    public static final float CRAB_BOB = 0.03f;

    /** A crab crosses a short stretch of floor at a time, quickly, and then thinks about it. */
    public static final float CRAB_DASH_MIN = 0.05f;
    public static final float CRAB_DASH_MAX = 0.24f;
    public static final float CRAB_SPEED = 2.2f;
    public static final float CRAB_ROCK = 5f;

    public static final Color WATER_DEEP = new Color(8, 24, 38);
    public static final Color WATER_SHALLOW = new Color(18, 60, 84);
    public static final Color GLASS = new Color(120, 200, 230);
    public static final Color KELP = new Color(24, 74, 52);

    protected final BreachConservatory conservatory;
    protected final InteractionDialogAPI dialog;

    /**
     * How long the tank takes to come up, in seconds.
     * <p>
     * The other half of the pop-in. The tank is mounted by a watcher rather than built with the
     * menu, so however early that watcher gets to it there is always some frame on which the tank
     * was not there and the next one on which it is - and at full strength that frame is a pop.
     * Eased on, it is the water clearing.
     */
    public static final float FADE_IN = 0.4f;

    protected PositionAPI pos;
    protected float time = 0f;

    /** Set on a pane standing in for the real tank - see {@link #setPreview}. */
    protected Backdrop preview;

    /** 0 on the frame it is mounted, 1 once it has arrived. */
    protected float shown = 0f;

    protected final List<TankFish> fish = new ArrayList<>();
    protected int stockStamp = -1;

    protected final List<Bubble> bubbles = new ArrayList<>();

    /** The tank's furniture, laid once per mount so it does not rearrange itself per frame. */
    protected final List<float[]> pebbles = new ArrayList<>();
    protected final List<float[]> kelp = new ArrayList<>();
    protected boolean furnished = false;

    public AquariumTankPanel(BreachConservatory conservatory, InteractionDialogAPI dialog) {
        this.conservatory = conservatory;
        this.dialog = dialog;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        pos = position;
    }

    /**
     * The scene hung behind the water, as a file path, or null for none.
     * <p>
     * Whichever {@link Backdrop} this conservatory is showing - the choice is the tank's, the
     * ownership is the player's, and both live in {@link Backdrops}. Null carries the gradient
     * alone, which is also what a row whose art has not been drawn yet comes to.
     * <p>
     * The art is cropped to cover, so any image works and one of the wrong shape loses its edges
     * rather than letterboxing. The pane is {@code 388 x 170} at the game's own UI scale and the
     * glass line is drawn over its edge, leaving {@code 386 x 168} of art visible - near enough
     * {@code 2.3:1} - so about {@code 772 x 336} covers it and stays sharp on a scaled-up
     * interface.
     */
    protected String backdropPath() {
        Backdrop hanging = preview != null ? preview : Backdrops.getHanging(conservatory);

        return hanging == null || Backdrops.isBare(hanging) ? null : hanging.sprite;
    }

    /**
     * Shows a scene this conservatory is not hanging, for a pane that is about the scene rather
     * than about the colony - the office's picker, and Crablobab's coat.
     * <p>
     * A preview is the tank itself and not a picture of one: same water, same caustics, same light
     * and the same crop, because the whole question being asked is what it will look like. It also
     * means there is one drawing of an aquarium in the mod rather than two that can drift.
     */
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

    /** Rebuilds the sim when the stock changes under it - the buttons, the office, anything. */
    protected void syncStock() {
        //null on a preview pane opened somewhere there is no colony at all - the man in the coat
        //will sell you a scene long before you have anywhere to hang it
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

        //boring order in, size order out - big ones read as closer when drawn last
        fish.sort((a, b) -> Float.compare(b.lengthPx, a.lengthPx));
    }

    protected float tankWidth() {
        return pos == null ? 0f : pos.getWidth() - WALL_PAD * 2f;
    }

    protected float tankHeight() {
        return pos == null ? 0f : pos.getHeight() - WALL_PAD * 2f;
    }

    /** Rolls the pebbles and the kelp once - furniture that rearranged itself would be a bug. */
    protected void furnish(float w, float h) {
        if (furnished) return;
        furnished = true;

        float x = 8f;
        while (x < w - 8f) {
            //{x, radius, shade} - a low bank of stones along the floor
            pebbles.add(new float[]{x, MathUtils.getRandomNumberInRange(1.6f, 3.4f),
                    MathUtils.getRandomNumberInRange(0.5f, 1f)});
            x += MathUtils.getRandomNumberInRange(5f, 14f);
        }

        int strands = Math.max(2, (int) (w / 90f));
        for (int i = 0; i < strands; i++) {
            //{x, height, phase, lean} - one blade of kelp, swaying on its own clock
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

        //the water clips its contents; anything mid-warp past the glass stays behind it
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

        //a preview is about the scene, and an empty tank is not news about a scene
        if (fish.isEmpty() && preview == null) drawEmptyLine(x, y, w, h, alphaMult);
    }

    /** The scene behind the water, when the art exists; the tint over it keeps it submerged. */
    protected void drawBackdrop(float x, float y, float w, float h, float alphaMult) {
        SpriteAPI backdrop = SpriteLoader.loadSprite(backdropPath());
        if (backdrop == null || backdrop.getWidth() <= 0f) return;

        //cover, not fit: the glass is the crop and the art fills every corner of it
        float scale = Math.max(w / backdrop.getWidth(), h / backdrop.getHeight());

        backdrop.setSize(backdrop.getWidth() * scale, backdrop.getHeight() * scale);
        backdrop.setColor(Color.WHITE);
        backdrop.setNormalBlend();
        backdrop.setAlphaMult(alphaMult);
        backdrop.renderAtCenter(x + w * 0.5f, y + h * 0.5f);
    }

    /**
     * The water itself: the depth gradient (translucent over a backdrop, near-solid without
     * one), a slow caustic weave of brighter bands drifting through the middle depths, and the
     * surface shimmer breathing under the rim.
     */
    protected void drawWater(float x, float y, float w, float h, float alphaMult) {
        boolean backdropped = SpriteLoader.loadSprite(backdropPath()) != null;
        float body = (backdropped ? 0.62f : 0.92f) * alphaMult;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        ShopUi.drawVerticalGradient(x, y, w, h, WATER_DEEP, WATER_SHALLOW, body, body);

        //the caustic weave: soft bright bands sliding through the water at their own speeds,
        //which is most of what makes still water read as water
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

        //the surface shimmer: a thin brighter band that breathes with the tank
        float shimmer = 0.16f + 0.05f * (float) Math.sin(time * 0.9f);
        ShopUi.drawVerticalGradient(x, y + h - 14f, w, 9f, GLASS, 0f, shimmer * alphaMult);
    }

    /** Shafts of surface light leaning through the water, wandering a little. Drawn over the
     *  swimmers - light in water sits on top of what swims through it. */
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

    /** The kelp: blades swaying from the floor, each segment leaning on the one below it. */
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

                //sway grows with height off the floor; the root never moves
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

    /** The floor: a dark bed with a low bank of stones on it. */
    protected void drawFloor(float x, float y, float w, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        //the bed itself, darker than any water above it
        ShopUi.drawVerticalGradient(x, y, w, 10f, Color.BLACK, 0.5f * alphaMult, 0f);

        for (float[] stone : pebbles) {
            float shade = stone[2];

            catchrelease.rendering.helper.Disc.draw(x + stone[0], y + 3f, stone[1],
                    new Color((int) (40 * shade), (int) (52 * shade), (int) (60 * shade)),
                    0.9f * alphaMult, 0.9f * alphaMult, false);
        }
    }

    /** The glass: the panes' one-pixel border, with a brighter bevel line along the top rim. */
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

        //the rim catching the room's light
        setColor(GLASS, 0.35f * alphaMult);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 1f, y + h - 1f);
        GL11.glVertex2f(x + w - 1f, y + h - 1f);
        GL11.glEnd();
    }

    protected void drawEmptyLine(float x, float y, float w, float h, float alphaMult) {
        PaneWidgets.drawNote("The tank is empty.", x, y, w, h, alphaMult);
    }

    /**
     * How long a specimen is on the glass, in px, from how long it actually is in metres.
     * <p>
     * The tank used to walk this off the catch's size-fraction, which is where the specimen sits
     * <i>within its own species</i> - so a waller and a pipechovy both came out the same middling
     * forty-odd pixels, and the size the fish was graded and paid for never showed. Worse, the
     * fraction is the average of two rolls that are each already bunched towards the middle, so
     * even within one species the whole stock landed within a few pixels of each other.
     * <p>
     * Against the real length instead, and logarithmically, because the table spans nearly two
     * hundred to one and a linear reading would put every fish in the sector under a pixel to
     * make room for the waller. The ends are the table's own, so the biggest thing in the water
     * is the biggest thing on the glass no matter what a species file adds later.
     */
    protected static float lengthOnGlass(FishCatch data) {
        float span = FISH_LENGTH_MAX - FISH_LENGTH_MIN;

        float shortest = Float.MAX_VALUE;
        float longest = 0f;

        for (FishSpec other : FishSpecLoader.getAllFishSpecs()) {
            if (other.lengthMin > 0f) shortest = Math.min(shortest, other.lengthMin);
            longest = Math.max(longest, other.lengthMax);
        }

        //a table of one length, or none at all: the species scale says nothing, so fall back on
        //where the specimen sits in its own range rather than drawing everything identically
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

    //---------------------------------------------------------------- the swimmers

    /**
     * One fish in the water: its species' motion archetype driving a target point, a smoothed
     * heading, and the warped-strip render that keeps it breathing.
     */
    protected static class TankFish {

        /**
         * How a specimen carries itself, which is a different question from how it moves about.
         * <p>
         * Motion is the species sheet's business and decides where a specimen goes; this decides
         * which way round and which way up it is while it goes there, and the two do not answer
         * to each other. A mollusc the sheet calls a floater still rises - it just does not turn
         * to face the surface on the way, because it has nothing to turn with.
         */
        protected enum Build {
            /** Anything with a spine: points where it is going, within {@link #MAX_PITCH}. */
            SWIMMER,

            /** Molluscs, and everything the sheet files as Other: never turns, only lists. */
            DRIFTER,

            /** Crabs: on the stones, sideways, in short bursts. */
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

        /**
         * How far off level the body is <i>drawn</i>, which is the heading bounded to
         * {@link #MAX_PITCH} for a swimmer and something else entirely for the other two.
         * <p>
         * Kept apart from {@link #heading} rather than clamping that, because the heading is the
         * course and the course is allowed anywhere: it is what steers, what decides the side in
         * {@link #advanceTurnover}, and what has to swing the full half-circle when a fish turns
         * round. Only the drawing is bounded.
         */
        protected float pitch = 0f;

        /** Its own clock, for the leans and rocks that answer to nothing else. */
        protected float age = 0f;

        /** The band a crawler is allowed in, as tank fractions - depends on the tank's height and
         *  on how big this one is, so it is worked out on the tick rather than declared. */
        protected float floorMin = 0f;
        protected float floorMax = 0f;

        /**
         * Which way round the fish is, as a signed scale on its own length: 1 facing the way the
         * art was drawn, -1 the other way, and everything in between mid-turn. It is a number
         * rather than a flag because turning over is something the eye has to be shown - a fish
         * that changes sides in one frame reads as a glitch, and a fish that shortens to nothing
         * and comes back the other way reads as a fish.
         */
        protected float turnover;
        protected float turnoverTarget;

        /** What MIXED is currently being; everyone else keeps their own. */
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

            //started level and already round the right way, so opening the menu is not a room of
            //fish spinning to face where they are going
            boolean rightward = MathUtils.getRandomNumberInRange(0f, 1f) < 0.5f;
            heading = rightward ? 0f : 180f;
            turnover = turnoverTarget = rightward ? 1f : -1f;

            //scattered in, so a fresh tank does not start as a firing squad; a crab scatters
            //along the floor, since that is the only place a crab is ever going to be
            loc.set(MathUtils.getRandomNumberInRange(0.15f, 0.85f),
                    build == Build.CRAWLER ? 0.1f
                            : MathUtils.getRandomNumberInRange(0.2f, 0.8f));
            target.set(loc);
        }

        /**
         * Off the same crab/mollusc/fish tags the codex reads a type off, so the tank and the
         * sheet cannot disagree about what a thing is. Anything unfiled drifts, which is the safe
         * default: it is the one manner that assumes nothing about the animal's shape.
         */
        protected static Build buildOf(FishSpec spec) {
            if (spec.tags.contains("crab")) return Build.CRAWLER;
            if (spec.tags.contains("mollusc")) return Build.DRIFTER;
            if (spec.tags.contains("fish")) return Build.SWIMMER;

            return Build.DRIFTER;
        }

        /**
         * The manner it is about to move in: the sheet's, or a fresh roll for
         * {@link FishMotion#MIXED} - with the bolt taken out of anything that drifts. A mollusc
         * crossing the tank at three times speed is not drifting, whatever the roll said, and
         * sixteen of the drifting rows on the sheet are MIXED.
         */
        protected FishMotion nextMode() {
            FishMotion rolled = spec.motion == FishMotion.MIXED ? rollMixedMode() : spec.motion;

            return build == Build.DRIFTER && rolled == FishMotion.DARTER
                    ? FishMotion.SMOOTH : rolled;
        }

        protected FishMotion rollMixedMode() {
            FishMotion[] pool = {FishMotion.SMOOTH, FishMotion.DARTER,
                    FishMotion.SINKER, FishMotion.FLOATER};

            return pool[(int) MathUtils.getRandomNumberInRange(0f, pool.length - 0.01f)];
        }

        /**
         * Positions are kept as fractions of the tank so a resize never beaches anyone, but every
         * <i>decision</i> here is taken in pixels and converted back at the end.
         * <p>
         * The distinction is not pedantry. The tank is about twice as wide as it is tall, so a
         * fraction of its width and a fraction of its height are different distances on the glass,
         * and a course steered in fractions comes out steeper on screen than it was meant to and
         * slower going up than going across. Worse, the heading taken off it disagreed with the
         * visible travel, and disagreed most towards vertical - which is exactly where the fish
         * has to decide which way round it is.
         */
        public void advance(float amount, float tankW, float tankH) {
            age += amount;

            //the stones, plus half of however tall this one is, so it stands on the bed and not
            //through it
            floorMin = MathUtils.clamp((CRAB_BED_PX + lengthPx * aspect * 0.5f)
                    / Math.max(tankH, 1f), 0.05f, 0.5f);
            floorMax = floorMin + CRAB_BOB;

            //a crab has nowhere else to be, so the mode churn has nothing to say to it
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

                //steered in pixels at a flat pixels-a-second, then handed back as fractions
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

            //face the way it is actually going on the glass, by the shortest turn, never snapping
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

        /** Where to go next, which is the species sheet's question everywhere except the floor. */
        protected void pickNext(float restless) {
            if (build == Build.CRAWLER) {
                //no depth to choose and no far side to make for: a dash along the stones, then a
                //stop, which is the entire behavioural repertoire of a crab
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
                default:
                    pause = MathUtils.getRandomNumberInRange(0.2f, 1.5f) / restless;
                    pickTarget(0.1f, 0.9f);
                    break;
            }
        }

        /**
         * Which way up the body is drawn, and - for everything that turns - which way round.
         * <p>
         * A swimmer's is the heading's own elevation with the horizontal component taken as
         * positive, bounded to {@link #MAX_PITCH}: a specimen climbing dead vertically reads as a
         * steep climb rather than as a mast, and the side it is on is still decided by the
         * heading, so the turn is a come-about at a slant rather than a loop over the top.
         *
         * @param speed the specimen's own cruise, in tank fractions a second, for the rock
         */
        protected void advanceBearing(float amount, float speed) {
            switch (build) {
                case DRIFTER:
                    //never turns and never aims: a slow list either way, on its own clock, while
                    //it goes wherever the sheet was sending it. advanceTurnover is deliberately
                    //not called - the side it started on is the side it keeps
                    pitch = DRIFT_TILT * (float) Math.sin(age * DRIFT_TILT_RATE + wavePhase);
                    return;

                case CRAWLER:
                    //flat on its legs, with the shell rocking while the legs are working
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

        /**
         * Decides which way round the fish should be and walks it there.
         * <p>
         * A heading inside {@link #TURNOVER_BAND} of vertical decides nothing and leaves the last
         * answer standing, so a fish climbing or diving keeps the side it had instead of arguing
         * with itself about it every frame.
         */
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

        /**
         * The strip render. The body lies along local +X with the head at the +X end; the art
         * is mapped onto that axis from its own {@link FishSpec#spriteDirection}, and a fish
         * heading anywhere leftish is turned over rather than rotated onto its back - the length
         * scales through zero and out the far side, which is a fish coming about rather than a
         * sprite changing its mind. The angle it is drawn at is {@link #pitch} and not the
         * heading, so nothing ever rotates up through the vertical to get from one side to the
         * other. A travelling wave walks head to tail - light at the jaw, loose
         * at the fin - and the whole body pulses a couple of percent, which is the breathing.
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

            //the body lies along the bounded pitch rather than along the raw heading, and is
            //scaled along itself by the turnover, which is what puts the head at the right end.
            //The angle is mirrored on the far side because the scale runs before the rotation, so
            //by the time the body is turned it has already been swung the half-circle - and it is
            //ridden by the turnover rather than switched at its sign, so the one frame where the
            //body has no length has no jump in it either
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
