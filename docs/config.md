# 配置语义说明

## 配置文件

配置文件名为 `.lintrc.json`，放置在任意目录中，对该目录及其所有子目录生效。

```json
{
  "rules": {
    "no-todo": { "enabled": true, "severity": "error" },
    "trailing-whitespace": { "enabled": false }
  },
  "include": ["**/*.txt"],
  "exclude": ["**/generated/**"]
}
```

- `rules.<rule-id>.enabled`：是否启用该规则（缺省继承）。
- `rules.<rule-id>.severity`：严重级别覆盖，取值 `error` / `warning` / `info`（缺省继承）。
- `include` / `exclude`：glob 模式，目录遍历时过滤文件（子目录只能追加模式）。

## 继承与覆盖

对任意文件，配置解析顺序为：

1. 引擎的全局配置（`LintEngine.builder().globalConfig(...)`，可选）；
2. 从根目录到文件所在目录沿途每个 `.lintrc.json`，子目录配置合并覆盖父目录。

合并规则：

- 规则配置逐规则合并：子配置中出现的字段覆盖父配置，未出现的字段继承父值。
  例如父配置 `severity=error`，子配置 `enabled=false` → 该规则在子目录被禁用；
  子配置 `severity=warning` → 级别降为 warning。
- `include` / `exclude` 集合取并集（子目录只能增加过滤模式）。

因此：

- 子目录的规则集与级别可以与父目录不同；
- 同级目录（sibling）互不影响，各自继承父配置；
- 未配置的规则使用规则默认级别且默认启用。

## 配置缓存

- 配置按目录缓存，缓存键包含配置文件的最后修改时间；文件修改后自动重新加载。
- 配置变化会使增量缓存中所有相关文件的结果失效（缓存键包含配置指纹）。

## 严重级别

`Severity` 枚举：`ERROR`、`WARNING`、`INFO`。规则默认级别由插件声明，
配置可覆盖。抑制提示（suppression hint）固定为 `WARNING`。
