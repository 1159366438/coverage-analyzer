# Coverage Analyzer Tool

这是一个基于Java的GUI工具，用于分析代码覆盖率信息。

## 功能特性

- 图形化界面，易于使用
- 支持JaCoCo XML格式的覆盖率报告
- 可以查询特定类的覆盖率信息
- 显示多种覆盖率指标：
  - 指令覆盖率 (Instruction Coverage)
  - 分支覆盖率 (Branch Coverage)
  - 行覆盖率 (Line Coverage)
  - 方法覆盖率 (Method Coverage)
  - 类覆盖率 (Class Coverage)

## 使用方法

1. 运行程序：`mvn exec:java`
2. 在"Report Path"字段中输入JaCoCo覆盖率报告的路径
3. 点击"Browse..."按钮选择报告目录，或手动输入路径
4. 点击"Load Report"加载覆盖率数据
5. 在"Class Name"字段中输入要查询的类名
6. 点击"Search Coverage"查看该类的覆盖率信息

## 构建项目

使用Maven构建项目：

```bash
mvn clean package
```

这将创建一个可执行的JAR文件。

## 依赖项

- Java 11+
- JavaFX 17+
- dom4j (用于XML解析)
- Gson (用于JSON处理)
