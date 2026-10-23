#!/bin/sh
# 固定官方 Java 17 安全补丁及校验和；只在构建容器中解包，不修改宿主机环境。
set -eu

kind="${1:?请指定 jdk 或 jre}"
destination="${2:?请指定解包目录}"
case "$kind" in
  jdk) checksum="8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711" ;;
  jre) checksum="9f41bf88c7900c7097444a1bdeb84146ae692bc08842490ab098063c74bf9a8a" ;;
  *) echo "不支持的 Java 包类型：$kind" >&2; exit 1 ;;
esac

# 当前生产 CI 仅构建 linux/amd64，禁止静默安装不匹配的原生二进制。
test "$(uname -m)" = "x86_64"
archive="$(mktemp)"
trap 'rm -f "$archive"' EXIT HUP INT TERM
curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \
  "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-${kind}_x64_linux_hotspot_17.0.21_9.tar.gz" \
  --output "$archive"
printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --strict -
mkdir -p "$destination"
tar --extract --gzip --file "$archive" --directory "$destination" --strip-components=1
grep -Fx 'JAVA_RUNTIME_VERSION="17.0.21+9"' "$destination/release"
