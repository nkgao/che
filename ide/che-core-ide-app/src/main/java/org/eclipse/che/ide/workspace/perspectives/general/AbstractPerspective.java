/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.workspace.perspectives.general;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.api.constraints.Constraints;
import org.eclipse.che.ide.api.event.ActivePartChangedEvent;
import org.eclipse.che.ide.api.event.ActivePartChangedHandler;
import org.eclipse.che.ide.api.mvp.Presenter;
import org.eclipse.che.ide.api.parts.PartPresenter;
import org.eclipse.che.ide.api.parts.PartStack;
import org.eclipse.che.ide.api.parts.PartStackType;
import org.eclipse.che.ide.api.parts.PartStackView;
import org.eclipse.che.ide.api.parts.Perspective;
import org.eclipse.che.ide.workspace.PartStackPresenterFactory;
import org.eclipse.che.ide.workspace.PartStackViewFactory;
import org.eclipse.che.ide.workspace.WorkBenchControllerFactory;
import org.eclipse.che.ide.workspace.WorkBenchPartController;
import org.eclipse.che.providers.DynaProvider;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.ide.api.parts.PartStackType.INFORMATION;
import static org.eclipse.che.ide.api.parts.PartStackType.NAVIGATION;
import static org.eclipse.che.ide.api.parts.PartStackType.TOOLING;
import static org.eclipse.che.ide.api.parts.PartStackView.TabPosition.BELOW;
import static org.eclipse.che.ide.api.parts.PartStackView.TabPosition.LEFT;
import static org.eclipse.che.ide.api.parts.PartStackView.TabPosition.RIGHT;

/**
 * The class which contains general business logic for all perspectives.
 *
 * @author Dmitry Shnurenko
 */
//TODO need rewrite this, remove direct dependency on PerspectiveViewImpl and other GWT Widgets
public abstract class AbstractPerspective implements Presenter, Perspective, ActivePartChangedHandler {

    private enum State {
        NORMAL,
        MAXIMIZED_LEFT_PART,
        MAXIMIZED_RIGHT_PART,
        MAXIMIZED_BOTTOM_PART,
        MAXIMIZED_CENTRAL_PART
    }

    protected final Map<PartStackType, PartStack> partStacks;
    protected final PerspectiveViewImpl           view;

    private final String                  perspectiveId;
    private final DynaProvider            dynaProvider;
    private final WorkBenchPartController leftPartController;
    private final WorkBenchPartController rightPartController;
    private final WorkBenchPartController belowPartController;

    private double        leftPartSize;
    private double        rightPartSize;
    private double        belowPartSize;

    private State         layoutState = State.NORMAL;

    private PartPresenter activePart;
    private PartPresenter activePartBeforeChangePerspective;

    protected AbstractPerspective(@NotNull String perspectiveId,
                                  @NotNull PerspectiveViewImpl view,
                                  @NotNull PartStackPresenterFactory stackPresenterFactory,
                                  @NotNull PartStackViewFactory partViewFactory,
                                  @NotNull WorkBenchControllerFactory controllerFactory,
                                  @NotNull EventBus eventBus,
                                  @NotNull DynaProvider dynaProvider) {
        this.view = view;
        this.perspectiveId = perspectiveId;
        this.dynaProvider = dynaProvider;
        this.partStacks = new HashMap<>();

        PartStackView navigationView = partViewFactory.create(LEFT, view.getLeftPanel());

        leftPartController = controllerFactory.createController(view.getSplitPanel(), view.getNavigationPanel());
        PartStack navigationPartStack = stackPresenterFactory.create(navigationView, leftPartController);
        partStacks.put(NAVIGATION, navigationPartStack);

        PartStackView informationView = partViewFactory.create(BELOW, view.getBottomPanel());

        belowPartController = controllerFactory.createController(view.getSplitPanel(), view.getInformationPanel());
        PartStack informationStack = stackPresenterFactory.create(informationView, belowPartController);
        partStacks.put(INFORMATION, informationStack);

        PartStackView toolingView = partViewFactory.create(RIGHT, view.getRightPanel());

        rightPartController = controllerFactory.createController(view.getSplitPanel(), view.getToolPanel());
        PartStack toolingPartStack = stackPresenterFactory.create(toolingView, rightPartController);
        partStacks.put(TOOLING, toolingPartStack);

        eventBus.addHandler(ActivePartChangedEvent.TYPE, this);
    }

    /**
     * Opens previous active tab on current perspective.
     *
     * @param partStackType
     *         part type on which need open previous active part
     */
    protected void openActivePart(@NotNull PartStackType partStackType) {
        PartStack partStack = partStacks.get(partStackType);
        partStack.openPreviousActivePart();
    }

    @Override
    public void storeState() {
        activePartBeforeChangePerspective = activePart;

        if (activePartBeforeChangePerspective != null) {
            activePartBeforeChangePerspective.storeState();
        }
    }

    @Override
    public void restoreState() {
        if (activePartBeforeChangePerspective != null) {
            setActivePart(activePartBeforeChangePerspective);
            activePartBeforeChangePerspective.restoreState();
        }
    }

    @Override
    public void onActivePartChanged(ActivePartChangedEvent event) {
        activePart = event.getActivePart();
    }

    /** {@inheritDoc} */
    @Override
    public void removePart(@NotNull PartPresenter part) {
        PartStack destPartStack = findPartStackByPart(part);
        if (destPartStack != null) {
            destPartStack.removePart(part);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void hidePart(@NotNull PartPresenter part) {
        PartStack destPartStack = findPartStackByPart(part);
        if (destPartStack != null) {
            destPartStack.hidePart(part);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void maximizeCentralPart() {
        if (layoutState == State.MAXIMIZED_CENTRAL_PART) {
            return;
        }

        leftPartSize = leftPartController.getSize();
        rightPartSize = rightPartController.getSize();
        belowPartSize = belowPartController.getSize();

        leftPartController.setHidden(true);
        rightPartController.setHidden(true);
        belowPartController.setHidden(true);

        layoutState = State.MAXIMIZED_CENTRAL_PART;
    }

    /** {@inheritDoc} */
    @Override
    public void maximizeBottomPart() {
        if (layoutState == State.MAXIMIZED_BOTTOM_PART) {
            return;
        }

        leftPartSize = leftPartController.getSize();
        rightPartSize = rightPartController.getSize();
        belowPartSize = belowPartController.getSize();

        leftPartController.setHidden(true);
        rightPartController.setHidden(true);
        belowPartController.maximize();

        layoutState = State.MAXIMIZED_BOTTOM_PART;
    }

    /** {@inheritDoc} */
    @Override
    public void restoreParts() {
        if (layoutState == State.NORMAL) {
            return;
        }

        leftPartController.setSize(leftPartSize);
        rightPartController.setSize(rightPartSize);
        belowPartController.setSize(belowPartSize);

        layoutState = State.NORMAL;
    }

    /** {@inheritDoc} */
    @Override
    public void setActivePart(@NotNull PartPresenter part) {
        PartStack destPartStack = findPartStackByPart(part);
        if (destPartStack != null) {
            destPartStack.setActivePart(part);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setActivePart(@NotNull PartPresenter part, @NotNull PartStackType type) {
        PartStack destPartStack = partStacks.get(type);
        destPartStack.setActivePart(part);
    }

    /**
     * Find parent PartStack for given Part
     *
     * @param part
     *         part for which need find parent
     * @return Parent PartStackPresenter or null if part not registered
     */
    public PartStack findPartStackByPart(@NotNull PartPresenter part) {
        for (PartStackType partStackType : PartStackType.values()) {

            if (partStacks.get(partStackType).containsPart(part)) {
                return partStacks.get(partStackType);
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void addPart(@NotNull PartPresenter part, @NotNull PartStackType type) {
        addPart(part, type, null);
    }

    /** {@inheritDoc} */
    @Override
    public void addPart(@NotNull PartPresenter part, @NotNull PartStackType type, @Nullable Constraints constraint) {
        PartStack destPartStack = partStacks.get(type);

        List<String> rules = part.getRules();

        if (rules.isEmpty() && !destPartStack.containsPart(part)) {
            destPartStack.addPart(part, constraint);

            return;
        }

        if (rules.contains(perspectiveId)) {
            destPartStack.addPart(part, constraint);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public PartStack getPartStack(@NotNull PartStackType type) {
        return partStacks.get(type);
    }

    @Override
    public JsonObject getState() {
        JsonObject state = Json.createObject();
        JsonObject partStacks = Json.createObject();
        state.put("ACTIVE_PART", activePart.getClass().getName());
        state.put("PART_STACKS", partStacks);


        partStacks.put(PartStackType.INFORMATION.name(), getPartStackState(this.partStacks.get(INFORMATION), belowPartController));
        partStacks.put(PartStackType.NAVIGATION.name(), getPartStackState(this.partStacks.get(NAVIGATION), leftPartController));
        partStacks.put(PartStackType.TOOLING.name(), getPartStackState(this.partStacks.get(TOOLING), rightPartController));

        return state;
    }

    private JsonObject getPartStackState(PartStack partStack, WorkBenchPartController partController) {
        JsonObject state = Json.createObject();
        state.put("SIZE", partController.getSize());
        if (partStack.getParts().isEmpty()) {
            state.put("HIDDEN", true);
        } else {
            if (partStack.getActivePart() != null) {
                state.put("ACTIVE_PART", partStack.getActivePart().getClass().getName());
            }
            state.put("HIDDEN", partController.isHidden());
            JsonArray parts = Json.createArray();
            state.put("PARTS", parts);
            int i = 0;
            for (PartPresenter entry : partStack.getParts()) {
                JsonObject presenterState = Json.createObject();
                presenterState.put("CLASS", entry.getClass().getName());
                parts.set(i++, presenterState);
            }
        }
        return state;
    }

    @Override
    public void loadState(@NotNull JsonObject state) {
        if (state.hasKey("PART_STACKS")) {
            JsonObject part_stacks = state.getObject("PART_STACKS");
            List<PartPresenter> activeParts = new ArrayList<>();
            for (String partStackType : part_stacks.keys()) {
                JsonObject partStack = part_stacks.getObject(partStackType);
                switch (PartStackType.valueOf(partStackType)) {
                    case INFORMATION:
                        belowPartSize = restorePartController(partStacks.get(INFORMATION), belowPartController, partStack, activeParts);
                        break;
                    case NAVIGATION:
                        leftPartSize = restorePartController(partStacks.get(NAVIGATION), leftPartController, partStack, activeParts);
                        break;
                    case TOOLING:
                        rightPartSize = restorePartController(partStacks.get(TOOLING), rightPartController, partStack, activeParts);
                        break;
                }
            }
            for (PartPresenter part : activeParts) {
                setActivePart(part);
            }
        }
        if (state.hasKey("ACTIVE_PART")) {
            String activePart = state.getString("ACTIVE_PART");
            Provider<PartPresenter> provider = dynaProvider.getProvider(activePart);
            if (provider != null) {
                setActivePart(provider.get());
            }
        }

    }

    private double restorePartController(PartStack stack, WorkBenchPartController controller, JsonObject partStack,
                                         List<PartPresenter> activeParts) {
        double size = 0;
        if (partStack.hasKey("SIZE")) {
            size = partStack.getNumber("SIZE");
            controller.setSize(size);
        }

        if (partStack.hasKey("HIDDEN")) {
            controller.setHidden(partStack.getBoolean("HIDDEN"));
        }

        if (partStack.hasKey("PARTS")) {
            JsonArray parts = partStack.get("PARTS");
            for (int i = 0; i < parts.length(); i++) {
                JsonObject value = parts.get(i);
                if (value.hasKey("CLASS")) {
                    String className = value.getString("CLASS");
                    Provider<PartPresenter> provider = dynaProvider.getProvider(className);
                    if (provider != null) {
                        PartPresenter partPresenter = provider.get();
                        if (!stack.containsPart(partPresenter)) {
                            stack.addPart(partPresenter);
                        }
                    }
                }
            }
        }

        //hide part stack if we cannot restore opened parts
        if (stack.getParts().isEmpty()) {
            controller.setHidden(true);
        }

        if (partStack.hasKey("ACTIVE_PART")) {
            String className = partStack.getString("ACTIVE_PART");
            Provider<PartPresenter> provider = dynaProvider.getProvider(className);
            if (provider != null) {
                activeParts.add(provider.get());
            }
        }
        return size;
    }
}
