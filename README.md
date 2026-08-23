# Chaos IDEA Plugin（CustomScript）

基于 IntelliJ IDEA 平台的个人效率插件，为 IdeaVim 补充文本操作与终端工具。

> 本项目是个人门面/效率工具聚合，仅收录精选功能，不堆砌。更多相关项目见
> [xieqiansong/xieqiansong](https://github.com/xieqiansong/xieqiansong)。

## 功能概览

- **自定义文本替换**（右键菜单 `Custom Actions`）
  - 字符串反转 / 压缩空白
  - 多行转 SQL `WHERE xxx IN (...)`
  - CSV 转 SQL `VALUES(...)`
- **终端工具**
  - 发送选中文本到终端（`Send to Terminal`）
  - 导出终端全部输出到 `$CUSTOM_HOME/terminal-out/*.log`（持续刷新）
- **IdeaVim 扩展**（按 `.ideavimrc` 中 `set ...` 启用）
  - `set easymotion`
  - `set which-key`
  - `set quickscope`
  - function-text-obj 文本对象

## 依赖

- IDEA 2024.3.6（Community），JDK 17
- IdeaVim、AceJump、JetBrains Terminal

## 许可证与参考来源

本项目整体采用 **GPL-3.0** 许可，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

其中包含/参考的第三方扩展（各保留其原始许可证与版权声明）：

| 组件             | 许可证         | 来源 / 参考                                                          |
|------------------|----------------|----------------------------------------------------------------------|
| EasyMotion       | GPL-3.0-or-later | <https://github.com/AlexPl292/IdeaVim-EasyMotion>                 |
| Which-Key        | GPL-3.0        | <https://github.com/TheBlob42/idea-which-key>                        |
| QuickScope       | 本地重构（参考 MIT） | 本地 Java 重写（`QuickScopeExtension`），功能思路参考 <https://github.com/unblevable/quick-scope>（MIT），不含第三方源码 |
| FunctionTextObj | MIT | Copyright (c) 2024 Julien Phalip，<https://github.com/jphalip/ideavim-functiontextobj> |
| Peekaboo        | MIT | Copyright (c) 2024 Julien Phalip，<https://github.com/jphalip/ideavim-peekaboo>        |

> 说明：上表各组件均在各自许可证条款下使用；MIT 组件保留其版权声明。

<!-- 处于「规划中/整理中」的内容以「敬请期待 / 整理中」标注，不虚构已完成。 -->