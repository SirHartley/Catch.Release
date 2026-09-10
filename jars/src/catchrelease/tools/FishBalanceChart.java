package catchrelease.tools;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.SimulatedAngler.Skill;

final class FishBalanceChart extends JPanel {

    static final Color[] COLORS = {new Color(55, 112, 180), new Color(188, 103, 24), new Color(108, 70, 159)};
    static final int LEFT = 205;
    static final int ROW = 48;

    List<Result> results = List.of();
    Skill profile = Skill.REGULAR;
    boolean times;

    FishBalanceChart() {
        setBackground(Color.WHITE);
    }

    void setResults(List<Result> results, Skill profile, boolean times) {
        this.results = List.copyOf(results);
        this.profile = profile;
        this.times = times;
        setPreferredSize(new Dimension(780, Math.max(220, 85 + ROW * results.size())));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.DARK_GRAY);
            g.drawString(times ? profile + " successful catch seconds: P10 — median — P90"
                    : "Catch %: blue Beginner / orange Regular / purple Skilled", 12, 18);
            if (results.isEmpty()) { g.drawString("Run fish first. Stale and filtered-out results are excluded.", 12, 70); return; }
            double max = times ? Math.max(1, results.stream().mapToDouble(r -> r.skills().get(profile).time(0.9))
                    .filter(Double::isFinite).max().orElse(1)) : 100;
            int width = Math.max(100, getWidth() - LEFT - 75);
            for (int i = 0; i <= 4; i++) {
                int x = LEFT + width * i / 4;
                g.setColor(new Color(225, 228, 232));
                g.drawLine(x, 48, x, 65 + ROW * results.size());
                g.setColor(Color.DARK_GRAY);
                g.drawString(number(max * i / 4) + (times ? "s" : "%"), x - 8, 40);
            }
            for (int i = 0; i < results.size(); i++) {
                Result result = results.get(i);
                int y = 65 + i * ROW;
                g.setColor(Color.DARK_GRAY);
                String name = result.request().fish().name();
                while (g.getFontMetrics().stringWidth(name) > LEFT - 22 && name.length() > 2) name = name.substring(0, name.length() - 2) + "…";
                g.drawString(name, 12, y + 10);
                if (times) {
                    Stats stats = result.skills().get(profile);
                    if (!Double.isFinite(stats.time(0.5))) { g.drawString("No catches", LEFT, y + 10); continue; }
                    int low = LEFT + (int) (width * stats.time(0.1) / max);
                    int high = LEFT + (int) (width * stats.time(0.9) / max);
                    int median = LEFT + (int) (width * stats.time(0.5) / max);
                    g.setColor(COLORS[SKILLS.indexOf(profile)]);
                    g.drawLine(low, y + 9, high, y + 9);
                    g.drawLine(low, y + 4, low, y + 14);
                    g.drawLine(high, y + 4, high, y + 14);
                    g.fillOval(median - 4, y + 5, 8, 8);
                    g.drawString(number(stats.time(0.5)), high + 6, y + 14);
                } else {
                    for (int s = 0; s < SKILLS.size(); s++) {
                        double value = result.skills().get(SKILLS.get(s)).catchRate();
                        int length = (int) (width * value / 100);
                        g.setColor(COLORS[s]);
                        g.fillRect(LEFT, y - 6 + s * 12, length, 9);
                        g.drawString(number(value), LEFT + length + 5, y + 3 + s * 12);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }
}
