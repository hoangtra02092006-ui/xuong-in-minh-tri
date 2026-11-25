package vn.minhtri.xuongin.nesting;

import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.engine.EngineException;
import vn.minhtri.xuongin.engine.EngineItem;
import vn.minhtri.xuongin.engine.EngineParams;
import vn.minhtri.xuongin.engine.EngineResult;
import vn.minhtri.xuongin.engine.NestingEngine;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.shared.Units;

/** {@link NestingPlanner} backed by the real {@link NestingEngine} (spec 8). */
@Component
public class EngineNestingPlanner implements NestingPlanner {

    @Override
    public NestResult plan(NestRequest req, Map<String, StoredFile> files, IntConsumer progressPercent) {
        List<EngineItem> items = NestRequestValidator.resolve(req, files).stream()
                .map(EngineNestingPlanner::toEngineItem)
                .toList();
        EngineParams params = new EngineParams(
                EngineResultMapper.toEngineMode(req.modeOrDefault()),
                Units.mmToCenti(req.sheetWidthMm()),
                Units.mmToCenti(req.marginMm()),
                Units.mmToCenti(req.gapMm()),
                req.maxSheetLengthMm() == null ? null : Units.mmToCenti(req.maxSheetLengthMm()),
                req.allowRotateGlobal());
        try {
            EngineResult result = new NestingEngine().nest(items, params,
                    (tried, total) -> progressPercent.accept(percent(tried, total)));
            return EngineResultMapper.toNestResult(items, params, result);
        } catch (EngineException e) {
            throw EngineResultMapper.toApiException(e);
        }
    }

    static int percent(long tried, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, tried * 100 / total));
    }

    private static EngineItem toEngineItem(ResolvedItem item) {
        return new EngineItem(item.fileId(), item.label(), item.categoryIndex(), Units.mmToCenti(item.widthMm()),
                Units.mmToCenti(item.heightMm()), item.quantity(), item.allowRotate(), item.shape());
    }
}
