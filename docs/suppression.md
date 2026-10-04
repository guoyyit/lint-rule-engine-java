# 行内抑制说明

在文件中通过注释标记抑制诊断。标记前可加注释前缀：`//`、`#`、`<!--`、`--`、`;`，
也可以直接顶格写（行首无注释前缀）。

## 标记语法

```
// lint-disable [r1, r2]        禁用规则，直到文件末尾或 lint-enable
// lint-enable [r1, r2]         重新启用规则
// lint-disable-line [r1, r2]    仅禁用当前行
// lint-disable-next-line [r1, r2]  仅禁用下一行
```

- 规则列表为逗号分隔；为空表示所有规则。
- 作用范围确定：
  - `lint-disable` 从标记所在行开始生效，直到对应的 `lint-enable`（enable 所在行恢复）；
  - `lint-disable-line` 仅作用于标记所在行；
  - `lint-disable-next-line` 仅作用于标记的下一行。

## 错误提示

- 标记引用了不存在的规则 → 在标记所在行给出提示（`Unknown rule 'x' ...`）。
- 标记拼写错误（如 `lint-disbale`、`lint-diable`、`lint-enble` 等）→
  在标记所在行给出 `Malformed suppression marker ...` 提示。
- 提示诊断的规则 id 为 `lint-engine:suppression`，级别 WARNING，**不会被任何抑制标记抑制**。

## 示例

```java
// lint-disable-next-line no-todo
TODO 这行不会被报告

// lint-disable no-todo
TODO 区块内不会被报告
// lint-enable no-todo
TODO 这行恢复报告

TODO 同一行 // lint-disable-line no-todo
```
