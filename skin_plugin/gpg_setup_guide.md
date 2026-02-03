# GPG 密钥配置指南

## ⚠️ 当前问题

您的 `aspectjx/gpg.asc` 文件包含的是 **公钥**（PUBLIC KEY），但 Maven Central 签名需要的是 **私钥**（PRIVATE KEY）。

当前文件内容：
```
-----BEGIN PGP PUBLIC KEY BLOCK-----
...
-----END PGP PUBLIC KEY BLOCK-----
```

需要的格式：
```
-----BEGIN PGP PRIVATE KEY BLOCK-----
...
-----END PGP PRIVATE KEY BLOCK-----
```

## 🔧 使用 Gpg4win 导出私钥

### 方法 1: 使用 Kleopatra GUI（推荐）

1. **打开 Kleopatra**（Gpg4win 的图形界面）

2. **找到您的密钥**
   - 密钥 ID: `4418 8577 D8C5 8269`
   - 密钥标识: `D8C58269`

3. **导出私钥**
   - 右键点击您的密钥
   - 选择 "Export Secret Keys..." 或"导出私钥..."
   - **不要**选择 "Export..." （那个导出的是公钥）

4. **保存文件**
   - 选择保存位置: `H:\work\projects\AspectJ\gradle_plugin_android_aspectjx\aspectjx\gpg.asc`
   - 文件格式选择: ASCII armored (`.asc`)
   - 点击保存

5. **输入密码**
   - 系统会要求输入密钥的保护密码
   - 这个密码就是您在 `gradle.properties` 中配置的 `signingPassword=D8C58269`

### 方法 2: 使用命令行

打开 PowerShell 或 CMD，执行：

```bash
# 导出私钥（ASCII 格式）
gpg --armor --export-secret-keys D8C58269 > H:\work\projects\AspectJ\gradle_plugin_android_aspectjx\aspectjx\gpg.asc

# 或者使用完整的密钥 ID
gpg --armor --export-secret-keys 44188577D8C58269 > H:\work\projects\AspectJ\gradle_plugin_android_aspectjx\aspectjx\gpg.asc
```

### 验证导出的文件

导出后，检查文件内容应该以以下内容开头：

```
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQdGBGlczcoWCSsGAQQB2kcPAQEHQLD...
...
-----END PGP PRIVATE KEY BLOCK-----
```

## 📝 配置说明

### 当前配置（已正确）

**gradle.properties**:
```properties
# GPG 签名密码（密钥的保护密码）
signingPassword=D8C58269

# 密钥文件路径（相对于项目根目录）
signingKeyFile=aspectjx/gpg.asc
```

**密钥信息**:
- 密钥 ID: `4418 8577 D8C5 8269`
- 短密钥 ID: `D8C58269`
- 文件位置: `H:\work\projects\AspectJ\gradle_plugin_android_aspectjx\aspectjx\gpg.asc`

### 路径匹配

✅ **相对路径配置正确**:
- `gradle.properties` 中: `signingKeyFile=aspectjx/gpg.asc`
- 实际路径: `项目根目录/aspectjx/gpg.asc`
- 这两个路径是匹配的

## 🔐 安全建议

1. **不要提交私钥到版本控制**
   
   在 `.gitignore` 中添加：
   ```
   aspectjx/gpg.asc
   gradle.properties
   ```

2. **使用环境变量**（可选，更安全）
   
   不在 `gradle.properties` 中存储密码，而是使用环境变量：
   ```bash
   # Windows PowerShell
   $env:SIGNING_PASSWORD="D8C58269"
   
   # Windows CMD
   set SIGNING_PASSWORD=D8C58269
   ```

3. **备份私钥**
   
   将私钥文件备份到安全的地方，不要只保存在项目目录中。

## ✅ 验证签名配置

导出正确的私钥后，测试签名：

```bash
# 测试构建和签名
./gradlew :aspectjx:build

# 测试发布（会进行签名）
./gradlew :aspectjx:publishToMavenLocal
```

如果配置正确，您会看到生成的 `.asc` 签名文件：
- `aspectjx-2.0.10.jar.asc`
- `aspectjx-2.0.10-sources.jar.asc`
- `aspectjx-2.0.10-javadoc.jar.asc`
- `aspectjx-2.0.10.pom.asc`

## 📚 参考资料

- [Gpg4win 官方文档](https://www.gpg4win.org/documentation.html)
- [Maven Central GPG 要求](https://central.sonatype.org/publish/requirements/gpg/)
- [Gradle Signing Plugin](https://docs.gradle.org/current/userguide/signing_plugin.html)
