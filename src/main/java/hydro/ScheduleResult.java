package hydro;

import java.util.List;

/** 全年结果包含12个月；每个月包含两座水库的水力计算结果。 */
public record ScheduleResult(List<Month> months) {
    public ScheduleResult { months = List.copyOf(months); }

    public double upperEnergy() {
        return months.stream().mapToDouble(month -> month.upper().energy()).sum();
    }

    public double lowerEnergy() {
        return months.stream().mapToDouble(month -> month.lower().energy()).sum();
    }

    public double totalEnergy() { return upperEnergy() + lowerEnergy(); }

    public record Month(MonthInput input, Station upper, Station lower) {
        public double energy() { return upper.energy() + lower.energy(); }
    }

    /** 水位m、库容亿m³、流量m³/s、出力MW、电量MWh、水量平衡残差m³。 */
    public record Station(
            double startLevel, double endLevel, double startStorage, double endStorage,
            double inflow, double release, double powerFlow, double spill,
            double tailwater, double head, double power, double energy, double waterBalanceError) { }
}
