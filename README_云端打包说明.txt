不用 Android Studio 的打包方式：

1. 新建 GitHub 仓库。
2. 解压本压缩包。
3. 把 EnterpriseCustomerManager 文件夹内的 app、.github、build.gradle、settings.gradle 等文件上传到仓库根目录。
4. 打开仓库 Actions 页面。
5. 左侧选择 Build Android APK。
6. 点击 Run workflow。
7. 等待绿色对勾。
8. 在运行详情页底部 Artifacts 下载 enterprise-customer-manager-debug-apk。
9. 解压后得到 app-debug.apk，发送到手机安装。

如果安装新版提示“应用未安装”，请先卸载旧版本，再安装新版。卸载App会清空App内部数据，但不会删除已写入手机通讯录的联系人。
