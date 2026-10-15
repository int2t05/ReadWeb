# ReadWeb 1.0.0 发布验证

- **日期：** 2026-10-15
- **范围：** 公开仓库、标签工作流、签名 APK、校验文件与 API 36 安装启动
- **结论：** `v1.0.0` 已在 GitHub 正式发布。APK 和校验文件已从 Release 下载并独立核验；公开 APK 可在 API 36 模拟器安装启动。

## 发布位置

- 仓库：[int2t05/ReadWeb](https://github.com/int2t05/ReadWeb)
- Release：[ReadWeb v1.0.0](https://github.com/int2t05/ReadWeb/releases/tag/v1.0.0)
- 标签提交：`f511fc3691c197ed9acea29aeba8c8244a0c1a51`
- 资产：`app-release.apk`（189,729 字节）和 `app-release.apk.sha256`

## 独立验证

- [main 验证工作流](https://github.com/int2t05/ReadWeb/actions/runs/38726220278)、[v1.0.0 验证工作流](https://github.com/int2t05/ReadWeb/actions/runs/38726337519) 与 [签名 Release 工作流](https://github.com/int2t05/ReadWeb/actions/runs/38726337523) 均通过。
- 从公开 Release 下载 APK，`apksigner` 验签成功；包名、版本和 SDK 元数据为 `io.github.int2t05.readweb`、`1.0.0`、min SDK 26、target SDK 36。
- APK SHA-256：`b5b0eaff03907a6d9cbce1b871988736e7cc3e8a76f740e7269e4bf7b4c83adf`。证书 SHA-256：`ebf87bc4ce83934ff293ab0dcad6b7c9728a0bcb51e4153cfb7d9ff5006c91dd`。
- Release 下载件在 Android 16/API 36 模拟器安装并启动成功，没有 `AndroidRuntime` 致命异常。

## 校验文件修正

首次上传的 `.sha256` 中包含 CI 内部构建目录，因此用户下载到普通目录后无法直接运行 `sha256sum -c`。APK 字节和摘要值正确；已用只包含 `app-release.apk` 文件名的校验记录替换 Release 中的 sidecar，并从下载目录通过 `sha256sum -c` 复验。发布工作流已同步为在 APK 所在目录生成并检查 sidecar，供后续版本使用；本次已发布的 APK 未更改。

## 仍未验证

没有目标 Vivo/OriginOS 真机，也没有完成 PRD 中的设备内存、性能和长时间稳定性测量。API 36 模拟器检查仅覆盖安装、启动和此前记录的核心阅读流程。
