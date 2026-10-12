package hydro;

/** 给定一座水库的月初、月末水位，计算该月流量和发电量。 */
public class WaterPower {
    public static ScheduleResult.Station calculate(
            Reservoir reservoir, double startLevel, double endLevel,
            double inflow, MonthInput month, double ecoFlow, double levelLimit) {

        // 1. 检查运行水位和本月上限。不可行的转移返回null，让DP跳过。
        if (!Double.isFinite(startLevel) || !Double.isFinite(endLevel) || !Double.isFinite(inflow)
                || !Double.isFinite(ecoFlow) || !Double.isFinite(levelLimit) || inflow < 0 || ecoFlow < 0
                || startLevel < reservoir.minLevel() || endLevel < reservoir.minLevel()
                || startLevel > reservoir.maxLevel() || endLevel > reservoir.maxLevel()
                || startLevel > levelLimit || endLevel > levelLimit) return null;

        // 2. 水量平衡：Q出 = Q入 + (V初 - V末)/秒数。库容亿m³换成m³要乘1e8。
        double startStorage = reservoir.storageCurve().valueAt(startLevel);
        double endStorage = reservoir.storageCurve().valueAt(endLevel);
        double release = inflow + (startStorage - endStorage) * 1e8 / month.seconds();
        if (release < 0 || release > reservoir.maxRelease() || release < ecoFlow) return null;

        // 3. 总泄流决定尾水位；净水头 = 平均上游水位 - 尾水位 - 损失水头。
        Curve tailwaterCurve = reservoir.tailwaterCurve();
        if (release < tailwaterCurve.minX() || release > tailwaterCurve.maxX()) return null;
        double tailwater = tailwaterCurve.valueAt(release);
        double grossHead = (startLevel + endLevel) / 2 - tailwater;
        if (grossHead <= 0 && release > 0) return null;
        double head = grossHead - reservoir.headLoss();

        // 4. 发电流量同时受总泄流、机组过流能力、装机容量限制，剩余部分为弃水。
        double powerFlow = 0;
        if (head > 0) {
            double flowAtMaxPower = reservoir.maxPower() * 1000 / (reservoir.powerCoefficient() * head);
            powerFlow = Math.min(release, Math.min(reservoir.maxPowerFlow(), flowAtMaxPower));
        }
        double spill = release - powerFlow;
        double power = head > 0 ? reservoir.powerCoefficient() * powerFlow * head / 1000 : 0;
        double energy = power * month.hours();
        double waterBalanceError = (endStorage - startStorage) * 1e8
                - (inflow - release) * month.seconds();
        if (!Double.isFinite(energy)) throw new IllegalArgumentException("计算溢出，请检查水库参数");

        return new ScheduleResult.Station(startLevel, endLevel, startStorage, endStorage,
                inflow, release, powerFlow, spill, tailwater, head, power, energy, waterBalanceError);
    }
}
