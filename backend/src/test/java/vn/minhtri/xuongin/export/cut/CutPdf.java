package vn.minhtri.xuongin.export.cut;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDSeparation;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import vn.minhtri.xuongin.shared.Units;

/** Reads a cut PDF back: page size, layers, and what each layer draws (in page mm). */
final class CutPdf {

    /** One closed path drawn in a layer, points in page mm. */
    record Path(String layer, String strokeColourSpace, List<double[]> points) {
        double[] bounds() {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (double[] p : points) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            return new double[] {minX, minY, maxX, maxY};
        }
    }

    /** Filled rectangle in page mm. */
    record Rect(String layer, double x, double y, double w, double h) {}

    final double widthMm;
    final double lengthMm;
    final List<String> layers = new ArrayList<>();
    final Map<String, Boolean> layerVisible = new TreeMap<>();
    final List<Path> strokedPaths = new ArrayList<>();
    final List<Rect> filledRects = new ArrayList<>();
    final List<String> strokedOutsideCutLayer = new ArrayList<>();
    final List<String> xobjectsDrawn = new ArrayList<>();
    boolean anyFill;
    String separationName;
    String separationAlternate;

    private CutPdf(double widthMm, double lengthMm) {
        this.widthMm = widthMm;
        this.lengthMm = lengthMm;
    }

    static CutPdf read(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDPage page = doc.getPage(0);
            CutPdf pdf = new CutPdf(
                    Units.ptToMm(page.getMediaBox().getWidth()), Units.ptToMm(page.getMediaBox().getHeight()));
            PDOptionalContentProperties oc = doc.getDocumentCatalog().getOCProperties();
            if (oc != null) {
                for (PDOptionalContentGroup group : oc.getOptionalContentGroups()) {
                    pdf.layers.add(group.getName());
                    pdf.layerVisible.put(group.getName(), oc.isGroupEnabled(group));
                }
            }
            pdf.parse(page);
            return pdf;
        }
    }

    private void parse(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        List<Object> tokens = new PDFStreamParser(page).parse();
        List<COSBase> operands = new ArrayList<>();
        String layer = null;
        String strokeSpace = null;
        List<List<double[]>> pending = new ArrayList<>();
        List<double[]> current = null;
        List<double[]> rects = new ArrayList<>();
        for (Object token : tokens) {
            if (!(token instanceof Operator op)) {
                operands.add((COSBase) token);
                continue;
            }
            switch (op.getName()) {
                case "BDC" -> {
                    PDPropertyList props = resources.getProperties((COSName) operands.get(1));
                    layer = ((PDOptionalContentGroup) props).getName();
                }
                case "EMC" -> layer = null;
                case "CS" -> {
                    PDColorSpace space = resources.getColorSpace((COSName) operands.get(0));
                    strokeSpace = space.getName();
                    if (space instanceof PDSeparation sep) {
                        separationName = sep.getColorantName();
                        separationAlternate = sep.getAlternateColorSpace().getName();
                    }
                }
                case "m" -> {
                    current = new ArrayList<>();
                    current.add(point(operands));
                    pending.add(current);
                }
                case "l" -> current.add(point(operands));
                case "re" -> rects.add(new double[] {
                    num(operands, 0), num(operands, 1), num(operands, 2), num(operands, 3)});
                case "S", "s" -> {
                    if (!CutComposer.CUT_LAYER.equals(layer)) {
                        strokedOutsideCutLayer.add(String.valueOf(layer));
                    }
                    for (List<double[]> path : pending) {
                        strokedPaths.add(new Path(layer, strokeSpace, path));
                    }
                    pending.clear();
                    rects.clear();
                }
                case "f", "F", "f*", "B", "B*", "b", "b*" -> {
                    anyFill = true;
                    for (double[] r : rects) {
                        filledRects.add(new Rect(layer, Units.ptToMm(r[0]), Units.ptToMm(r[1]),
                                Units.ptToMm(r[2]), Units.ptToMm(r[3])));
                    }
                    rects.clear();
                    pending.clear();
                }
                case "Do" -> xobjectsDrawn.add(String.valueOf(layer));
                default -> {
                    // other operators do not matter here
                }
            }
            operands.clear();
        }
    }

    List<Path> cutPaths() {
        return strokedPaths.stream().filter(p -> CutComposer.CUT_LAYER.equals(p.layer())).toList();
    }

    List<Rect> markRects() {
        return filledRects.stream().filter(r -> CutComposer.MARK_LAYER.equals(r.layer())).toList();
    }

    private static double[] point(List<COSBase> operands) {
        return new double[] {Units.ptToMm(num(operands, 0)), Units.ptToMm(num(operands, 1))};
    }

    private static double num(List<COSBase> operands, int i) {
        return ((COSNumber) operands.get(i)).floatValue();
    }
}
