# ReadWeb 1.0.0 发布就绪审查

- **日期：** 2026-10-15
- **范围：** 首版实现、发布构建、仓库交付资料和 API 36 模拟器核心流程
**结论：** 1.0.0 源码版本已准备好本地标记；已验证的功能范围通过。没有生产签名密钥或 GitHub 远程仓库，因此没有生成可分发签名包，也没有公开发布。

## 发布前修正

- API 36 模拟器首次加载在线 Markdown 时触发 WebView 主线程崩溃。根因是后台 `shouldInterceptRequest` 回调调用了 `WebView.getUrl()`，并访问了非线程安全的临时映射。现在后台回调只把请求交给 `WebViewAssetLoader`，不调用视图方法；顶层导航仍拦截普通网页直接进入内部阅读页。
- Release 冒烟发现从普通网页返回 Markdown 会白屏。对历史中的阅读页重新获取 Markdown 并注入已加载的阅读文档，不重复创建历史项；Release 收缩包往返导航验证通过。
- 短 Markdown 页面曾显示 0% 进度。进度改为根据文档和视口的 CSS 像素异步计算，并合并快速连续的滚动更新；单屏以内显示 100%。
- 将应用版本设为 `1.0.0`，Release 变体启用 R8 和资源压缩；添加签名配置、tag 驱动的 GitHub Release 工作流、发布步骤、变更记录和安全报告说明。

## 独立验证

环境为 `ReadWeb_API36` 模拟器、Android 16/API 36、Android System WebView `133.0.6943.137`。使用本机临时 HTTP 测试页与 Markdown 文档，不访问登录站点。

- 普通网页按原有 HTML 布局显示；页内 `.md` 链接切换到 Markdown 阅读视图，标题、强调、列表与大纲均可见。
- 从 Markdown 打开普通网页，再返回后，原 Markdown 内容恢复且应用无崩溃。
- 左侧功能区可以加入并查看收藏；短文档显示 100%。长文档在起始位置为 0%，滚动后为 9%，滚至底部为 100%。
- Markdown 字号从 18 px 调至 19 px；新增第二个标签、打开普通网页并切换回 Markdown 标签。
- 强制结束并重启应用后，选中标签和 Markdown 内容恢复；收藏仍存在，功能区仍显示已收藏状态与 19 px 字号。
- 切换 Android 深色模式后，Markdown 阅读页与应用界面采用深色配色。
- R8/资源压缩后的 Release APK 用本地 Android Debug 密钥仅为模拟器测试临时签名；浏览网页、Markdown、回退阅读及功能面板均可工作。这个测试包不用于分发。
- 修正后台线程错误后，以上交互未再出现 `AndroidRuntime` 崩溃记录。

- Android JVM 测试 28 项通过；`lintDebug`、`lintRelease`、`assembleDebug` 和 R8/资源压缩 `assembleRelease` 全部通过。
- Markdown renderer 测试 7 项通过；`npm audit --audit-level=high` 报告 0 个漏洞；同步生成的 Android renderer 资源已核验。
- Debug APK 在模拟器安装验证；Release APK 仅以调试密钥临时签名后验证，不是正式发布包。APK 元数据为 `io.github.int2t05.readweb`、`1.0.0`、min SDK 26、target SDK 36。
- 两个 GitHub Actions workflow YAML 均可解析；11 个 Markdown 文件的本地链接均可解析；仓库源码扫描未发现密钥或令牌格式匹配。

Android API 36 模拟器验证是功能冒烟测试，不代表 Vivo/OriginOS 设备兼容性、长时间稳定性或性能指标已经达标。

## 尚需完成的发布门槛

- 建立并确认 `int2t05/ReadWeb` 的 GitHub 远程仓库及公开可见性；当前 checkout 没有 `origin`，账号下尚无该仓库。
- 生成并安全备份持久 Release keystore，配置 GitHub Actions 四项签名密钥。工作流会拒绝未签名 APK。
- 对目标 Vivo/OriginOS 手机执行安装和设备验证；PRD 中的启动、切换、内存及长时间稳定性阈值尚无测量数据。
- 完成上述设置后，再单独确认推送初始提交和 `v1.0.0` 标签。当前审查未推送、未创建远程 Release。
