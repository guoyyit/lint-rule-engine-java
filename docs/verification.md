# 验证方法

## 环境

- JDK 17
- Maven 3.9+
- Spring Boot 3.4.1（Web 可选，核心引擎不依赖 Spring）

## 构建

```bash
mvn -q compile
```

## 测试

```bash
mvn test
```

测试覆盖以下场景（共 36 个用例）：

| 测试类 | 覆盖场景 |
|---|---|
| `PluginRegistryTest` | 内置插件加载、失败插件隔离、空 id、重复规则 id、健康插件不受失败插件影响 |
| `ConfigInheritanceTest` | 子目录覆盖父配置、同级目录互不影响、孙目录继承合并配置、全局配置被目录配置覆盖、畸形配置可定位 |
| `SuppressionTest` | next-line / line / disable-enable 区块、全规则抑制、`#` 前缀、未知规则提示、错误拼写提示、提示不可抑制、多次运行稳定 |
| `FixConflictTest` | 冲突修复确定性裁决、内容被用户修改时中止不覆盖、非冲突修复后复查通过、无可修复项、多次修复结果一致 |
| `IncrementalCacheTest` | 未改变文件命中缓存、单文件改变只失效该文件、配置变化全部失效、规则集变化全部失效、缓存有界、缓存与全新检查结果一致 |
| `ConcurrencyCancelTest` | 单文件超时不污染其他文件、取消后已完成结果保留、取消后重查与全量一致、不同文件顺序/线程数结果一致、规则抛异常不崩溃 |
| `LintEngineEndToEndTest` | 目录遍历跳过构建目录、诊断稳定排序、检查幂等、干净文件无诊断 |

## 手动验证

启动应用后：

```bash
# 查看已加载插件与加载失败
curl -s http://localhost:8080/api/lint/plugins

# 检查指定目录（目录遍历）
curl -s -X POST http://localhost:8080/api/lint/check \
  -H 'Content-Type: application/json' \
  -d '{}'

# 检查指定文件
curl -s -X POST http://localhost:8080/api/lint/check \
  -H 'Content-Type: application/json' \
  -d '{"files": ["src/main/java/Example.java"]}'
```

## 确定性保证

- 插件按 id 排序加载，规则按 id 排序执行；
- 诊断按 `(文件路径, 行, 列, 规则 id, 消息)` 稳定排序；
- 文件结果按路径排序；
- 修复冲突按 `(起始偏移, 结束偏移, 规则 id)` 确定优先级；
- 与检查顺序、并发度无关。
