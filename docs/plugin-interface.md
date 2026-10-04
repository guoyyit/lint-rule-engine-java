# 插件接口说明

## 概述

规则以插件形式注册。一个插件（`LintRulePlugin`）可以声明自己的 id、名称、版本，
并贡献若干条规则（`LintRule`）。引擎通过 `ServiceLoader` 自动发现插件，也支持
编程式注册（便于测试与内嵌集成）。

## SPI

### `LintRulePlugin`

```java
public interface LintRulePlugin {
    String getId();                       // 插件唯一 id，例如 "builtin"
    String getName();                     // 可读名称
    String getVersion();                  // 版本，纳入缓存键
    List<LintRule> getRules();            // 贡献的规则
}
```

### `LintRule`

```java
public interface LintRule {
    String getId();                       // 规则唯一 id，例如 "no-todo"
    String getName();
    String getDescription();
    default Severity getDefaultSeverity() { return Severity.WARNING; }
    default Set<String> getApplicableExtensions() { return Set.of(); } // 空集 = 所有文件
    default boolean isFixable() { return false; }
    List<Diagnostic> check(SourceFile file);   // 必须确定性，不得修改文件
    default Optional<Fix> fix(SourceFile file, Diagnostic diagnostic) { return Optional.empty(); }
}
```

规则必须满足：给定相同 `SourceFile` 永远产出相同诊断；不得修改文件内容；
对畸形输入不得抛出异常（引擎仍会兜底捕获）。

## 注册方式

### 1. ServiceLoader（推荐）

在 `src/main/resources/META-INF/services/` 下创建文件
`com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin`，
每行写一个插件实现类的全限定名：

```
com.example.MyPlugin
com.example.OtherPlugin
```

### 2. 编程式注册

```java
PluginRegistry registry = PluginRegistry.of(new MyPlugin(), new OtherPlugin());
// 或合并 ServiceLoader 加载结果
PluginRegistry loaded = PluginRegistry.load();
PluginRegistry combined = PluginRegistry.combine(loaded, List.of(new MyPlugin()));
```

## 加载失败隔离

`PluginRegistry` 对每个插件独立校验与加载：

- 插件构造抛出异常 → 记录为 `PluginLoadFailure`，插件跳过；
- 插件 id 为空、`getRules()` 返回 null、规则 id 为空、插件内规则 id 重复、
  与已加载插件的规则 id 重复 → 记录失败并跳过该插件；
- 失败信息包含插件 id 与原因，可通过 `registry.getFailures()` 获取，
  REST 接口 `GET /api/lint/plugins` 也会返回。

一个插件失败不影响其他插件，检查照常进行。若某条规则在检查时抛出异常，
该异常被捕获并生成一条 `lint-engine:rule-error` 诊断（ERROR 级），
同一文件的其他规则与其他文件的结果不受影响。

## 诊断与修复

- `Diagnostic`：`ruleId`、`severity`、`filePath`、`range`（1 -based 行列）、
  `message`、可选 `fix`。
- `Fix`：`description` + 若干 `TextEdit`（range + 替换文本，空串表示删除）。
- 可修复规则在 `check` 返回的诊断中携带 `Fix`，并在 `fix` 中返回它。

## 内置插件

| 插件 id | 规则 id | 级别 | 可修复 | 说明 |
|---|---|---|---|---|
| builtin-no-todo | no-todo | warning | 否 | 标记 TODO |
| builtin-trailing-whitespace | trailing-whitespace | info | 是 | 行尾空白 |
| builtin-long-line | long-line | info | 否 | 行超 120 字符 |
| builtin-no-tab-indent | no-tab-indent | warning | 是 | 行首 tab 缩进 |
| builtin-final-newline | final-newline | info | 是 | 文件末尾换行 |
