package net.therailwayproject.alex;

import java.awt.Graphics2D;
import java.util.function.Supplier;

import org.jdesktop.swingx.JXMapViewer;
import org.jdesktop.swingx.painter.Painter;

public interface VisiblePainter extends Painter<JXMapViewer> {
    void setVisible(boolean visible);
    boolean isVisible();
    void toggleVisible();
}

class LineOverlay implements VisiblePainter {
    private boolean visible = false;
    private final Supplier<PaintingLogic> paintingLogicSupplier;

    public LineOverlay(Supplier<PaintingLogic> paintingLogicSupplier) {
        this.paintingLogicSupplier = paintingLogicSupplier;
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
        if (visible) {
            PaintingLogic paintingLogic = paintingLogicSupplier.get();
            paintingLogic.paint(g, map, width, height);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public void toggleVisible() {
        visible = !visible;
    }
}

class CompositePainter implements Painter<JXMapViewer> {
    public VisiblePainter[] painters;

    public CompositePainter(VisiblePainter... painters) {
        this.painters = painters;
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
        for (VisiblePainter painter : painters) {
            if (painter.isVisible()) {
                painter.paint(g, map, width, height);
            }
        }
    }
}

interface PaintingLogic {
    void paint(Graphics2D g, JXMapViewer map, int width, int height);
}