# 静态检查规则引擎（lint-rule-engine）

一个可扩展的静态检查规则引擎与规则插件机制，具备以下能力：

1. **插件化规则注册**：插件声明名称、适用文件范围与可修复能力；单个插件加载失败被隔离，不影响其他插件产出，失败原因可定位到具体插件来源。
2. **目录层级配置**：子目录 `.lintrc` 继承并覆盖父目录；同一份规则集与输入在任意检查顺序、任意并发度下产出逐条一致、排序稳定的诊断集合。
3. **严重级别与行内抑制**：抑制范围确定，被抑制诊断不会出现在最终结果中；错误标记或引用不存在的规则会产生带文件、行号的可定位提示。
4. **自动修复**：冲突修复按既定确定性策略择优，写入前检测用户并发修改，写入采用“临时文件 + 原子替换”，修复后自动复查确认问题消除。
5. **增量检查**：仅重检受影响文件；缓存键覆盖文件内容、配置指纹、规则集指纹与引擎版本；缓存为有界 LRU，不会无界增长。
6. **并发与取消**：每个文件独立线程执行、独立超时与取消；单文件超时/取消不污染其他文件，不留半写状态；取消后重新检查与全量检查结果一致。

---

## 1. 快速开始

```bash
mvn test          # 运行全部 30+ 个自动化测试
mvn spring-boot:run
curl 'http://localhost:8080/lint?path=/path/to/project'
curl -X POST 'http://localhost:8080/lint/fix?path=/path/to/project'
```

Java 直接使用：

```java
PluginRegistry registry = new PluginLoader().load(List.of(
        new PluginSource.ServiceLoader(MyPlugin.class.getClassLoader(), rootDir)));
LintEngine engine = new LintEngine(rootDir, registry,
        LintOptions.defaults().withParallelism(8).withTimeoutMillis(5000));
LintReport report = engine.check();                        // 全量
engine.checkIncremental(CancellationToken.none());         // 增量
engine.checkAndFix(new CancellationToken());               // 自动修复 + 复查
```

---

## 2. 插件接口

插件只依赖 `rule` 与 `plugin` 包的公共接口：

```java
public interface LintPlugin {
    String name();          // 唯一插件名（大小写不敏感，禁止空白）
    List<Rule> rules();     // 贡献的规则；需要 public 无参构造
}

public interface Rule {
    RuleId id();                    // 必须是 <本插件名>:<规则名>
    Severity defaultSeverity();     // INFO / WARNING / ERROR
    boolean canFix();               // 是否提供自动修复
    String appliesTo();             // glob，如 "**/*.java"
    void check(RuleContext ctx);    // 线程安全；普通输入问题不得抛出
}
```

规则通过 `RuleContext` 报告诊断：

```java
ctx.report(ruleId, line, column, Severity.WARNING, "message");
ctx.report(ruleId, line, column, Severity.WARNING, "message",
           new Fix(startOffset, endOffset, replacement)); // 可选修复
```

### 注册方式

* **Java SPI（推荐）**：在 jar 的
  `META-INF/services/com.github.guoyyit.lintruleenginejava.plugin.LintPlugin`
  中每行写一个实现类全限定名（内置插件即以此方式注册）。
* **显式类名**：`new PluginSource.ClassNames(classLoader, List.of("com.acme.MyPlugin"))`，
  便于测试或受控环境。

### 加载失败隔离

加载器对每个候选插件单独捕获以下故障，汇总到 `registry.failures()`：

* 类不存在 / 无法链接（`ClassNotFoundException`、`NoClassDefFoundError` 等）；
* 构造函数抛异常（解开反射包装，报告根因类名与消息）；
* 插件名为空白、`rules()` 返回 null 或抛异常；
* 规则 id 前缀不属于该插件、id 重复、规则项为 null；
* SPI 配置错误（`ServiceConfigurationError`）。

每个 `PluginLoadFailure` 都带：插件名（尽力推断）、**来源位置**（`class:全限定名#序号`
或 `serviceloader:目录`）、原因说明与根因异常类名。健康插件的规则照常加载与执行。

### 内置插件

| 规则 id | 默认级别 | 可修复 | 说明 |
|---|---|---|---|
| `builtin:trailing-whitespace` | WARNING | 是（删除尾随空白） | 忽略行尾注释自身前的空白 |
| `builtin:no-todo` | INFO | 否 | 行内出现词边界 `TODO` |

---

## 3. 配置语义（`.lintrc`）

引擎从分析根目录开始，沿文件所在目录路径逐级读取 `.lintrc`，
**父级先应用，子级继承后覆盖同名条目**。隐藏目录不参与扫描；`.lintrc` 自身不检查。

```properties
# 覆盖规则严重级别（子目录覆盖父目录，同 key 以最深层为准）
severity builtin:no-todo=error
# 在该目录子树内关闭规则（继承累积，无“重新启用”语法；severity 不改变 disabled）
disable builtin:trailing-whitespace
# 仅检查匹配的文件（多层 include 取并集；为空表示检查所有文件）
include **/*.txt
```

* 严重级别取值：`INFO` / `WARNING` / `ERROR`（大小写不敏感）。
* 规则引用格式固定为 `<plugin>:<rule>`，规则名大小写不敏感。
* 行内 `#` 之后为注释；空行忽略。
* 任何非法指令（未知指令、错误的规则引用、未知级别）都不会中断分析，
  而是作为 `ConfigProblem`（含**文件绝对路径与行号**）出现在 `LintReport.configProblems()`。
* 配置层按文件 mtime 缓存并自动失效；配置指纹（各层内容哈希的哈希）
  纳入增量缓存键，配置变化必然使相关文件缓存失效。

### 可复现排序

最终诊断按 `(文件路径, 行, 列, 规则id, 消息)` 排序；
规则在注册内部按 id 排序；插件失败、配置问题、修复结果同样稳定排序。
因此并行度、线程调度顺序均不影响结果（测试 `ConfigHierarchyTest` 与
`EngineEndToEndTest` 对并行度 1..16 / 多线程并发做了等价性断言）。

---

## 4. 行内抑制

标记写在 `//` 或 `#` 注释内：

| 标记 | 生效范围 |
|---|---|
| `lint:suppress plugin:rule [更多规则...]` | 标记与代码同一行 → **仅该行**；标记独占一行 → **该行与下一行** |
| `lint:suppress-all` | 同上，但覆盖全部规则 |
| `lint:disable [规则...]` | 从标记行起的**区域** |
| `lint:enable [规则...]` | 区域结束于 enable 行之前一行；enable 行起诊断恢复 |
| `lint:disable-all` | 无规则参数的整区域关闭 |

* 无匹配 `enable` 时区域延伸到文件末尾。
* 嵌套未关闭的 disable、一行多个指令、`suppress-all` 带参数、
  引用未知规则或格式错误的 `plugin:rule` 都会产生
  `lint:bad-suppression` 诊断（带行、列、原始标记文本）。
* 被抑制的诊断**不会**出现在最终结果中，但计数体现在 `LintReport.suppressedCount()`。

---

## 5. 自动修复与冲突策略

1. 收集所有带 `Fix` 的诊断，按 `(startOffset, endOffset, ruleId)` 去重；
2. 将字符范围**传递重叠**的修复聚成簇；
3. 每簇执行确定性策略 `FixPolicy.HIGHEST_RULE_ID`：
   `plugin:rule` 字典序最大者获胜；同一规则多个重叠修复时取
   `(start, end, replacement)` 最小者；
4. 落败修复记录在 `FixResult.conflicts()`（含获胜者、被拒规则 id 与原因）；
5. 获胜修复**从右向左**拼接，避免偏移失效；
6. 写盘前重新读取目标文件并比对哈希：若用户在分析后改过文件，
   本次修复跳过并产生 `CheckFailure`（`concurrent-modification`）；
7. 写入采用“同目录临时文件 → `ATOMIC_MOVE` 替换”（不支持原子移动的文件系统
   使用先落盘临时文件再替换的方式），目标文件永远不会被半写；
8. 修复完成后对被修文件**立即重新检查**，报告中的诊断以复查结果为准，
   确认问题消除（或暴露剩余问题）。取消发生在修复阶段时不写入任何文件。

---

## 6. 增量缓存

单文件缓存键：

```
CacheKey = sha256(fileBytes)
          | 配置指纹（适用的所有 .lintrc 层内容哈希）
          | 规则集指纹（规则 id/默认级别/appliesTo/canFix）
          | 引擎版本常量（抑制与引擎语义变更时升级）
```

* `checkIncremental`：仅当文件内容哈希与上次运行记录不同、或缓存键未命中时才重跑规则；
  未改变文件直接复用缓存诊断，保证“未改变文件的诊断结果保持不变”。
* 规则集或配置变化会改变键（指纹不同），相关缓存自然失效。
* `DefaultLintCache` 为访问序 LRU（默认上限 10 000，可构造时指定），
  长期运行不会无界增长；另可 `invalidateAll()`。
* 取消的运行**不**更新增量状态，因此取消后重新检查等价于对当前磁盘状态的全量检查。

---

## 7. 并发、超时与取消

* 每次运行使用独立的固定线程池（守护线程，结束即 `shutdownNow`），
  运行之间互不污染；并行度与超时在 `LintOptions` 中配置。
* 每个文件最多等待 `perFileTimeoutMillis`，超时后中断该文件任务并记录
  `CheckFailure`（原因 `per-file check timed out ...`，根因 `TimeoutException`），
  其他文件照常完成。
* `CancellationToken` 为协作式取消：引擎每 ≤100ms 轮询一次，
  取消后立即中断所有未完成任务并标记剩余文件为 `check cancelled`，
  报告置 `cancelled=true`，不写任何修复文件，不更新缓存状态。
* 规则内部应可被中断（及时响应 `InterruptedException` 与中断标志）；
  单条规则抛异常只导致该文件失败，不影响其他规则与其他文件。

---

## 8. 验证方法（测试）

```bash
mvn test
```

| 测试类 | 覆盖需求 |
|---|---|
| `PluginLoadingTest` | 类缺失、构造抛异常、空白插件名、rules 抛异常、id 前缀不符、非插件类、重复 id；健康插件不受影响 |
| `ServiceLoaderPluginTest` | SPI 发现内置插件 |
| `ConfigHierarchyTest` | 继承/覆盖、disable 继承、非法配置可定位、并行度 1 与 16 结果逐条一致 |
| `SuppressionTest` | 同行/上行抑制、suppress-all、disable/enable 区域边界、未知规则与坏标记提示 |
| `FixConflictTest` | 重叠修复最高 rule-id 取胜、被拒规则上报、修复后复查清空、二次运行稳定、顺序无关 |
| `EngineEndToEndTest` | 内置规则修复+复查、6 线程/不同并行度结果完全一致 |
| `IncrementalCacheTest` | 未改文件不重检、改单文件只重检该文件、父/子配置变化的失效范围、规则集指纹隔离、LRU 有界淘汰 |
| `CancellationTest` | 单文件超时隔离、取消后再全量检查一致、取消修复不产生半写文件 |

---

## 9. HTTP 接口

| 方法与路径 | 说明 |
|---|---|
| `GET /lint?path=&incremental=&parallelism=&timeoutMillis=` | 执行检查（默认全量） |
| `POST /lint/fix?path=&parallelism=&timeoutMillis=` | 修复并复查 |

响应字段：`diagnostics`、`failures`（文件级失败）、`pluginFailures`、
`configProblems`、`fixedFiles`、`suppressedCount`、`cancelled`、`clean`。
