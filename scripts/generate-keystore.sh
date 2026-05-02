#!/bin/bash
# 生成 Android Release 签名密钥的脚本
# 使用方法：在本地运行此脚本，然后将生成的密钥配置到 GitHub

echo "=== Android Release 签名密钥生成工具 ==="
echo ""
echo "请提供以下信息来生成签名密钥："
echo ""
echo "示例值："
echo "  密钥别名 (alias): bloodpressure"
echo "  密钥有效期: 10000 天"
echo "  名字: Yang Yang"
echo "  组织单位: Personal"
echo "  组织: Personal"
echo "  城市: Beijing"
echo "  省份: Beijing"
echo "  国家代码: CN"
echo ""

# 生成密钥的命令（需要在本地运行）
cat << 'COMMAND'
keytool -genkeypair -v \
  -alias bloodpressure \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.jks \
  -storetype JKS

# 然后将密钥转换为 Base64
base64 -i release.jks | tr -d '\n' > keystore_base64.txt

echo ""
echo "=== 下一步操作 ==="
echo "1. 将 keystore_base64.txt 的内容设置为 GitHub Secret: KEYSTORE_BASE64"
echo "2. 将密钥库密码设置为 GitHub Secret: KEYSTORE_PASSWORD"
echo "3. 将密钥别名设置为 GitHub Secret: KEY_ALIAS (默认: bloodpressure)"
echo "4. 将密钥密码设置为 GitHub Secret: KEY_PASSWORD"
COMMAND
