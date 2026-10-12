package hydro;

/** 本月外部输入；upper=龙羊峡，lower=拉西瓦，intervalInflow=两库之间的区间来水。 */
public record MonthInput(
        int month, int days, double upperInflow, double intervalInflow,
        double upperEcoFlow, double lowerEcoFlow,
        double upperLevelLimit, double lowerLevelLimit) {

    public MonthInput {
        double[] values = {upperInflow, intervalInflow, upperEcoFlow, lowerEcoFlow,
                upperLevelLimit, lowerLevelLimit};
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("月输入必须为有限数");
        }
        if (month < 1 || month > 12 || days <= 0 || upperInflow < 0 || intervalInflow < 0
                || upperEcoFlow < 0 || lowerEcoFlow < 0) {
            throw new IllegalArgumentException("月份、天数或流量不合法");
        }
    }

    public double seconds() { return days * 86_400.0; }
    public double hours() { return days * 24.0; }
}
