package io.github.bearinmindcat.taghover.client;

import com.inductiveautomation.factorypmi.application.binding.DefaultInteractionController;
import com.inductiveautomation.factorypmi.application.binding.ExpressionPropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.IndirectTagBindingAdapter;
import com.inductiveautomation.factorypmi.application.binding.PropertyInteractionDescriptor;
import com.inductiveautomation.factorypmi.application.binding.SimpleBoundPropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.SimpleBoundTagAdapter;
import com.inductiveautomation.factorypmi.application.components.PMILabel;
import com.inductiveautomation.factorypmi.application.components.template.TemplateHolder;
import com.inductiveautomation.factorypmi.application.components.template.VisionTemplate;
import com.inductiveautomation.vision.api.client.components.model.VisionContainer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Real Vision components from the Ignition SDK with fake tag bindings, in a plain window and with no Gateway.
 *
 * <ul>
 *   <li>{@code gradlew :client:runDemo} opens the window: hold Ctrl and hover over the boxes.</li>
 *   <li>{@code gradlew :client:demoCheck} simulates Ctrl+hover on every box, checks each tooltip and saves
 *       screenshots to {@code client/build/demo}. It uses synthetic events, so the real mouse and keyboard are
 *       never touched.</li>
 * </ul>
 */
public final class TooltipDemo {

    private record Check(String name, Component target, String expected) {}

    private final JFrame frame = new JFrame("Tag Hover Tooltip demo (fake tags, no Gateway)");
    private final List<Check> checks = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        boolean check = args.length > 0 && "--check".equals(args[0]);
        File outDir = new File(args.length > 1 ? args[1] : "build/demo");

        AtomicReference<TooltipDemo> demo = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> demo.set(new TooltipDemo()));
        TagHoverTooltip.install(ComponentLocator.DEFAULT);

        if (check) {
            int failures = demo.get().runChecks(outDir);
            SwingUtilities.invokeAndWait(() -> demo.get().frame.dispose());
            System.exit(failures == 0 ? 0 : 1);
        }
    }

    private TooltipDemo() {
        VisionTemplate root = new VisionTemplate();
        DefaultInteractionController controller = new DefaultInteractionController();
        root.setInteractionController(controller);
        root.setName("Root Container"); // What a real window's root is called.
        root.setBackground(Color.WHITE);
        root.setPreferredSize(new Dimension(1000, 560));

        PMILabel level = box(root, "Tank 1 Level", "Tank 1 Level: 42.5 gal", 30, 30);
        bindTag(controller, level, "text", "[default]Site/Tank1/Level");
        checks.add(new Check("direct-tag", level, "[default]Site/Tank1/Level"));

        PMILabel amps = box(root, "Motor Amps", "Motor Amps: 12.1 A", 330, 30);
        IndirectTagBindingAdapter indirect = new IndirectTagBindingAdapter();
        indirect.setTarget(amps);
        indirect.setTargetPropertyName("text");
        try {
            indirect.setPathParts(IndirectTagBindingAdapter.parseIndirect("[default]Motors/{1}/Amps"));
        } catch (java.text.ParseException e) {
            throw new IllegalStateException(e);
        }
        controller.setPropertyAdapter(amps, "text", indirect);
        checks.add(new Check("indirect-tag", amps, "[default]Motors/{1}/Amps"));

        PMILabel status = box(root, "Status", "Status: NORMAL", 630, 30);
        ExpressionPropertyAdapter expression = new ExpressionPropertyAdapter();
        expression.setTarget(status);
        expression.setTargetPropertyName("text");
        expression.setExpressionSource(
            "if({[default]Site/Tank1/HighAlarm}, 'HIGH', tag(\"[default]Site/Tank1/Mode\"))");
        controller.setPropertyAdapter(status, "text", expression);
        checks.add(new Check("expression", status, "[default]Site/Tank1/HighAlarm"));

        PMILabel copy = box(root, "Level Copy", "Level Copy: 42.5 gal", 30, 200);
        bindProperty(controller, copy, "text", level, "text");
        checks.add(new Check("property-chain", copy, "via"));

        TemplateHolder motor = new TemplateHolder();
        motor.setName("Motor 1");
        motor.setBounds(330, 200, 260, 110);
        motor.setBorder(BorderFactory.createTitledBorder("Template instance: Motor 1"));
        ((VisionContainer) root).addComponent(motor);
        bindTag(controller, motor, "TagPath", "[default]Motors/M1");

        VisionTemplate motorTemplate = new VisionTemplate();
        DefaultInteractionController motorController = new DefaultInteractionController();
        motorTemplate.setInteractionController(motorController);
        motorTemplate.setOpaque(false);
        motorTemplate.setBounds(10, 25, 240, 75);
        addTo(motor, motorTemplate);
        PMILabel speed = new PMILabel();
        speed.setName("Speed");
        speed.setText("Speed: 1750 rpm");
        speed.setFont(speed.getFont().deriveFont(Font.PLAIN, 16f));
        speed.setBounds(10, 10, 220, 50);
        speed.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        addTo(motorTemplate, speed);
        bindProperty(motorController, speed, "text", motorTemplate, "TagPath");
        checks.add(new Check("template-parameter", speed, "In template"));

        PMILabel plain = box(root, "Plain Label", "Plain label (no tags)", 630, 200);
        checks.add(new Check("no-bindings", plain, "No tag bindings"));

        JLabel help = new JLabel(
            "Hold Ctrl and hover over a box. These are real Vision components with fake tag bindings.",
            SwingConstants.CENTER);
        help.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel content = new JPanel(new BorderLayout());
        content.add(help, BorderLayout.NORTH);
        content.add(root, BorderLayout.CENTER);

        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    /** Simulates Ctrl+hover on each box and checks the tooltip. Returns the number of failures. */
    private int runChecks(File outDir) throws Exception {
        outDir.mkdirs();
        int failures = 0;
        int n = 1;
        for (Check c : checks) {
            onEdt(() -> hover(c.target(), true));
            Thread.sleep(250);
            AtomicReference<String> html = new AtomicReference<>();
            onEdt(() -> html.set(TagHoverTooltip.shownHtml()));
            File shot = new File(outDir, String.format("%02d-%s.png", n++, c.name()));
            onEdt(() -> screenshot(shot));

            boolean shown = html.get() != null && html.get().contains(c.expected());

            onEdt(() -> TagHoverTooltip.dispatch(new KeyEvent(
                frame, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_CONTROL,
                KeyEvent.CHAR_UNDEFINED)));
            AtomicReference<String> afterRelease = new AtomicReference<>();
            onEdt(() -> afterRelease.set(TagHoverTooltip.shownHtml()));
            boolean hidden = afterRelease.get() == null;

            boolean ok = shown && hidden;
            failures += ok ? 0 : 1;
            System.out.printf("%s  %-20s shown=%s hiddenOnCtrlRelease=%s  %s%n",
                ok ? "PASS" : "FAIL", c.name(), shown, hidden, shot.getName());
            if (!shown) {
                System.out.println("      tooltip was: " + html.get());
            }
        }

        // Hovering without Ctrl must not show anything.
        onEdt(() -> hover(checks.get(0).target(), false));
        AtomicReference<String> noCtrl = new AtomicReference<>();
        onEdt(() -> noCtrl.set(TagHoverTooltip.shownHtml()));
        boolean quiet = noCtrl.get() == null;
        failures += quiet ? 0 : 1;
        System.out.printf("%s  %-20s%n", quiet ? "PASS" : "FAIL", "no-ctrl-no-tooltip");

        System.out.println(failures == 0 ? "All demo checks passed." : failures + " demo check(s) failed.");
        return failures;
    }

    /** Dispatches a synthetic mouse move through AWT, so the real AWTEventListener sees it. */
    private static void hover(Component target, boolean ctrl) {
        MouseEvent move = new MouseEvent(
            target, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), ctrl ? InputEvent.CTRL_DOWN_MASK : 0,
            target.getWidth() / 2, target.getHeight() / 2, 0, false);
        target.dispatchEvent(move);
    }

    /** Paints the window (including a lightweight tooltip popup) to a PNG, without capturing the screen. */
    private void screenshot(File file) {
        try {
            Container rootPane = frame.getRootPane();
            BufferedImage image = new BufferedImage(
                rootPane.getWidth(), rootPane.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            rootPane.paint(g);
            g.dispose();
            ImageIO.write(image, "png", file);
        } catch (Exception e) {
            System.out.println("      screenshot failed: " + e);
        }
    }

    private static PMILabel box(Container root, String name, String text, int x, int y) {
        PMILabel label = new PMILabel();
        label.setName(name);
        label.setText(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 16f));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        label.setBounds(x, y, 260, 110);
        addTo(root, label);
        return label;
    }

    private static void addTo(Container parent, Component child) {
        if (parent instanceof VisionContainer) {
            ((VisionContainer) parent).addComponent(child);
        } else {
            parent.add(child);
        }
    }

    private static void bindTag(DefaultInteractionController c, Component target, String property, String path) {
        SimpleBoundTagAdapter adapter = new SimpleBoundTagAdapter();
        adapter.setTarget(target);
        adapter.setTargetPropertyName(property);
        adapter.setTagPathString(path);
        c.setPropertyAdapter(target, property, adapter);
    }

    private static void bindProperty(
        DefaultInteractionController c, Component target, String property, Component source, String sourceProperty) {

        SimpleBoundPropertyAdapter adapter = new SimpleBoundPropertyAdapter();
        adapter.setTarget(target);
        adapter.setTargetPropertyName(property);
        adapter.setInteraction(new PropertyInteractionDescriptor(source, adapter, adapter, sourceProperty));
        c.setPropertyAdapter(target, property, adapter);
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }
}
