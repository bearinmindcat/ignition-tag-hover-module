package io.github.bearinmindcat.taghover.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bearinmindcat.taghover.client.TagReport.BindingLine;
import io.github.bearinmindcat.taghover.client.TagReport.Section;
import com.inductiveautomation.factorypmi.application.binding.DefaultInteractionController;
import com.inductiveautomation.factorypmi.application.binding.ExpressionPropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.IndirectTagBindingAdapter;
import com.inductiveautomation.factorypmi.application.binding.PropertyInteractionDescriptor;
import com.inductiveautomation.factorypmi.application.binding.SimpleBoundPropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.SimpleBoundTagAdapter;
import com.inductiveautomation.factorypmi.application.binding.UDTTagAdapter;
import com.inductiveautomation.factorypmi.application.components.PMILabel;
import com.inductiveautomation.factorypmi.application.components.template.TemplateHolder;
import com.inductiveautomation.factorypmi.application.components.template.VisionTemplate;
import java.awt.Component;
import java.util.List;
import java.util.Set;
import com.inductiveautomation.vision.api.client.components.model.VisionContainer;
import javax.swing.JPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the inspector against real Vision binding classes from the 8.1.35 SDK. A {@link VisionTemplate} with its
 * own {@link DefaultInteractionController} stands in for a window, since both are binding roots.
 */
class BindingInspectorTest {

    private final BindingInspector inspector = new BindingInspector();

    private VisionTemplate root;
    private DefaultInteractionController controller;

    @BeforeEach
    void setUp() {
        root = new VisionTemplate();
        controller = new DefaultInteractionController();
        root.setInteractionController(controller);
    }

    @Test
    void directTagBinding() {
        PMILabel label = label("Level", root);
        bindTag(controller, label, "text", "[default]Tank1/Level");

        TagReport report = inspector.inspect(label);

        assertNotNull(report);
        assertEquals(label, report.anchor());
        BindingLine line = onlyLine(report.sections().get(0));
        assertEquals("text", line.property());
        assertEquals("tag", line.kind());
        assertEquals("[default]Tank1/Level", line.tagPath());
        assertNull(line.via());
    }

    @Test
    void hoveringAnInternalChildReportsTheVisionComponent() {
        PMILabel label = label("Level", root);
        bindTag(controller, label, "text", "[default]Tank1/Level");
        JPanel internal = new JPanel();
        label.add(internal);

        TagReport report = inspector.inspect(internal);

        assertNotNull(report);
        assertEquals(label, report.anchor());
    }

    @Test
    void indirectBindingShowsItsPattern() throws Exception {
        PMILabel label = label("Amps", root);
        IndirectTagBindingAdapter adapter = new IndirectTagBindingAdapter();
        adapter.setTarget(label);
        adapter.setTargetPropertyName("text");
        adapter.setPathParts(IndirectTagBindingAdapter.parseIndirect("[default]Motors/{1}/Amps"));
        controller.setPropertyAdapter(label, "text", adapter);

        BindingLine line = onlyLine(inspector.inspect(label).sections().get(0));

        assertEquals("indirect", line.kind());
        // Not started, so Vision has not resolved {1}; the pattern itself is what we can show.
        assertEquals("[default]Motors/{1}/Amps", line.tagPath());
    }

    @Test
    void expressionTagReferences() {
        PMILabel label = label("Status", root);
        ExpressionPropertyAdapter adapter = new ExpressionPropertyAdapter();
        adapter.setTarget(label);
        adapter.setTargetPropertyName("text");
        adapter.setExpressionSource("if({[default]Tank1/HighAlarm}, 'HIGH', tag(\"[default]Tank1/Mode\"))");
        controller.setPropertyAdapter(label, "text", adapter);

        List<BindingLine> lines = inspector.inspect(label).sections().get(0).lines();

        assertEquals(2, lines.size());
        assertEquals("[default]Tank1/HighAlarm", lines.get(0).tagPath());
        assertEquals("[default]Tank1/Mode", lines.get(1).tagPath());
        assertTrue(lines.stream().allMatch(l -> "expression".equals(l.kind())));
    }

    @Test
    void propertyBindingIsFollowedToTheTag() {
        PMILabel source = label("Source", root);
        bindTag(controller, source, "text", "[default]Tank1/Level");

        PMILabel target = label("Target", root);
        bindProperty(controller, target, "text", source, "text");

        BindingLine line = onlyLine(inspector.inspect(target).sections().get(0));

        assertEquals("text", line.property());
        assertEquals("[default]Tank1/Level", line.tagPath());
        assertNotNull(line.via());
        assertTrue(line.via().endsWith(".text"), line.via());
    }

    @Test
    void templateParameterIsTracedToTheTemplateInstance() {
        // Window > TemplateHolder "Motor 1" (TagPath param bound to a tag) > template root > label bound to the param.
        TemplateHolder holder = new TemplateHolder();
        holder.setName("Motor 1");
        addTo(root, holder);
        bindTag(controller, holder, "TagPath", "[default]Motors/M1");

        VisionTemplate template = new VisionTemplate();
        template.setName("Motor"); // A real template root is named after the template.
        DefaultInteractionController templateController = new DefaultInteractionController();
        template.setInteractionController(templateController);
        addTo(holder, template);

        PMILabel label = label("Name", template);
        bindProperty(templateController, label, "text", template, "TagPath");

        TagReport report = inspector.inspect(label);

        assertNotNull(report);
        BindingLine traced = onlyLine(report.sections().get(0));
        assertEquals("[default]Motors/M1", traced.tagPath());
        // Via the instance's parameter, not the template root's (which would read "Motor 1.Motor.TagPath").
        assertTrue(traced.via().endsWith("Motor 1.TagPath"), traced.via());
        assertFalse(traced.via().contains("Motor.TagPath"), traced.via());

        assertEquals(2, report.sections().size());
        Section templateSection = report.sections().get(1);
        assertTrue(templateSection.template());
        assertEquals("Motor 1", templateSection.title());
        assertEquals("[default]Motors/M1", onlyLine(templateSection).tagPath());
    }

    @Test
    void udtParameterMembersAreTracedToTheMemberTags() {
        // Dropping the UDT Motor1 on a window makes a template instance whose Motor parameter has a two-way UDT
        // binding; labels inside read Motor::Amps (subfolders written with dots).
        TemplateHolder holder = new TemplateHolder();
        holder.setName("MotorFace");
        addTo(root, holder);
        UDTTagAdapter udt = new UDTTagAdapter();
        udt.setTarget(holder);
        udt.setTargetPropertyName("Motor");
        udt.setTagPathString("[default]Motors/Motor1");
        udt.setBidirectional(true);
        controller.setPropertyAdapter(holder, "Motor", udt);

        VisionTemplate template = new VisionTemplate();
        template.setName("MotorFace");
        DefaultInteractionController templateController = new DefaultInteractionController();
        template.setInteractionController(templateController);
        addTo(holder, template);
        PMILabel amps = label("Amps", template);
        bindProperty(templateController, amps, "text", template, "Motor::Amps");
        PMILabel alarm = label("Alarm", template);
        bindProperty(templateController, alarm, "text", template, "Motor::Alarms.High");

        TagReport report = inspector.inspect(amps);

        BindingLine line = onlyLine(report.sections().get(0));
        assertEquals("text", line.property());
        assertEquals("[default]Motors/Motor1/Amps", line.tagPath());
        assertEquals("UDT member", line.kind());
        assertTrue(line.via().endsWith("MotorFace.Motor"), line.via());
        // The label only reads: the parameter's two-way binding doesn't make it write.
        assertFalse(line.writable());
        assertTrue(onlyLine(report.sections().get(1)).writable());

        assertEquals("[default]Motors/Motor1/Alarms/High",
            onlyLine(inspector.inspect(alarm).sections().get(0)).tagPath());
    }

    @Test
    void hoveringEmptySpaceInATemplateReportsTheInstance() {
        TemplateHolder holder = new TemplateHolder();
        holder.setName("Motor 1");
        addTo(root, holder);
        bindTag(controller, holder, "TagPath", "[default]Motors/M1");
        VisionTemplate template = new VisionTemplate();
        template.setInteractionController(new DefaultInteractionController());
        addTo(holder, template);

        TagReport report = inspector.inspect(template);

        assertNotNull(report);
        assertEquals(holder, report.anchor());
        assertEquals("[default]Motors/M1", onlyLine(report.sections().get(0)).tagPath());
    }

    @Test
    void componentWithoutBindingsSaysSo() {
        PMILabel label = label("Plain", root);

        TagReport report = inspector.inspect(label);

        assertNotNull(report);
        assertFalse(report.hasAnyLines());
        assertTrue(report.toHtml().contains("No tag bindings"));
    }

    @Test
    void componentOutsideAnyVisionWindowIsIgnored() {
        JPanel loose = new JPanel();
        loose.add(new JPanel());

        assertNull(inspector.inspect(loose.getComponent(0)));
    }

    @Test
    void expressionParsingIgnoresPropertyReferences() {
        Set<String> paths = BindingInspector.tagPathsInExpression(
            "{Root Container.Label.text} + { [~]Relative/Tag } + tag('[default]Single')");

        assertEquals(List.of("[~]Relative/Tag", "[default]Single"), List.copyOf(paths));
    }

    @Test
    void unnamedContainersAreDroppedFromPaths() {
        assertEquals("Motor 1.TagPath", BindingInspector.cleanPath("null.Motor 1.null.TagPath"));
        assertEquals("Root Container.Label", BindingInspector.cleanPath("Root Container.Label"));
        assertNull(BindingInspector.cleanPath("null"));
        assertNull(BindingInspector.cleanPath(null));
    }

    private static PMILabel label(String name, java.awt.Container parent) {
        PMILabel label = new PMILabel();
        label.setName(name);
        addTo(parent, label);
        return label;
    }

    /** Vision containers refuse {@code add()}; components go in through {@code addComponent()}. */
    private static void addTo(java.awt.Container parent, Component child) {
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

    private static BindingLine onlyLine(Section section) {
        assertEquals(1, section.lines().size(), () -> "lines: " + section.lines());
        return section.lines().get(0);
    }
}
