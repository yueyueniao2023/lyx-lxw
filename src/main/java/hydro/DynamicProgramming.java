package hydro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 两库联合动态规划。阶段是月，状态是“两库月末水位”的组合，目标是全年总电量最大。 */
public class DynamicProgramming {
    private final Reservoir upper;
    private final Reservoir lower;
    private final List<MonthInput> months;

    public DynamicProgramming(Reservoir upper, Reservoir lower, List<MonthInput> months) {
        if (months.size() != 12) throw new IllegalArgumentException("必须提供12个月的数据");
        for (int i = 0; i < 12; i++) {
            MonthInput month = months.get(i);
            if (month.month() != i + 1 || month.upperLevelLimit() < upper.minLevel()
                    || month.upperLevelLimit() > upper.maxLevel() || month.lowerLevelLimit() < lower.minLevel()
                    || month.lowerLevelLimit() > lower.maxLevel()) {
                throw new IllegalArgumentException("月份必须连续，本月水位上限必须在运行范围内");
            }
        }
        this.upper = upper;
        this.lower = lower;
        this.months = List.copyOf(months);
    }

    public ScheduleResult solve() {
        double[] upperLevels = upper.levels();
        double[] lowerLevels = lower.levels();
        int upperCount = upperLevels.length;
        int lowerCount = lowerLevels.length;
        int upperEndIndex = upper.levelIndex(upper.endLevel());
        int lowerEndIndex = lower.levelIndex(lower.endLevel());

        // 第0个月只有指定初水位可达；负无穷表示无法到达该水位组合。
        double[][] bestEnergy = emptyEnergy(upperCount, lowerCount);
        bestEnergy[upper.levelIndex(upper.startLevel())][lower.levelIndex(lower.startLevel())] = 0;

        // 保存最优前驱：本月某个终点，是由上个月哪个水位组合转移过来的。
        int[][][] previousUpper = new int[13][upperCount][lowerCount];
        int[][][] previousLower = new int[13][upperCount][lowerCount];
        for (int month = 0; month <= 12; month++) {
            for (int upperIndex = 0; upperIndex < upperCount; upperIndex++) {
                Arrays.fill(previousUpper[month][upperIndex], -1);
                Arrays.fill(previousLower[month][upperIndex], -1);
            }
        }

        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            MonthInput month = months.get(monthNumber - 1);
            double[][] nextEnergy = emptyEnergy(upperCount, lowerCount);
            boolean found = false;
            // 前11个月可选任意网格终点；12月只允许指定年末水位。
            int firstUpperEnd = monthNumber == 12 ? upperEndIndex : 0;
            int upperEndLimit = monthNumber == 12 ? upperEndIndex + 1 : upperCount;
            int firstLowerEnd = monthNumber == 12 ? lowerEndIndex : 0;
            int lowerEndLimit = monthNumber == 12 ? lowerEndIndex + 1 : lowerCount;

            for (int upperStart = 0; upperStart < upperCount; upperStart++) {
                for (int upperEnd = firstUpperEnd; upperEnd < upperEndLimit; upperEnd++) {
                    ScheduleResult.Station upperResult = WaterPower.calculate(upper,
                            upperLevels[upperStart], upperLevels[upperEnd], month.upperInflow(),
                            month, month.upperEcoFlow(), month.upperLevelLimit());
                    if (upperResult == null) continue;

                    // 关键耦合：下库入流 = 上库总出库（发电+弃水）+ 区间来水。
                    double lowerInflow = upperResult.release() + month.intervalInflow();
                    for (int lowerStart = 0; lowerStart < lowerCount; lowerStart++) {
                        if (bestEnergy[upperStart][lowerStart] == Double.NEGATIVE_INFINITY) continue;
                        for (int lowerEnd = firstLowerEnd; lowerEnd < lowerEndLimit; lowerEnd++) {
                            ScheduleResult.Station lowerResult = WaterPower.calculate(lower,
                                    lowerLevels[lowerStart], lowerLevels[lowerEnd], lowerInflow,
                                    month, month.lowerEcoFlow(), month.lowerLevelLimit());
                            if (lowerResult == null) continue;

                            // 同一个本月终点，只保留累计电量最大的路径。
                            double candidate = bestEnergy[upperStart][lowerStart]
                                    + upperResult.energy() + lowerResult.energy();
                            if (candidate > nextEnergy[upperEnd][lowerEnd]) {
                                nextEnergy[upperEnd][lowerEnd] = candidate;
                                previousUpper[monthNumber][upperEnd][lowerEnd] = upperStart;
                                previousLower[monthNumber][upperEnd][lowerEnd] = lowerStart;
                                found = true;
                            }
                        }
                    }
                }
            }
            if (!found) throw new IllegalStateException(monthNumber + "月没有可行调度，请检查输入参数");
            bestEnergy = nextEnergy;
        }

        // 从指定年末水位向前回溯，恢复每个月最优的水位、流量及电量。
        List<ScheduleResult.Month> result = new ArrayList<>();
        int upperEnd = upperEndIndex;
        int lowerEnd = lowerEndIndex;
        for (int monthNumber = 12; monthNumber >= 1; monthNumber--) {
            int upperStart = previousUpper[monthNumber][upperEnd][lowerEnd];
            int lowerStart = previousLower[monthNumber][upperEnd][lowerEnd];
            MonthInput month = months.get(monthNumber - 1);
            ScheduleResult.Station upperResult = WaterPower.calculate(upper,
                    upperLevels[upperStart], upperLevels[upperEnd], month.upperInflow(),
                    month, month.upperEcoFlow(), month.upperLevelLimit());
            ScheduleResult.Station lowerResult = WaterPower.calculate(lower,
                    lowerLevels[lowerStart], lowerLevels[lowerEnd], upperResult.release() + month.intervalInflow(),
                    month, month.lowerEcoFlow(), month.lowerLevelLimit());
            result.add(new ScheduleResult.Month(month, upperResult, lowerResult));
            upperEnd = upperStart;
            lowerEnd = lowerStart;
        }
        Collections.reverse(result);
        return new ScheduleResult(result);
    }

    private static double[][] emptyEnergy(int upperCount, int lowerCount) {
        double[][] energy = new double[upperCount][lowerCount];
        for (double[] row : energy) Arrays.fill(row, Double.NEGATIVE_INFINITY);
        return energy;
    }
}
