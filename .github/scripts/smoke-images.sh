#!/usr/bin/env bash
# 使用隔离网络验证交付镜像；匿名边界检查不依赖真实数据库、生产凭据或宿主机端口。
set -euo pipefail
backend_image="${1:-easy-blog-backend:ci}"
frontend_image="${2:-easy-blog-frontend:ci}"
suffix="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}-$$"
network="easy-blog-smoke-$suffix"
backend="easy-blog-backend-$suffix"
frontend="easy-blog-frontend-$suffix"
response_file="$(mktemp)"

cleanup() {
  result=$?
  if [ "$result" -ne 0 ]; then
    docker logs "$backend" 2>&1 || true
    docker logs "$frontend" 2>&1 || true
  fi
  docker rm -f "$frontend" "$backend" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f "$response_file"
  exit "$result"
}
trap cleanup EXIT

# 确认非 root 身份和仅 JRE 的运行阶段，不限制后续依赖补丁升级。
docker run --rm --entrypoint sh "$backend_image" -c \
  'test ! -e /opt/java/openjdk/bin/javac'
for image in "$backend_image" "$frontend_image"; do
  user="$(docker image inspect --format '{{.Config.User}}' "$image")"
  test -n "$user" && test "$user" != root && test "$user" != 0
done

docker network create "$network" >/dev/null
docker run --detach --name "$backend" --network "$network" --network-alias backend \
  -e SPRING_PROFILES_ACTIVE=dev -e BLOG_SCHEDULING_ENABLED=false \
  -e BLOG_JWT_SECRET=ci-only-smoke-secret-at-least-32-bytes \
  "$backend_image" >/dev/null
docker run --detach --name "$frontend" --network "$network" \
  --publish 127.0.0.1::8080 "$frontend_image" >/dev/null
base_url="http://$(docker port "$frontend" 8080/tcp)"

# 页面与后端均就绪后再校验状态码，避免把启动中的 502 误判成接口回归。
ready=false
for attempt in $(seq 1 60); do
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$base_url/api/public/article?id=0" || true)"
  if [ "$status" = 400 ]; then ready=true; break; fi
  sleep 1
done
test "$ready" = true

expect_status() {
  expected="$1"; shift
  actual="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' "$@")"
  if [ "$actual" != "$expected" ]; then
    echo "HTTP 兼容性失败：期望 $expected，实际 $actual" >&2
    cat "$response_file" >&2
    return 1
  fi
}
expect_status 200 "$base_url/"
expect_status 200 "$base_url/neko-panel/login"
expect_status 400 "$base_url/api/public/get_article_list?page=0&size=10"
expect_status 401 -X POST -H 'Content-Type: application/json' --data '{}' "$base_url/api/admin/content/article"
expect_status 401 -X POST -H 'Authorization: Bearer invalid.token.value' "$base_url/api/admin/logout"
expect_status 400 -X POST -H 'Content-Type: application/json' --data '{"userName":' "$base_url/api/public/login"
echo "镜像运行身份、启动、同源反代及匿名认证边界检查通过"
