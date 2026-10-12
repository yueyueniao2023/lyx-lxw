package hydro;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.StringJoiner;

/** 只输出一张12行的调度表，中文表头可直接用Excel打开。 */
public class ResultWriter {
    public static void write(Path file, ScheduleResult result) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            StringJoiner header = new StringJoiner(",");
            header.add("月份").add("天数").add("区间来水m3s");
            for (String name : new String[]{"龙羊峡", "拉西瓦"}) {
                for (String item : new String[]{"月初水位m", "月末水位m", "月初库容亿m3", "月末库容亿m3",
                        "入库流量m3s", "总出库流量m3s", "发电流量m3s", "弃水流量m3s", "尾水位m",
                        "净水头m", "出力MW", "电量亿kWh", "水量平衡残差m3"}) header.add(name + item);
            }
            header.add("两库电量亿kWh");
            writer.write(header.toString());
            writer.newLine();
            for (ScheduleResult.Month month : result.months()) {
                writer.write(numbers(month.input().month(), month.input().days(), month.input().intervalInflow())
                        + "," + station(month.upper()) + "," + station(month.lower())
                        + "," + numbers(month.energy() / 100_000));
                writer.newLine();
            }
        }
    }

    private static String station(ScheduleResult.Station station) {
        return numbers(station.startLevel(), station.endLevel(), station.startStorage(), station.endStorage(),
                station.inflow(), station.release(), station.powerFlow(), station.spill(), station.tailwater(),
                station.head(), station.power(), station.energy() / 100_000, station.waterBalanceError());
    }

    private static String numbers(double... values) {
        StringJoiner row = new StringJoiner(",");
        for (double value : values) row.add(String.format(Locale.ROOT, "%.9f", value));
        return row.toString();
    }
}
