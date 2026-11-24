# FunnyTool 固定共享开发签名

按用户明确要求，本目录公开本应用专用的固定签名私钥、口令及证书，供本地与 GitHub Actions 生成同证书 APK。**这不是保密的生产签名。任何拿到仓库的人均可签署同身份的伪造安装包，请只安装可信构建；禁止在其他应用复用此密钥。**

| 项目 | 值 |
|---|---|
| Keystore | `funnytool-shared.p12`（PKCS12） |
| Alias | `funnytool` |
| Store / key password | `funnytool-public-signing`（有意公开） |
| 算法 | RSA 3072 / SHA256withRSA |
| 公钥证书 | `funnytool-shared-cert.pem` |
| 证书 SHA-256 | `40d32ff8d57d87894d4198ad48b1c4da010a2e46ed7a2d3b5e96442296936d0a` |

默认 `assembleRelease` 使用此固定密钥，不再回退到临时 debug key。后续版本只有证书一致且 versionCode 不降低时才能覆盖升级。**不要重新生成或替换本文件**；已固定签名的版本应持续使用同一密钥。

旧 `v0.1.1` / `v0.1.2` 是临时 debug 签名，不能直接覆盖升级到此证书；首次切换通常需卸载旧版，卸载会删除配置。旧 Release 删除后仍保留 Git tag 和历史源码。

验证已下载 APK：
```bash
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs FunnyTool-v0.1.3-release.apk
```
核对 `Signer #1 certificate SHA-256 digest` 与上表一致，而不只是看验证成功。

如需正式保密签名，使用根 README 中的四项环境变量/GitHub Secrets 覆盖；私有密钥不要提交入库。切换到另一张证书可能再次导致不能直接覆盖安装，操作前明确迁移方案。
