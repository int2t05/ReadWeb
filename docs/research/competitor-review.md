# ReadWeb 竞品与方案可信度评估

- **调研日期：** 2026-10-14
- **用途：** 判断原始需求材料中的产品与技术说法是否足以支持 ReadWeb 首版选型。
**证据限制：** 依据官方项目仓库或产品文档；未安装竞品实测，也没有同设备性能对比。官方功能说明只能证明其公开定位，不能证明其在目标手机上的性能。

## 结论

WebView 壳可以承载普通网页和在线 Markdown，但多标签管理、窗口策略、生命周期和内存回收仍由应用实现。现有证据不足以证明某个名为 FastWebView 的模板明确包含需求中的能力。Markor、Obsidian 和 EinkBro 可参考各自的交互或定位，均不能据此认定为 ReadWeb 的直接替代品。

## 核验结果

| 对象或说法 | 可核验事实 | 对 ReadWeb 的意义 |
| --- | --- | --- |
| “WebView 原生就支持多标签” | Android WebView 提供网页加载与页面导航能力；标签集合、窗口策略和回收行为需要宿主应用实现。 | 多标签属于 ReadWeb 自身功能，不能视为 WebView 自动提供的完整浏览器能力。 |
| “多个 WebView 完全隔离、低内存且更稳定” | 多个 WebView 并不等于完整的浏览器配置隔离；内存及稳定性取决于页面、系统 WebView 和宿主管理方式。 | 必须限制驻留实例并在目标设备测量，不能用架构推断性能结论。 |
| “开启跨域即可加载 Markdown” | Web 内容遵守 Web 安全模型；通过放宽文件 URL 跨源访问会扩大风险。 | ReadWeb 使用受限的 HTTP(S) 获取和本地可信阅读页，不应通过降低 WebView 安全设置解决。 |
| FastWebView | 原始材料未提供唯一、可确认的官方仓库链接；无法核实所称的极简、多标签和 Markdown 功能。 | 暂不作为选型依据。若后续提供确切仓库，再单独评估维护状态、功能和许可证。 |
| Markor | [官方仓库](https://github.com/gsantner/markor)将其描述为 Android 文本、Markdown、笔记和待办工具。 | 可参考本地文本与 Markdown 阅读交互；不是已证实满足多 URL 浏览需求的方案。 |
| Obsidian Android | [官方 Android 说明](https://obsidian.md/help/android)介绍的是 Android 笔记应用和文件库使用方式。 | 可参考 Markdown/笔记阅读体验；不据此推断任意网页浏览或内存表现。 |
| EinkBro | [官方仓库](https://github.com/plateaukao/einkbro)说明其采用 Android WebView 并面向电子墨水阅读；[官网](https://plateaukao.github.io/einkbro/)展示阅读和导出等功能。 | 是较接近的 WebView 阅读应用参考；公开材料不足以证明其满足 ReadWeb 的在线 Markdown 流程，亦无同条件性能数据。 |
| markdown-it 与 marked | [markdown-it](https://github.com/markdown-it/markdown-it) 和 [marked](https://github.com/markedjs/marked) 都是 Markdown 渲染组件，不是完整阅读应用。 | 没有同条件基准，不能得出谁“性能最高”。ReadWeb 采用 markdown-it 以匹配跨仓库统一方案，并关闭原始 HTML。 |

## 路线判断

| 路线 | 可确认的能力与限制 | 对首版的适配 |
| --- | --- | --- |
| PWA | 可安装到 Android 主屏，也可通过 Service Worker 缓存自有站点资源，不能据此称其“不能离线”。但作为自有网页应用，它不能保证把任意外部网站完整嵌入阅读器：网站可禁止被 iframe 嵌入，跨域脚本读取 Markdown 也取决于目标服务器的 CORS 配置。 | 适合自有网页和 Markdown 内容；不直接满足“同一应用内打开任意多个网址”的主要场景。 |
| Android WebView 壳 | 可把用户指定的网站作为顶层页面加载；标签列表、弹窗策略、页面回收、会话恢复和 Markdown 阅读模式由应用负责。仍依赖 Android System WebView 与网站兼容性。 | 更贴合首版多网址阅读需求，性能、内存和设备稳定性仍须实测。 |
| 纯原生页面 | 可使用原生 Markdown 库；普通网页仍需嵌入 WebView。分别维护两套内容呈现路径，会增加适配面。 | 可行，但本项目首版选择系统 WebView 加内置 Markdown 阅读页。 |

因此，不能把“PWA 一定离线差”或“WebView 一定比 PWA 稳定”当成已证实结论。尤其没有 vivo/OriginOS 目标设备测量，后台存活和内存表现只能列为待验证问题。

## 技术证据

- Android 官方 [WebView 指南](https://developer.android.com/develop/ui/views/layout/webapps/webview)说明了将 WebView 集成到应用的基本方式；窗口处理需要宿主参与。
- Android 官方 [WebView 对象管理](https://developer.android.com/develop/ui/views/layout/webapps/managing-webview)与[内存指南](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory)要求宿主关注 WebView 生命周期和内存管理。
- Android 官方[不安全文件包含风险说明](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion)解释了不安全本地内容及跨源配置的风险。
- web.dev 的 [PWA 安装说明](https://web.dev/learn/pwa/installation)和 [Service Worker 说明](https://web.dev/learn/pwa/service-workers)覆盖安装与资源缓存能力；MDN 的 [X-Frame-Options](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/X-Frame-Options)与 [CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)说明外站嵌入和跨域读取受站点响应策略控制。
- markdown-it 的[安全说明](https://github.com/markdown-it/markdown-it/blob/master/docs/safety.md)建议对不可信内容保持原始 HTML 关闭；其[安全公告](https://github.com/advisories/GHSA-253c-mchw-3w2r)记录了 linkify 复杂度问题及修复版本。

以上资料支持的是约束和公开功能定位，不构成目标设备性能证明，也不构成完整产品对比。完整的技术决策与当前实现参数见 [技术设计](../TECH.md)。
