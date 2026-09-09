package catchrelease.tools;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class FishFacingPicker {

    private static final String CSV_PATH = "data/campaign/fish.csv";

    private final Sheet sheet;
    private final JFrame frame = new JFrame("Fish facing");
    private final JLabel title = new JLabel();
    private final JLabel status = new JLabel("Click the head to save and advance. Back revisits a fish.");
    private final ImagePanel canvas = new ImagePanel(point -> pick(point.x, point.y));
    private final JButton back = new JButton("Back");
    private final JButton skip = new JButton("Skip");
    private int index;
    private BufferedImage image;
    private double direction;

    record Cell(int start, int end, String value) {

    }

    record Fish(String id, String name, String icon, Cell direction) {

    }

    static final class Sheet {

        final Path path;
        final Path root;
        String source;
        List<Fish> fish;
        Path backup;

        Sheet(Path path) throws IOException {
            this.path = path.toRealPath();
            root = this.path.getParent().getParent().getParent();
            source = Files.readString(this.path);
            fish = readFish(source);
        }

        void save(int index, double angle) throws IOException {
            if (!Files.readString(path).equals(source)) {
                throw new IOException("fish.csv changed outside this tool. Reopen it before continuing.");
            }
            Cell cell = fish.get(index).direction;
            String value = String.format(Locale.ROOT, "%.2f", Math.round(angle * 100) % 36000 / 100d);
            String updated = source.substring(0, cell.start) + value + source.substring(cell.end);
            if (updated.equals(source)) return;
            List<Fish> parsed = readFish(updated);
            if (backup == null) {
                backup = Files.createTempFile(path.getParent(), "fish.csv.facing-", ".bak");
                Files.writeString(backup, source);
                System.out.println("Backup: " + backup);
            }
            Path temp = Files.createTempFile(path.getParent(), "fish-facing-", ".tmp");
            try {
                Files.writeString(temp, updated);
                try {
                    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            source = updated;
            fish = parsed;
        }
    }

    static final class ImagePanel extends JPanel {

        BufferedImage image;
        double direction;

        ImagePanel(Consumer<Point> onPick) {
            setPreferredSize(new Dimension(700, 600));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) onPick.accept(event.getPoint());
                }
            });
        }

        Rectangle imageBounds() {
            if (image == null) return new Rectangle();
            double scale = Math.min((getWidth() - 64d) / image.getWidth(),
                    (getHeight() - 64d) / image.getHeight());
            int w = Math.max(1, (int) (image.getWidth() * scale));
            int h = Math.max(1, (int) (image.getHeight() * scale));
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                for (int y = 0; y < getHeight(); y += 16) {
                    for (int x = 0; x < getWidth(); x += 16) {
                        g.setColor((x / 16 + y / 16) % 2 == 0 ? new Color(44, 48, 53) : new Color(58, 62, 67));
                        g.fillRect(x, y, 16, 16);
                    }
                }
                if (image == null) return;
                Rectangle box = imageBounds();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, box.x, box.y, box.width, box.height, null);
                g.setColor(Color.WHITE);
                g.drawRect(box.x, box.y, box.width, box.height);
                double radians = Math.toRadians(direction);
                double reach = Math.min(image.getWidth(), image.getHeight()) * 0.4;
                int cx = (int) box.getCenterX();
                int cy = (int) box.getCenterY();
                int hx = cx + (int) (Math.cos(radians) * reach * box.width / image.getWidth());
                int hy = cy - (int) (Math.sin(radians) * reach * box.height / image.getHeight());
                g.setColor(new Color(255, 200, 65));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(cx, cy, hx, hy);
                g.fillOval(hx - 4, hy - 4, 8, 8);
                g.drawLine(cx - 6, cy, cx + 6, cy);
                g.drawLine(cx, cy - 6, cx, cy + 6);
            } finally {
                g.dispose();
            }
        }
    }

    private FishFacingPicker(Sheet sheet, String startId) {
        this.sheet = sheet;
        if (startId != null) {
            index = -1;
            for (int i = 0; i < sheet.fish.size(); i++) {
                if (sheet.fish.get(i).id.equals(startId)) index = i;
            }
            if (index < 0) throw new IllegalArgumentException("Unknown fish ID: " + startId);
        }
        back.addActionListener(event -> showFish(index - 1));
        skip.addActionListener(event -> showFish(index + 1));
        JButton close = new JButton("Close");
        close.addActionListener(event -> frame.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(back);
        buttons.add(skip);
        buttons.add(close);
        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.add(status, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(title, BorderLayout.NORTH);
        content.add(canvas, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        frame.setContentPane(content);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setMinimumSize(new Dimension(640, 480));
        frame.setLocationRelativeTo(null);
        showFish(index);
        System.out.println("Editing: " + sheet.path);
        frame.setVisible(true);
    }

    private void showFish(int next) {
        index = Math.max(0, Math.min(next, sheet.fish.size()));
        back.setEnabled(index > 0);
        skip.setEnabled(index < sheet.fish.size());
        image = null;
        if (index == sheet.fish.size()) {
            title.setText("End of list. Saved clicks are already in fish.csv; skipped entries are unchanged.");
        } else {
            Fish fish = sheet.fish.get(index);
            try {
                direction = Double.parseDouble(fish.direction.value);
                if (!Double.isFinite(direction)) direction = 180d;
            } catch (NumberFormatException ex) {
                direction = 180d;
            }
            title.setText(String.format(Locale.ROOT, "%d / %d   %s [%s]   %.2f degrees",
                    index + 1, sheet.fish.size(), fish.name, fish.id, direction));
            try {
                image = ImageIO.read(sheet.root.resolve(fish.icon).toFile());
                if (image == null) throw new IOException("Unsupported image: " + fish.icon);
            } catch (IOException ex) {
                status.setText("Image unavailable; use Skip. " + fish.icon);
                System.err.println(ex.getMessage());
            }
        }
        canvas.image = image;
        canvas.direction = direction;
        canvas.repaint();
    }

    private void pick(int x, int y) {
        if (image == null) return;
        Point2D.Double head = imagePoint(canvas.imageBounds(), image.getWidth(), image.getHeight(), x, y);
        if (head == null) return;
        if (head.distance(image.getWidth() / 2d, image.getHeight() / 2d) < 1d) {
            status.setText("Click away from the centre to specify a direction, or Skip this fish.");
            return;
        }
        double angle = facing(image.getWidth(), image.getHeight(), head.x, head.y);
        Fish fish = sheet.fish.get(index);
        try {
            sheet.save(index, angle);
            String saved = String.format(Locale.ROOT, "Saved %s: %.2f degrees (head %.1f, %.1f)",
                    fish.id, angle, head.x, head.y);
            System.out.println(saved);
            status.setText(saved);
            showFish(index + 1);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Could not save", JOptionPane.ERROR_MESSAGE);
        }
    }

    static Point2D.Double imagePoint(Rectangle bounds, int width, int height, int x, int y) {
        if (!bounds.contains(x, y)) return null;
        return new Point2D.Double((x - bounds.x) * width / (double) bounds.width,
                (y - bounds.y) * height / (double) bounds.height);
    }

    static double facing(int width, int height, double x, double y) {
        // Images are y-down; spriteDirection is counter-clockwise from the right.
        return (Math.toDegrees(Math.atan2(height / 2d - y, x - width / 2d)) + 360d) % 360d;
    }

    static List<Fish> readFish(String source) throws IOException {
        List<List<Cell>> rows = parseCsv(source);
        if (rows.isEmpty()) throw new IOException("Empty fish.csv");
        List<String> header = rows.get(0).stream().map(Cell::value).toList();
        int id = header.indexOf("id");
        int name = header.indexOf("name");
        int icon = header.indexOf("icon");
        int direction = header.indexOf("spriteDirection");
        if (Math.min(Math.min(id, name), Math.min(icon, direction)) < 0) {
            throw new IOException("Required columns: id, name, icon, spriteDirection");
        }
        List<Fish> fish = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int required = Math.max(Math.max(id, name), Math.max(icon, direction));
        for (List<Cell> row : rows.subList(1, rows.size())) {
            if (row.stream().allMatch(cell -> cell.value.isBlank()) || row.get(0).value.startsWith("#")) continue;
            if (row.size() <= id || row.get(id).value.isBlank() || row.get(id).value.startsWith("#")) continue;
            if (row.size() <= required) throw new IOException("Missing fields for " + row.get(id).value);
            if (!ids.add(row.get(id).value)) throw new IOException("Duplicate fish ID: " + row.get(id).value);
            fish.add(new Fish(row.get(id).value, row.get(name).value, row.get(icon).value, row.get(direction)));
        }
        if (fish.isEmpty()) throw new IOException("No fish rows found");
        return fish;
    }

    static List<List<Cell>> parseCsv(String source) throws IOException {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        int start = source.startsWith("\uFEFF") ? 1 : 0;
        boolean quoted = false;
        for (int i = start; i <= source.length(); i++) {
            char c = i == source.length() ? '\0' : source.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '"') i++;
                else if (i == start) quoted = true;
                else if (quoted) {
                    quoted = false;
                    if (i + 1 < source.length() && ",\r\n".indexOf(source.charAt(i + 1)) < 0) {
                        throw new IOException("Text after CSV quote at " + i);
                    }
                } else throw new IOException("Unexpected CSV quote at " + i);
            } else if (!quoted && (c == ',' || c == '\r' || c == '\n' || i == source.length())) {
                if (i == source.length() && start == i && row.isEmpty()) break;
                String raw = source.substring(start, i);
                String value = raw.startsWith("\"") ? raw.substring(1, raw.length() - 1).replace("\"\"", "\"") : raw;
                row.add(new Cell(start, i, value));
                if (c != ',') {
                    rows.add(row);
                    row = new ArrayList<>();
                    if (c == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
                }
                start = i + 1;
            }
        }
        if (quoted) throw new IOException("Unclosed CSV quote");
        return rows;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path path = args.length > 0 ? Path.of(args[0]) : Path.of("").toAbsolutePath();
                if (Files.isDirectory(path)) {
                    while (path != null && !Files.isRegularFile(path.resolve(CSV_PATH))) path = path.getParent();
                    if (path == null) throw new IOException("Set the working directory or first argument to the mod folder.");
                    path = path.resolve(CSV_PATH);
                }
                new FishFacingPicker(new Sheet(path), args.length > 1 ? args[1] : null);
            } catch (IOException | IllegalArgumentException ex) {
                System.err.println(ex.getMessage());
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Fish facing", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
