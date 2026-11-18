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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
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

    /** Tag configurations a plant Gateway might have, read the way the module reads the real ones. */
    private static final FakeTagConfig GATEWAY = new FakeTagConfig()
        .opc("[default]Site/Tank1/Level", "ns=1;s=[CP300]F8:23")
        .reference("[default]Site/Tank1/HighAlarm", "[.]../PLC/HighSwitch")
        .opc("[default]Site/PLC/HighSwitch", "ns=1;s=[CP100]Global.F8.F8[17]")
        .memory("[default]Site/Tank1/Mode")
        .expression("[default]Site/TotalEffluent", "{[.]FIT704} + {[.]FIT718}")
        .opc("[default]Site/FIT704", "ns=1;s=[CP400]Global.F8.F8[119]")
        .opc("[default]Site/FIT718", "ns=1;s=[CP400]Global.F8.F8[121]");

    private static TagSources newSources() {
        return new TagSourceLookup(GATEWAY, () -> "default", System::currentTimeMillis, SwingUtilities::invokeLater);
    }

    public static void main(String[] args) throws Exception {
        boolean check = args.length > 0 && "--check".equals(args[0]);
        File outDir = new File(args.length > 1 ? args[1] : "build/demo");

        AtomicReference<TooltipDemo> demo = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> demo.set(new TooltipDemo()));
        // Settings stay in memory, so the demo never changes the user's real Tag Hover settings.
        TagHoverTooltip.install(
            ComponentLocator.DEFAULT, SettingsStore.inMemory(TagHoverSettings.DEFAULT), newSources());

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
        root.setPreferredSize(new Dimension(1000, 640));

        PMILabel level = box(root, "Tank 1 Level", "Tank 1 Level: 42.5 gal", 30, 30);
        bindTag(controller, level, "text", "[default]Site/Tank1/Level");
        checks.add(new Check("direct-tag", level, "[default]Site/Tank1/Level"));
        checks.add(new Check("plc-address", level, "PLC</font> [CP300]F8:23"));

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
        checks.add(new Check("reference-tag", status, "PLC</font> [CP100]Global.F8.F8[17]"));
        checks.add(new Check("memory-tag", status, "memory tag, no PLC"));

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

        PMILabel total = box(root, "Total Effluent", "Total Effluent: 3.2 MGD", 30, 370);
        bindTag(controller, total, "text", "[default]Site/TotalEffluent");
        checks.add(new Check("expression-tag", total, "PLC</font> [CP400]Global.F8.F8[121]"));

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

        failures += hotkeyChecks(outDir);
        failures += settingsWindowChecks(outDir);

        System.out.println(failures == 0 ? "All demo checks passed." : failures + " demo check(s) failed.");
        return failures;
    }

    /**
     * Opens the settings window the way Ctrl+Comma does, unticks two details, saves, and checks the tooltip leaves
     * them out. Returns the number of failures.
     */
    private int settingsWindowChecks(File outDir) throws Exception {
        Component level = target("direct-tag");
        SettingsStore store = SettingsStore.inMemory(TagHoverSettings.DEFAULT);
        TagHoverTooltip.install(ComponentLocator.DEFAULT, store, newSources());
        onEdt(() -> hover(level, true));
        Thread.sleep(250);
        boolean before = shown("[CP300]F8:23") && shown("Root Container.Tank 1 Level");
        key(KeyEvent.KEY_RELEASED, KeyEvent.VK_CONTROL, 0, System.currentTimeMillis());

        TagHoverTooltip.openSettings();
        JDialog dialog = null;
        for (int i = 0; i < 40 && dialog == null; i++) {
            Thread.sleep(100);
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && "Tag Hover Settings".equals(d.getTitle())) {
                    dialog = d;
                }
            }
        }
        if (dialog == null) {
            return report("settings-window-opens", false);
        }
        JDialog settings = dialog;
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        onEdt(() -> {
            paint(settings.getRootPane(), new File(outDir, "30-settings-window.png"));
            JCheckBox plc = find(settings, JCheckBox.class, TagHoverSettings.Detail.PLC_ADDRESS.label());
            JCheckBox path = find(settings, JCheckBox.class, TagHoverSettings.Detail.COMPONENT_PATH.label());
            JButton save = find(settings, JButton.class, "Save");
            found.set(plc != null && path != null && save != null && plc.isSelected() && path.isSelected());
            if (found.get()) {
                plc.doClick();
                path.doClick();
                save.doClick();
            } else {
                settings.dispose();
            }
        });
        Thread.sleep(250);

        TagHoverSettings saved = store.load();
        boolean savedOk = found.get()
            && !saved.shows(TagHoverSettings.Detail.PLC_ADDRESS)
            && !saved.shows(TagHoverSettings.Detail.COMPONENT_PATH)
            && saved.shows(TagHoverSettings.Detail.TAG_SOURCES);
        onEdt(() -> hover(level, true));
        Thread.sleep(250);
        boolean after = shown("[default]Site/Tank1/Level") && !shown("[CP300]") && !shown("Root Container.Tank 1");
        onEdt(() -> screenshot(new File(outDir, "31-details-off.png")));
        key(KeyEvent.KEY_RELEASED, KeyEvent.VK_CONTROL, 0, System.currentTimeMillis());

        install(KeyEvent.VK_CONTROL, TagHoverSettings.Mode.HOLD);
        return report("settings-checkboxes", before && savedOk && after);
    }

    private Component target(String checkName) {
        return checks.stream().filter(c -> c.name().equals(checkName)).findFirst().orElseThrow().target();
    }

    private static <T extends Component> T find(Container in, Class<T> type, String text) {
        for (Component c : in.getComponents()) {
            if (type.isInstance(c) && c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) {
                return type.cast(c);
            }
            if (c instanceof Container sub) {
                T hit = find(sub, type, text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** Other hotkeys, toggle mode and shortcuts. Returns the number of failures. */
    private int hotkeyChecks(File outDir) throws Exception {
        Component first = target("direct-tag");
        Component second = target("indirect-tag");
        int failures = 0;
        long t = System.currentTimeMillis();

        // Hold Ctrl, press C (Ctrl+C): the tooltip goes and stays away until Ctrl is let go.
        install(KeyEvent.VK_CONTROL, TagHoverSettings.Mode.HOLD);
        onEdt(() -> hover(first, true));
        boolean before = shown("Level");
        key(KeyEvent.KEY_PRESSED, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, t);
        onEdt(() -> hover(first, true));
        boolean away = !shown("Level");
        key(KeyEvent.KEY_RELEASED, KeyEvent.VK_CONTROL, 0, t + 50);
        onEdt(() -> hover(first, true));
        failures += report("hold-ctrl-c-stays-away", before && away && shown("Level"));

        // F2 held: shows while it is down, and F2 itself is consumed so it does nothing else.
        install(KeyEvent.VK_F2, TagHoverSettings.Mode.HOLD);
        onEdt(() -> hover(first, false));
        boolean quietBefore = shown(null);
        boolean consumed = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_F2, 0, t);
        boolean shownOnPress = shown("Level");
        onEdt(() -> hover(second, false));
        boolean followsMouse = shown("Motors");
        key(KeyEvent.KEY_RELEASED, KeyEvent.VK_F2, 0, t + 300);
        failures += report("hold-f2", quietBefore && consumed && shownOnPress && followsMouse && shown(null));

        // Toggle mode: a tap of Ctrl turns hover mode on, the tooltip follows the mouse with no key held,
        // and Esc turns it off.
        install(KeyEvent.VK_CONTROL, TagHoverSettings.Mode.TOGGLE);
        onEdt(() -> hover(first, false));
        boolean offAtFirst = shown(null);
        tap(KeyEvent.VK_CONTROL, t, 100);
        boolean onAfterTap = shown("Esc to stop");
        onEdt(() -> hover(second, false));
        boolean follows = shown("Motors");
        onEdt(() -> screenshot(new File(outDir, "20-toggle-mode.png")));
        boolean escConsumed = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, 0, t + 200);
        boolean offAfterEsc = shown(null);
        onEdt(() -> hover(first, false));
        failures += report("toggle-tap-and-esc",
            offAtFirst && onAfterTap && follows && escConsumed && offAfterEsc && shown(null));

        // Toggle mode: tapping again turns it off.
        tap(KeyEvent.VK_CONTROL, t + 1000, 100);
        boolean on = shown("Level");
        tap(KeyEvent.VK_CONTROL, t + 2000, 100);
        failures += report("toggle-tap-twice", on && shown(null));

        // Toggle mode: Ctrl held a long time, or used for Ctrl+C, is not a tap.
        tap(KeyEvent.VK_CONTROL, t + 3000, 900);
        boolean longHoldIgnored = shown(null);
        key(KeyEvent.KEY_PRESSED, KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK, t + 4000);
        key(KeyEvent.KEY_PRESSED, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, t + 4050);
        key(KeyEvent.KEY_RELEASED, KeyEvent.VK_CONTROL, 0, t + 4100);
        failures += report("toggle-not-a-tap", longHoldIgnored && shown(null));

        // Toggle mode: a click hides the tooltip, and the next mouse move brings it back.
        tap(KeyEvent.VK_CONTROL, t + 5000, 100);
        onEdt(() -> first.dispatchEvent(new MouseEvent(first, MouseEvent.MOUSE_PRESSED, t + 5200, 0,
            first.getWidth() / 2, first.getHeight() / 2, 1, false, MouseEvent.BUTTON1)));
        boolean hiddenOnClick = shown(null);
        onEdt(() -> hover(first, false));
        failures += report("toggle-click", hiddenOnClick && shown("Level"));
        tap(KeyEvent.VK_CONTROL, t + 6000, 100);

        install(KeyEvent.VK_CONTROL, TagHoverSettings.Mode.HOLD);
        return failures;
    }

    private static void install(int keyCode, TagHoverSettings.Mode mode) {
        TagHoverTooltip.install(
            ComponentLocator.DEFAULT, SettingsStore.inMemory(new TagHoverSettings(keyCode, mode)), newSources());
    }

    private boolean key(int id, int keyCode, int modifiers, long when) throws Exception {
        AtomicReference<Boolean> consumed = new AtomicReference<>(false);
        onEdt(() -> consumed.set(TagHoverTooltip.dispatch(
            new KeyEvent(frame, id, when, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED))));
        return consumed.get();
    }

    private void tap(int keyCode, long when, long heldMillis) throws Exception {
        int mask = keyCode == KeyEvent.VK_CONTROL ? InputEvent.CTRL_DOWN_MASK : 0;
        key(KeyEvent.KEY_PRESSED, keyCode, mask, when);
        key(KeyEvent.KEY_RELEASED, keyCode, 0, when + heldMillis);
    }

    /** With {@code null}: no tooltip is showing. Otherwise: a tooltip containing {@code text} is showing. */
    private static boolean shown(String text) throws Exception {
        AtomicReference<String> html = new AtomicReference<>();
        onEdt(() -> html.set(TagHoverTooltip.shownHtml()));
        return text == null ? html.get() == null : html.get() != null && html.get().contains(text);
    }

    private static int report(String name, boolean ok) {
        System.out.printf("%s  %s%n", ok ? "PASS" : "FAIL", name);
        return ok ? 0 : 1;
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
        paint(frame.getRootPane(), file);
    }

    private static void paint(Container rootPane, File file) {
        try {
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
