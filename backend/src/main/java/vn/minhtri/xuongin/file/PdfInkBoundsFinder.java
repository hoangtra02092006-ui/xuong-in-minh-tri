package vn.minhtri.xuongin.file;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDVectorFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

/**
 * Finds the bounds of everything a PDF page draws by reading its content stream (spec 7.1).
 *
 * <p>Vector, not pixels: a shape filled white is still artwork and must be kept, which a pixel
 * scan would miss. All drawing operations (paths, images, text, shadings) go into ONE union box,
 * so several small figures keep their relative positions. Every estimate errs outward.
 */
final class PdfInkBoundsFinder extends PDFGraphicsStreamEngine {

    /** Thrown to stop reading a page with too many drawing operations. */
    static final class TooComplexException extends RuntimeException {
        TooComplexException() {
            super(null, null, false, false);
        }
    }

    private final int maxShapes;
    private final GeneralPath path = new GeneralPath();
    private Integer pendingClipRule;
    private Rectangle2D bounds;
    private int shapes;

    PdfInkBoundsFinder(PDPage page, int maxShapes) {
        super(page);
        this.maxShapes = maxShapes;
    }

    /**
     * @return ink bounds in page space (points, origin bottom-left), or null for a blank page
     * @throws TooComplexException when the page has more than {@code maxShapes} drawing operations
     */
    Rectangle2D find() throws IOException {
        processPage(getPage());
        return bounds;
    }

    private void include(Rectangle2D box) {
        if (box == null || box.getWidth() < 0 || box.getHeight() < 0) {
            return;
        }
        if (++shapes > maxShapes) {
            throw new TooComplexException();
        }
        Rectangle2D effective = box;
        // Bounds of every active clip path: a superset of the real clip, so still errs outward.
        for (Path2D clip : getGraphicsState().getCurrentClippingPaths()) {
            effective = effective.createIntersection(clip.getBounds2D());
            if (effective.getWidth() < 0 || effective.getHeight() < 0) {
                return;
            }
        }
        bounds = bounds == null ? effective : bounds.createUnion(effective);
    }

    // ---- path construction (coordinates arrive already in page space) ----

    @Override
    public void moveTo(float x, float y) {
        path.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        path.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        path.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        path.moveTo(p0.getX(), p0.getY());
        path.lineTo(p1.getX(), p1.getY());
        path.lineTo(p2.getX(), p2.getY());
        path.lineTo(p3.getX(), p3.getY());
        path.closePath();
    }

    @Override
    public Point2D getCurrentPoint() {
        return path.getCurrentPoint();
    }

    @Override
    public void closePath() {
        path.closePath();
    }

    // ---- painting ----

    @Override
    public void fillPath(int windingRule) {
        includePath(-1);
        endPath();
    }

    @Override
    public void strokePath() {
        includePath(strokeHalfWidth());
        endPath();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        includePath(strokeHalfWidth());
        endPath();
    }

    @Override
    public void endPath() {
        // "W" only records the clip; it takes effect when the path ends (PDF 32000 8.5.4).
        if (pendingClipRule != null) {
            path.setWindingRule(pendingClipRule);
            getGraphicsState().intersectClippingPath(new Area(path));
            pendingClipRule = null;
        }
        path.reset();
    }

    @Override
    public void clip(int windingRule) {
        pendingClipRule = windingRule == Path2D.WIND_EVEN_ODD ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
    }

    /** @param outset half line width for strokes; negative for a plain fill, which paints nothing when it has no area */
    private void includePath(double outset) {
        if (path.getCurrentPoint() == null) {
            return;
        }
        Rectangle2D box = path.getBounds2D();
        if (outset < 0 && (box.getWidth() <= 0 || box.getHeight() <= 0)) {
            return;
        }
        include(grow(box, outset));
    }

    /** Half the line width in page space; the larger axis scale so the box errs outward. */
    private double strokeHalfWidth() {
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        double scale = Math.max(
                Math.hypot(ctm.getScaleX(), ctm.getShearY()),
                Math.hypot(ctm.getShearX(), ctm.getScaleY()));
        return Math.max(getGraphicsState().getLineWidth() * scale, 0) / 2;
    }

    @Override
    public void drawImage(PDImage image) {
        // Images are painted into the unit square mapped by the CTM.
        AffineTransform ctm = getGraphicsState().getCurrentTransformationMatrix().createAffineTransform();
        include(ctm.createTransformedShape(new Rectangle2D.Double(0, 0, 1, 1)).getBounds2D());
    }

    @Override
    public void shadingFill(COSName shadingName) {
        // "sh" paints the whole current clip area; the clip is applied in include().
        include(getPage().getCropBox().toGeneralPath().getBounds2D());
    }

    // ---- text ----

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement) throws IOException {
        RenderingMode mode = getGraphicsState().getTextState().getRenderingMode();
        if (mode.isFill() || mode.isStroke()) {
            Rectangle2D box = glyphBox(textRenderingMatrix, font, code, displacement);
            if (box != null) {
                include(grow(box, mode.isStroke() ? strokeHalfWidth() : 0));
            }
        }
        super.showGlyph(textRenderingMatrix, font, code, displacement);
    }

    /**
     * Glyph box in page space, transformed like the renderer does (text rendering matrix x font matrix).
     * Exact outline when the font has one, otherwise the font bounding box, which is never smaller
     * than the glyph. Null for an empty glyph (space).
     */
    private static Rectangle2D glyphBox(Matrix textRenderingMatrix, PDFont font, int code, Vector displacement) {
        AffineTransform textToPage = textRenderingMatrix.createAffineTransform();
        AffineTransform glyphToPage = new AffineTransform(textToPage);
        try {
            glyphToPage.concatenate(font.getFontMatrix().createAffineTransform());
        } catch (RuntimeException e) {
            glyphToPage.scale(0.001, 0.001);
        }
        if (font instanceof PDVectorFont vector) {
            try {
                GeneralPath outline = vector.getNormalizedPath(code);
                if (outline != null) {
                    if (outline.getBounds2D().isEmpty()) {
                        return null;
                    }
                    return glyphToPage.createTransformedShape(outline).getBounds2D();
                }
            } catch (IOException | RuntimeException ignored) {
                // Broken font program: fall back to the font box below.
            }
        }
        try {
            BoundingBox fontBox = font.getBoundingBox();
            if (fontBox != null && fontBox.getHeight() > 0 && fontBox.getWidth() > 0) {
                Rectangle2D glyphSpace = new Rectangle2D.Double(fontBox.getLowerLeftX(), fontBox.getLowerLeftY(),
                        fontBox.getWidth(), fontBox.getHeight());
                return glyphToPage.createTransformedShape(glyphSpace).getBounds2D();
            }
        } catch (IOException | RuntimeException ignored) {
            // No usable metrics: rough estimate below.
        }
        Rectangle2D rough = new Rectangle2D.Double(0, -0.25, Math.max(displacement.getX(), 0.5), 1.25);
        return textToPage.createTransformedShape(rough).getBounds2D();
    }

    private static Rectangle2D grow(Rectangle2D box, double outset) {
        if (outset <= 0) {
            return box;
        }
        return new Rectangle2D.Double(box.getX() - outset, box.getY() - outset,
                box.getWidth() + 2 * outset, box.getHeight() + 2 * outset);
    }
}
