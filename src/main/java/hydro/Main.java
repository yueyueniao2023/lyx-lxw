package hydro;

import java.nio.file.Path;
import java.util.Locale;

/** 从这里开始阅读：读数据 → 两库联合优化 → 保存12个月的结果。 */
public class Main {
    public static void main(String[] args) throws Exception {
        Path dataFolder = Path.of("data");
        Path outputFile = Path.of("results/schedule.csv");

        DataReader.Data data = DataReader.read(dataFolder);
        ScheduleResult result = new DynamicProgramming(
                data.upper(), data.lower(), data.months()).solve();
        ResultWriter.write(outputFile, result);

        System.out.println("月份  龙羊峡月末水位(m)  拉西瓦月末水位(m)  两库电量(亿kWh)");
        for (ScheduleResult.Month month : result.months()) {
            System.out.printf(Locale.ROOT, "%2d       %8.3f           %8.3f          %.6f%n",
                    month.input().month(), month.upper().endLevel(), month.lower().endLevel(),
                    month.energy() / 100_000);
        }
        System.out.printf(Locale.ROOT, "龙羊峡年发电量：%.6f 亿kWh%n", result.upperEnergy() / 100_000);
        System.out.printf(Locale.ROOT, "拉西瓦年发电量：%.6f 亿kWh%n", result.lowerEnergy() / 100_000);
        System.out.printf(Locale.ROOT, "两库年发电量合计：%.6f 亿kWh%n", result.totalEnergy() / 100_000);
        System.out.println("详细结果：" + outputFile.toAbsolutePath());
    }
}
