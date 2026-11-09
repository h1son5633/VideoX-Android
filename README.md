<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="VideoX 图标">

# VideoX

**粘贴链接，一键保存抖音 / B 站的视频、图集、动图和音乐**

[![最新版本](https://img.shields.io/github/v/release/h1son5633/VideoX-Android?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=blue)](https://github.com/h1son5633/VideoX-Android/releases/latest)
[![构建状态](https://img.shields.io/github/actions/workflow/status/h1son5633/VideoX-Android/android.yml?label=%E6%9E%84%E5%BB%BA)](https://github.com/h1son5633/VideoX-Android/actions)
![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)

</div>

## 这是什么

VideoX 是一个 Android 小工具：在抖音或 B 站看到喜欢的作品，复制分享链接粘贴进来，就能把视频、图片、动图、音乐保存到手机里，没有平台水印的烦恼，界面干净、没有广告。

## 主要功能

- 🎬 **视频解析** — 抖音、B 站分享链接粘贴即解析，视频直接下载到相册
- 🖼️ **图集与图文笔记** — 图集、图文笔记（note）整帖解析，图片可多选批量保存
- 🎞️ **动图保存** — 抖音动图（实况照片）会合成一张完整的动态照片保存，并针对 OPPO / 一加（ColorOS）相册做了识别适配，在相册里能直接动起来
- 🎵 **音乐提取** — 作品的背景音乐可以单独保存
- 🔗 **分享直达** — 在抖音等 App 里点「分享 → VideoX」，链接自动带入，直接解析
- 🔀 **接口自动切换** — 内置三个解析接口，当前接口解析失败时自动顺延到下一个重试，不用手动切换（v1.2.5 新增）
- 📂 **下载管理** — 下载记录按视频 / 图片 / 动图 / 音乐分类筛选；视频、图片、音乐可以分别设置保存文件夹
- 🕘 **解析历史** — 解析过的作品自动留档，随时回看、重新下载，可单条删除
- 🚀 **应用内更新** — 新版在 App 里直接检查更新，多线程分段下载 + 国内加速线路，国内网络也能顺利更新
- 🎨 **个性化** — 主题颜色、底部导航样式可调；支持仅 Wi-Fi 下载

## 下载安装

前往 [Releases 页面](https://github.com/h1son5633/VideoX-Android/releases/latest) 下载最新版 `app-release.apk` 安装即可（需要 Android 6.0 及以上）。

已安装旧版本的用户：在 App 的「设置 → 关于 → 检查更新」里可以直接升级。

## 使用方法

1. 在抖音 / B 站点开作品，点分享 → 复制链接
2. 打开 VideoX，粘贴链接，点「开始解析」
3. 在结果页选好要保存的视频、图片或音乐，点下载即可

## 解析接口

解析由第三方接口提供，App 内置三条线路并可自动切换：

| 接口 | 支持范围 | 说明 |
| --- | --- | --- |
| 接口一（推荐） | 抖音 / B 站 / 动图 | 全功能主力接口 |
| 接口二 | 抖音 / B 站 / 动图 / 图文笔记 | 备用线路，图文笔记靠它解析 |
| 接口三 | 仅抖音 | 速度快，但动图只返回静态图 |

默认用你选定的接口解析；失败时自动按顺序换下一条，直到成功或全部试完。

## 技术栈

Kotlin + Jetpack Compose，单文件解析调度与多接口适配；更新包采用多线程分段下载与多线路回落。

## 免责声明

本工具仅供个人学习与交流使用。解析服务由第三方接口提供，作品内容的版权归原作者及所属平台所有，请勿将下载的内容用于任何商业用途。
