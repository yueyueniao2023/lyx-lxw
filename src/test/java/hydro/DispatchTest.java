package hydro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DispatchTest {
    @Test
    void realDataMatchesKnownEnergyAndConservesWater() throws Exception {
        DataReader.Data data = DataReader.read(Path.of("data"));
        ScheduleResult result = new DynamicProgramming(data.upper(), data.lower(), data.months()).solve();
        assertEquals(31_787_339.211535, result.totalEnergy(), 0.0001);
        assertEquals(12, result.months().size());
        double upperStart = 2598.3;
        double lowerStart = 2452;
        for (ScheduleResult.Month month : result.months()) {
            ScheduleResult.Station upper = month.upper();
            ScheduleResult.Station lower = month.lower();
            assertEquals(upperStart, upper.startLevel());
            assertEquals(lowerStart, lower.startLevel());
            assertEquals(upper.release() + month.input().intervalInflow(), lower.inflow(), 1e-10);
            checkStation(data.upper(), upper, month.input().upperEcoFlow(), month.input().upperLevelLimit());
            checkStation(data.lower(), lower, month.input().lowerEcoFlow(), month.input().lowerLevelLimit());
            upperStart = upper.endLevel();
            lowerStart = lower.endLevel();
        }
        assertEquals(2595.2, upperStart);
        assertEquals(2452, lowerStart);
    }

    private void checkStation(Reservoir reservoir, ScheduleResult.Station station, double eco, double limit) {
        assertTrue(station.startLevel() >= reservoir.minLevel() && station.startLevel() <= limit);
        assertTrue(station.endLevel() >= reservoir.minLevel() && station.endLevel() <= limit);
        assertTrue(station.release() >= eco && station.release() <= reservoir.maxRelease());
        assertTrue(station.powerFlow() >= 0 && station.powerFlow() <= reservoir.maxPowerFlow());
        assertTrue(station.power() >= 0 && station.power() <= reservoir.maxPower() + 1e-9);
        assertTrue(station.spill() >= 0);
        assertEquals(station.release(), station.powerFlow() + station.spill(), 1e-10);
        assertEquals(0, station.waterBalanceError(), 0.0001);
    }

    @Test
    void dailyToMonthlyPreservesAnnualInflowVolume() throws Exception {
        List<MonthInput> months = DataReader.read(Path.of("data")).months();
        List<String> daily = Files.readAllLines(Path.of("data/daily-inflow.csv"));
        double upperDailyVolume = 0;
        double intervalDailyVolume = 0;
        for (String line : daily.subList(1, daily.size())) {
            String[] cells = line.split(",");
            upperDailyVolume += Double.parseDouble(cells[1]) * 86_400;
            intervalDailyVolume += Double.parseDouble(cells[2]) * 86_400;
        }
        assertEquals(365, months.stream().mapToInt(MonthInput::days).sum());
        assertEquals(upperDailyVolume, months.stream().mapToDouble(m -> m.upperInflow() * m.seconds()).sum(), 0.001);
        assertEquals(intervalDailyVolume, months.stream().mapToDouble(m -> m.intervalInflow() * m.seconds()).sum(), 0.001);
    }

    @Test
    void missingOrMisnumberedDayIsRejected(@TempDir Path folder) throws Exception {
        try (var files = Files.list(Path.of("data"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".csv")).toList()) {
                Files.copy(file, folder.resolve(file.getFileName()));
            }
        }
        Path dailyFile = folder.resolve("daily-inflow.csv");
        List<String> lines = Files.readAllLines(dailyFile);
        lines.set(1, lines.get(1).replaceFirst("^1,", "2,"));
        Files.write(dailyFile, lines, StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> DataReader.read(folder));
        lines.remove(lines.size() - 1);
        Files.write(dailyFile, lines, StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> DataReader.read(folder));
    }

    @Test
    void interpolationUsesNeighboursAndRejectsExtrapolation() {
        Curve curve = new Curve(new double[]{0, 10}, new double[]{2, 4});
        assertEquals(3, curve.valueAt(5));
        assertThrows(IllegalArgumentException.class, () -> curve.valueAt(-1));
        assertThrows(IllegalArgumentException.class, () -> curve.valueAt(11));
    }

    @Test
    void gridKeepsExactInitialAndTerminalLevels() throws Exception {
        Reservoir upper = DataReader.read(Path.of("data")).upper();
        assertEquals(2598.3, upper.levels()[upper.levelIndex(2598.3)]);
        assertEquals(2595.2, upper.levels()[upper.levelIndex(2595.2)]);
    }

    @Test
    void waterBalanceAndEnergyAgreeWithHandCalculation() {
        Reservoir reservoir = toyReservoir("上库", 100, 101, 1000);
        MonthInput month = new MonthInput(1, 31, 10, 2, 0, 0, 101, 81);
        ScheduleResult.Station result = WaterPower.calculate(reservoir, 101, 100, 10, month, 0, 101);
        assertNotNull(result);
        double expectedRelease = 10 + 0.01 * 1e8 / (31 * 86_400.0);
        double expectedHead = 100.5 - 10;
        double expectedPower = 8.5 * expectedRelease * expectedHead / 1000;
        assertEquals(expectedRelease, result.release(), 1e-12);
        assertEquals(expectedHead, result.head(), 1e-12);
        assertEquals(expectedPower * 31 * 24, result.energy(), 1e-8);
    }

    @Test
    void installedPowerLimitProducesSpillWithoutLosingWater() {
        Reservoir reservoir = toyReservoir("上库", 100, 101, 1);
        MonthInput month = new MonthInput(1, 31, 10, 2, 0, 0, 101, 81);
        ScheduleResult.Station result = WaterPower.calculate(reservoir, 100, 100, 10, month, 0, 101);
        assertNotNull(result);
        assertEquals(1, result.power(), 1e-12);
        assertTrue(result.spill() > 0);
        assertEquals(10, result.powerFlow() + result.spill(), 1e-12);
    }

    @Test
    void impossibleTransfersAreSkipped() {
        Reservoir reservoir = toyReservoir("上库", 100, 101, 1000);
        MonthInput month = new MonthInput(1, 31, 10, 2, 0, 0, 101, 81);
        assertNull(WaterPower.calculate(reservoir, 100, 100, 10, month, 11, 101)); // 生态不足
        assertNull(WaterPower.calculate(reservoir, 101, 100, 10, month, 0, 100)); // 月初超限
        assertNull(WaterPower.calculate(reservoir, 100, 101, 0, month, 0, 101)); // 无水可蓄
        assertNull(WaterPower.calculate(reservoir, 100, 100, 1001, month, 0, 101)); // 泄流超限
    }

    @Test
    void dynamicProgrammingAgreesWithCompletePathEnumeration() {
        Reservoir upper = toyReservoir("上库", 100, 101, 1000);
        Reservoir lower = toyReservoir("下库", 80, 81, 1000);
        List<MonthInput> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            // 前3个月可调整水位，其后限制在最低水位，使完整路径穷举保持小规模。
            months.add(new MonthInput(month, 30, month == 2 ? 20 : 10, 2, 0, 0,
                    month <= 3 ? 101 : 100, month <= 3 ? 81 : 80));
        }
        double expected = enumerate(upper, lower, months, 0, upper.startLevel(), lower.startLevel());
        double actual = new DynamicProgramming(upper, lower, months).solve().totalEnergy();
        assertTrue(Double.isFinite(expected));
        assertEquals(expected, actual, 1e-8);
    }

    /** 独立枚举完整路径，不使用DP的状态合并和前驱表。 */
    private double enumerate(Reservoir upper, Reservoir lower, List<MonthInput> months,
                             int index, double upperStart, double lowerStart) {
        if (index == 12) {
            return upperStart == upper.endLevel() && lowerStart == lower.endLevel()
                    ? 0 : Double.NEGATIVE_INFINITY;
        }
        MonthInput month = months.get(index);
        double best = Double.NEGATIVE_INFINITY;
        for (double upperEnd : upper.levels()) {
            ScheduleResult.Station upperResult = WaterPower.calculate(upper, upperStart, upperEnd,
                    month.upperInflow(), month, month.upperEcoFlow(), month.upperLevelLimit());
            if (upperResult == null) continue;
            for (double lowerEnd : lower.levels()) {
                ScheduleResult.Station lowerResult = WaterPower.calculate(lower, lowerStart, lowerEnd,
                        upperResult.release() + month.intervalInflow(), month, month.lowerEcoFlow(), month.lowerLevelLimit());
                if (lowerResult != null) {
                    double remaining = enumerate(upper, lower, months, index + 1, upperEnd, lowerEnd);
                    best = Math.max(best, upperResult.energy() + lowerResult.energy() + remaining);
                }
            }
        }
        return best;
    }

    private Reservoir toyReservoir(String name, double min, double max, double maxPower) {
        return new Reservoir(name, min, max, min, min, 1, 1000, 1000, maxPower, 8.5, 0,
                new Curve(new double[]{min, max}, new double[]{1, 1.01}),
                new Curve(new double[]{0, 1000}, new double[]{10, 10}));
    }
}
