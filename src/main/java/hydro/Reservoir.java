package hydro;

import java.util.Arrays;
import java.util.TreeSet;

/** 一座水库的固定参数。单位：水位m、库容亿m³、流量m³/s、出力MW。 */
public record Reservoir(
        String name, double minLevel, double maxLevel,
        double startLevel, double endLevel, double levelStep,
        double maxRelease, double maxPowerFlow, double maxPower,
        double powerCoefficient, double headLoss, Curve storageCurve, Curve tailwaterCurve) {

    public Reservoir {
        double[] values = {minLevel, maxLevel, startLevel, endLevel, levelStep,
                maxRelease, maxPowerFlow, maxPower, powerCoefficient, headLoss};
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("水库参数必须为有限数");
        }
        if (name == null || name.isBlank() || storageCurve == null || tailwaterCurve == null
                || minLevel >= maxLevel || startLevel < minLevel || startLevel > maxLevel
                || endLevel < minLevel || endLevel > maxLevel || levelStep <= 0
                || (maxLevel - minLevel) / levelStep > 10_000
                || maxRelease <= 0 || maxPowerFlow <= 0 || maxPower <= 0
                || powerCoefficient <= 0 || headLoss < 0
                || storageCurve.minX() > minLevel || storageCurve.maxX() < maxLevel
                || storageCurve.valueAt(minLevel) < 0
                || storageCurve.valueAt(maxLevel) <= storageCurve.valueAt(minLevel)) {
            throw new IllegalArgumentException("水库参数或曲线范围不合法：" + name);
        }
    }

    /** 常规网格之外，再加入精确的初末水位，避免把2598.3或2595.2取整。 */
    public double[] levels() {
        TreeSet<Double> levels = new TreeSet<>();
        int count = (int) Math.floor((maxLevel - minLevel) / levelStep);
        for (int i = 0; i <= count; i++) {
            double level = minLevel + i * levelStep;
            if (level < maxLevel && Math.abs(level - startLevel) > 1e-9
                    && Math.abs(level - endLevel) > 1e-9) levels.add(level);
        }
        levels.add(minLevel);
        levels.add(maxLevel);
        levels.add(startLevel);
        levels.add(endLevel);
        return levels.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public int levelIndex(double level) {
        int index = Arrays.binarySearch(levels(), level);
        if (index < 0) throw new IllegalArgumentException("水位不在网格中：" + level);
        return index;
    }
}
