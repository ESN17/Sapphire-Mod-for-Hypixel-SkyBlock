# Sapphire 1.0.1 源码

Sapphire 是面向 Hypixel SkyBlock 的客户端中文汉化模组。主工程使用 Minecraft 26.3 和 Java 25。源码包包含 Java 源码、测试、内置译名库、Gradle Wrapper、第三方许可和三个发行版本的适配规则。

## 构建

安装 JDK 25，将 JAVA_HOME 指向该 JDK。解压 ZIP 后在项目根目录运行以下命令。首次构建需要联网下载 Gradle、Minecraft 和 Fabric 依赖。

```powershell
.\gradlew.bat build
```

在 Linux 和 macOS 上使用 `./gradlew build`。产物位于项目的 `build/libs/`。

## 构建对应发布页的安装包

使用 Python 3.8 或更新版本运行以下命令，将版本号换为 `26.1.2`、`26.2` 或 `26.3`。脚本在新的目录中应用该版本的 Minecraft API 适配、Fabric API 版本和精确的 Minecraft 依赖声明。

```powershell
python prepare_target.py 26.1.2
cd build/mc26.1.2
.\gradlew.bat build
```

`release/source-sha256.json` 记录发布时的主工程源码和资源校验值。`release/JAR-SHA256.txt` 记录 GitHub 上三个安装包的校验值。需要外部参考模组、私人开发语料或在线凭据的可选测试按环境跳过。

许可证和数据来源见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
