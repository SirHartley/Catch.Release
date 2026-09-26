package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

// The tools' drawing of the catch track. Left mouse or Space in the focused preview holds the reel.
final class FishPreview extends JPanel {

    interface Scene {

        FishingSimulation game();

        float visibleFish();

        float jitter();

        // null draws the unidentified mote the game shows without sonar
        BufferedImage icon();

        Color moteColor();

        String title();

        String footer();

        String notice();

        boolean showTruePosition();
    }

    private final Scene scene;

    FishPreview(Scene scene, Consumer<Boolean> hold) {
        this.scene = scene;
        setPreferredSize(new Dimension(480, 470));
        setFocusable(true);
        setBackground(new Color(35, 39, 43));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                requestFocusInWindow();
                hold.accept(true);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) hold.accept(false);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hold.accept(false);
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                hold.accept(false);
            }
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed SPACE"), "hold");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released SPACE"), "release");
        getActionMap().put("hold", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) { hold.accept(true); }
        });
        getActionMap().put("release", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) { hold.accept(false); }
        });
    }

    // the game's 360-pixel track, shrunk only when the window is too short
    int trackPixels() {
        return Math.max(100, Math.min(360, getHeight() - 100));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        FishingSimulation game = scene.game();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (game == null) {
                g.setColor(Color.WHITE);
                g.drawString(scene.title(), 12, 22);
                String notice = scene.notice();
                if (notice != null) g.drawString(notice, 12, 45);
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int height = trackPixels();
            int top = 40;
            int bottom = top + height;
            int x = getWidth() / 2 - 42;
            int width = 52;
            g.setColor(new Color(17, 21, 24));
            g.fillRect(x, top, width, height);
            // the game greys the bar whenever it does not cover the fish
            g.setColor(game.isFishInBar() ? new Color(82, 179, 105) : new Color(96, 102, 108));
            int barTop = bottom - Math.round((game.getBarPosition() + game.getBarHeightFraction()) * height);
            g.fillRect(x, barTop, width, Math.round(game.getBarHeightFraction() * height));
            float shakeX = FishingSimulation.jitter(game.getTimeTotal(), 0f, game.getFishVelocity(), scene.jitter(),
                    game.getTellProgress());
            int fx = x + width / 2 + Math.round(shakeX * height / 360f);
            int fy = bottom - Math.round(scene.visibleFish() * height);
            float scale = height / FishConstants.MINIGAME_TRACK_HEIGHT;
            BufferedImage icon = scene.icon();
            paintTellFlare(g, game, fx, fy, scale);
            if (icon != null) {
                int size = Math.round(FishConstants.MINIGAME_FISH_ICON_SIZE * scale);
                g.drawImage(icon, fx - size / 2, fy - size / 2, size, size, null);
            } else paintMote(g, fx, fy, scale);
            paintTellMote(g, game, fx, fy, scale);
            if (scene.showTruePosition()) {
                int trueY = bottom - Math.round(game.getFishPosition() * height);
                g.setColor(Color.YELLOW);
                g.drawLine(x - 6, trueY, x + width + 6, trueY);
            }
            g.setColor(Color.GRAY);
            g.drawRect(x, top, width, height);
            g.setColor(new Color(17, 21, 24));
            g.fillRect(x + 72, top, 12, height);
            g.setColor(game.getProgress() < 0.3f ? new Color(225, 95, 80) : new Color(110, 195, 130));
            int filled = Math.round(game.getProgress() * height);
            g.fillRect(x + 72, bottom - filled, 12, filled);
            g.setColor(Color.WHITE);
            g.drawString(scene.title(), 12, 22);
            g.drawString(scene.footer(), 12, bottom + 25);
            String notice = scene.notice();
            if (notice != null) g.drawString(notice, 12, bottom + 45);
        } finally {
            g.dispose();
        }
    }

    // the game's catch mote: a faint halo, a colour glow in shrinking passes and a white core
    private void paintMote(Graphics2D g, int fx, int fy, float scale) {
        Color color = scene.moteColor();
        fillGlow(g, fx, fy, FishConstants.MINIGAME_MOTE_HALO_SIZE * scale, Color.WHITE, FishConstants.MINIGAME_MOTE_HALO_ALPHA);
        float glow = FishConstants.MINIGAME_MOTE_GLOW_SIZE * scale;
        for (int i = 0; i < FishConstants.MINIGAME_MOTE_GLOW_PASSES; i++) {
            fillGlow(g, fx, fy, glow, color, i == 0 ? 1f : FishConstants.MINIGAME_MOTE_INNER_ALPHA);
            glow *= FishConstants.MINIGAME_MOTE_GLOW_STEP;
        }
        float core = Math.max(2f, FishConstants.MINIGAME_MOTE_CORE_SIZE * scale);
        g.setColor(new Color(1f, 1f, 1f, FishConstants.MINIGAME_MOTE_CORE_ALPHA));
        g.fill(new java.awt.geom.Ellipse2D.Float(fx - core / 2, fy - core / 2, core, core));
    }

    // a soft disc standing in for the game's additive glow sprite
    private static void fillGlow(Graphics2D g, int fx, int fy, float size, Color color, float alpha) {
        if (size < 1f) return;
        float[] stops = {0f, 1f};
        Color[] colors = {new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(255 * Math.min(1f, alpha))),
                new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)};
        Paint previous = g.getPaint();
        g.setPaint(new RadialGradientPaint(fx, fy, size / 2, stops, colors));
        g.fill(new java.awt.geom.Ellipse2D.Float(fx - size / 2, fy - size / 2, size, size));
        g.setPaint(previous);
    }

    // the game panel's flare and departing mote in the fish's colour, scaled to the preview; Swing's y runs down the screen
    private void paintTellFlare(Graphics2D g, FishingSimulation game, int fx, int fy, float scale) {
        float progress = game.getTellProgress();
        if (progress <= 0f) return;
        float swell = (float) Math.sin(progress * Math.PI);
        float strength = swell * swell;
        fillGlow(g, fx, fy, FishConstants.MINIGAME_MOTE_HALO_SIZE * (1f + FishConstants.MINIGAME_TELL_FLARE_SWELL * strength) * scale,
                scene.moteColor(), FishConstants.MINIGAME_TELL_FLARE_ALPHA * strength);
    }

    private void paintTellMote(Graphics2D g, FishingSimulation game, int fx, int fy, float scale) {
        float progress = game.getTellProgress();
        float direction = game.getTellDirection();
        if (direction == 0f || progress <= FishConstants.MINIGAME_TELL_MOTE_START) return;
        float leave = (progress - FishConstants.MINIGAME_TELL_MOTE_START) / (1f - FishConstants.MINIGAME_TELL_MOTE_START);
        float eased = 1f - (1f - leave) * (1f - leave);
        int y = fy - Math.round(direction * FishConstants.MINIGAME_TELL_MOTE_TRAVEL * eased * scale);
        float size = FishConstants.MINIGAME_TELL_MOTE_SIZE * (1f - 0.4f * leave) * scale;
        float alpha = Math.min(1f, leave / 0.15f) * (1f - leave) * (1f - leave) * FishConstants.MINIGAME_TELL_MOTE_ALPHA;
        fillGlow(g, fx, y, size, scene.moteColor(), alpha);
        fillGlow(g, fx, y, size * 0.3f, Color.WHITE, alpha);
    }
}
