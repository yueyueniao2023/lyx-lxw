# 输入数据

CSV是从提供的龙羊峡—拉西瓦 Excel 数据逐值整理而来，Java只需读取文本。
来源存档：[原项目数据目录](https://github.com/yueyueniao2023/dp-hydro-dispatch/tree/codex/longyangxia-laxiwa-monthly/data/longyangxia-laxiwa/raw)。
本项目已包含运行所需的全部数据，运行时无需访问这个链接。

| 文件 | 内容 | 来源 |
| --- | --- | --- |
| `reservoirs.csv` | 两库固定参数，第一行龙羊峡、第二行拉西瓦 | `resourceData4/5.xlsx`；龙羊峡末水位按本题要求设为2595.2 m |
| `daily-inflow.csv` | 365天的龙羊峡入库及两库区间来水 | `input4.xlsx`、`input5.xlsx` |
| `monthly-limits.csv` | 12个月生态流量和水位上限 | `ecoFlow4/5.xlsx`；水位上限采用正常蓄水位 |
| `longyangxia-storage.csv` | 龙羊峡水位—库容 | `z4.xlsx`、`v4.xlsx` |
| `laxiwa-storage.csv` | 拉西瓦水位—库容 | `z5.xlsx`、`v5.xlsx` |
| `longyangxia-tailwater.csv` | 龙羊峡总泄流—尾水位 | `q4.xlsx`、`zTail4.xlsx` |
| `laxiwa-tailwater.csv` | 拉西瓦总泄流—尾水位 | `q5.xlsx`、`zTail5.xlsx` |

## 固定参数字段

| 字段 | 含义 | 单位 |
| --- | --- | --- |
| `name` | 水库名称 | — |
| `minLevel` / `maxLevel` | 最低/最高运行水位 | m |
| `startLevel` / `endLevel` | 年初/年末水位 | m |
| `levelStep` | 水位离散步长 | m |
| `maxRelease` | 最大总出库流量 | m³/s |
| `maxPowerFlow` | 最大发电流量 | m³/s |
| `maxPower` | 最大出力 | MW |
| `powerCoefficient` | 出力系数，本例8.5 | 按出力公式使用 |
| `headLoss` | 损失水头，本例0 | m |

## 来水与曲线

- `day` 是平年日序号1—365，`upperInflow` 是龙羊峡外部入库，`intervalInflow` 是两库间区间来水，流量均为m³/s。
  按题目确认的含义，`input5`直接作为区间来水。**拉西瓦入库 = 龙羊峡优化总出库 + 区间来水**。
- `upperEcoFlow` / `lowerEcoFlow` 是两库月平均生态下泄下限，单位m³/s。
  `upperLevelLimit` / `lowerLevelLimit` 是本月持续水位上限，月初月末都要满足，单位m。
- `level`、`tailwater` 是水位m，`storage` 是库容亿m³，`release` 是总出库m³/s。
- 龙羊峡库容曲线使用原表第601—702行，拉西瓦使用第391—512行，覆盖全部运行水位及插值邻点；
  不纳入运行范围以外的曲线段。尾水曲线完整保留，超出给定范围的流量不外推。

原资料没有标明年份和单位；本项目使用平年365天，以及上述水位、流量、库容、出力单位解释。
CSV使用UTF-8，第一行为固定英文表头，逗号分隔；字段内不放逗号或引号，编辑时保留列顺序。
