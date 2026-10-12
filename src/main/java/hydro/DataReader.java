package hydro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 读取简单CSV；把365个日平均流量按平年月份求平均，得到12个月的输入。 */
public class DataReader {
    public record Data(Reservoir upper, Reservoir lower, List<MonthInput> months) { }

    public static Data read(Path folder) throws IOException {
        List<String[]> reservoirs = rows(folder.resolve("reservoirs.csv"),
                "name,minLevel,maxLevel,startLevel,endLevel,levelStep,maxRelease,maxPowerFlow,maxPower,powerCoefficient,headLoss");
        if (reservoirs.size() != 2 || !reservoirs.get(0)[0].equals("龙羊峡")
                || !reservoirs.get(1)[0].equals("拉西瓦")) {
            throw new IOException("reservoirs.csv必须依次列出龙羊峡和拉西瓦");
        }
        Reservoir upper = reservoir(reservoirs.get(0), folder, "longyangxia");
        Reservoir lower = reservoir(reservoirs.get(1), folder, "laxiwa");
        List<String[]> daily = rows(folder.resolve("daily-inflow.csv"), "day,upperInflow,intervalInflow");
        List<String[]> limits = rows(folder.resolve("monthly-limits.csv"),
                "month,upperEcoFlow,lowerEcoFlow,upperLevelLimit,lowerLevelLimit");
        if (daily.size() != 365 || limits.size() != 12) throw new IOException("需要365天来水和12个月约束");

        int[] monthDays = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        List<MonthInput> months = new ArrayList<>();
        int dayIndex = 0;
        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            double upperSum = 0;
            double intervalSum = 0;
            for (int day = 0; day < monthDays[monthIndex]; day++) {
                String[] row = daily.get(dayIndex);
                if (number(row[0]) != dayIndex + 1) throw new IOException("日序号必须连续1—365");
                double upperInflow = number(row[1]);
                double intervalInflow = number(row[2]);
                if (upperInflow < 0 || intervalInflow < 0) throw new IOException("日来水不能为负");
                upperSum += upperInflow;
                intervalSum += intervalInflow;
                dayIndex++;
            }
            String[] row = limits.get(monthIndex);
            if (number(row[0]) != monthIndex + 1) throw new IOException("月份必须连续1—12");
            months.add(new MonthInput(monthIndex + 1, monthDays[monthIndex],
                    upperSum / monthDays[monthIndex], intervalSum / monthDays[monthIndex],
                    number(row[1]), number(row[2]), number(row[3]), number(row[4])));
        }
        return new Data(upper, lower, List.copyOf(months));
    }

    private static Reservoir reservoir(String[] row, Path folder, String filePrefix) throws IOException {
        return new Reservoir(row[0], number(row[1]), number(row[2]), number(row[3]), number(row[4]),
                number(row[5]), number(row[6]), number(row[7]), number(row[8]), number(row[9]), number(row[10]),
                curve(folder.resolve(filePrefix + "-storage.csv"), "level,storage"),
                curve(folder.resolve(filePrefix + "-tailwater.csv"), "release,tailwater"));
    }

    private static Curve curve(Path file, String header) throws IOException {
        List<String[]> rows = rows(file, header);
        double[] x = new double[rows.size()];
        double[] y = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            x[i] = number(rows.get(i)[0]);
            y[i] = number(rows.get(i)[1]);
        }
        return new Curve(x, y);
    }

    /** 本项目CSV不使用引号或含逗号的字段，严格检查表头和每行列数。 */
    private static List<String[]> rows(Path file, String header) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).replace("\uFEFF", "").equals(header)) {
            throw new IOException(file + "的表头应为：" + header);
        }
        int columns = header.split(",").length;
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(",", -1);
            if (cells.length != columns) throw new IOException(file + "第" + (i + 1) + "行列数不正确");
            for (int j = 0; j < cells.length; j++) cells[j] = cells[j].trim();
            rows.add(cells);
        }
        return rows;
    }

    private static double number(String text) throws IOException {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IOException("不是有效数值：" + text, ex);
        }
    }
}
