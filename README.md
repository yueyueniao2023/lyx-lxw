# 龙羊峡—拉西瓦双库调度

用 Java 动态规划求两座串联水库的 **12个月总发电量最大**。本项目只包含这一道题。

龙羊峡初水位 **2598.3 m**、末水位 **2595.2 m**；拉西瓦初末水位均为 **2452 m**。
默认水位步长 **0.5 m**，精确初末水位也会加入网格。

## 运行

需要 JDK 17 或以上版本。用 IntelliJ IDEA 打开本目录，导入 Maven 项目，直接运行 `hydro.Main`。
运行配置的工作目录选本项目根目录（含 `pom.xml` 的目录），无需填写程序参数。

也可以在项目根目录运行：

```shell
mvn compile exec:java
mvn test
```

主程序只使用 Java 标准库。若没有 Maven，可用 JDK 直接运行：

```shell
javac -encoding UTF-8 -d out src/main/java/hydro/*.java
java -cp out hydro.Main
```

控制台显示12个月水位和电量，完整结果保存到 [results/schedule.csv](results/schedule.csv)，用 Excel 打开即可。
默认数据的两库年发电量合计约 **317.873392 亿kWh**，其中龙羊峡 **79.722222**、拉西瓦 **238.151170 亿kWh**。

## 从哪里读起

所有主程序都在 `src/main/java/hydro`，共9个文件，只有一个包。

| 阅读顺序 | 文件 | 负责什么 |
| --- | --- | --- |
| 1 | [Main.java](src/main/java/hydro/Main.java) | 把读数据、优化、输出连起来 |
| 2 | [Reservoir.java](src/main/java/hydro/Reservoir.java) | 水库参数和水位网格 |
| 3 | [MonthInput.java](src/main/java/hydro/MonthInput.java) | 一个月的来水、生态流量和水位上限 |
| 4 | [WaterPower.java](src/main/java/hydro/WaterPower.java) | 一座水库一个月的水量平衡及发电计算 |
| 5 | [DynamicProgramming.java](src/main/java/hydro/DynamicProgramming.java) | 两库联合递推和最优路径回溯，核心算法 |
| 6 | [DataReader.java](src/main/java/hydro/DataReader.java) | 读CSV，把日流量按月求平均 |
| 7 | [Curve.java](src/main/java/hydro/Curve.java) | 曲线线性插值 |
| 8 | [ScheduleResult.java](src/main/java/hydro/ScheduleResult.java) | 保存全年、单月、单库计算结果 |
| 9 | [ResultWriter.java](src/main/java/hydro/ResultWriter.java) | 写一张中文调度表 |

`upper` 表示上库龙羊峡，`lower` 表示下库拉西瓦。
`release` 是总出库流量，`powerFlow` 是发电流量，`spill` 是弃水流量，三者满足 `release = powerFlow + spill`。

## 算法

1. **阶段**是月份，共12个月。**状态**是两库月末水位组合。**决策**是从已知月初水位出发，选择本月末两库水位。
2. 用水位—库容曲线把水位换成库容，反推上库出库；再用上库总出库与区间来水得到下库入库。
3. 检查运行水位、月水位上限、生态下泄、最大总泄流、尾水曲线覆盖；不可行的转移跳过。
4. 根据水头、机组过流能力和装机容量计算两库本月发电量。同一个月末水位组合只保留累计电量最大的路径。
5. 初始只允许指定初水位；12月只允许指定末水位。记录每次最优转移的前驱，从年末回溯出全年方案。

```text
上库总出库 = 上库入库 + (上库月初库容 - 上库月末库容) × 1e8 / 本月秒数
下库入库   = 上库总出库 + 区间来水
下库总出库 = 下库入库 + (下库月初库容 - 下库月末库容) × 1e8 / 本月秒数

净水头 = (月初水位 + 月末水位)/2 - 尾水位 - 损失水头
发电流量 = min(总出库, 最大发电流量, 最大出力×1000/(出力系数×净水头))
出力(MW) = 出力系数 × 发电流量 × 净水头 / 1000
电量(MWh) = 出力 × 当月天数 × 24

F[月,上库末水位,下库末水位]
    = max(前一个月的累计最优电量 + 本月上库电量 + 本月下库电量)
```

其中 `1e8` 把亿m³换为m³。净水头不大于0时发电量取0；代码也会排除有泄流却毛水头非正的转移。
出力系数使用8.5，已包含综合效率，不再乘一次效率。`1 MWh = 1000 kWh`，故除以100000得到亿kWh。

## 修改输入

所有输入在 [data](data/README.md) 中，程序不依赖原来的大项目。
修改 `reservoirs.csv` 的 `endLevel` 可改末水位，修改 `levelStep` 可改离散步长。
曲线、来水和生态流量均来自提供的龙羊峡—拉西瓦数据，具体字段和来源见数据说明。

结果是在所选水位网格、月平均模型下的最优解。365天按平年自然月划分；忽略传播损失、时滞、蒸发渗漏和机组启停，
水头用月初月末平均水位估计。生态流量约束作用于月平均总出库，不代表逐日达标。
没有单独的汛限、变幅和损失水头资料时，各月上限采用正常蓄水位，损失水头取0。
保证出力没有保证率定义，本例不将其作为每个月的硬约束。

## 核验

`mvn test` 核验日转月水量、精确初末水位、上下库衔接、各项约束和默认年电量；
另用小网格完整枚举所有可行路径，与动态规划结果对比。
