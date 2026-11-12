#!/usr/bin/env bash
# dev.sh - 本地启动（不依赖 Docker），复用根目录 .env；Git Bash / WSL / macOS / Linux 通用
#
# 用法：
#   ./scripts/dev.sh             同时起前后端
#   ./scripts/dev.sh --dry-run   只打印解析后的配置
#   ./scripts/dev.sh --backend   只起后端
#   ./scripts/dev.sh --frontend  只起前端
#
# 关键点：.env 是给 docker compose 写的（MYSQL_*），
# 后端 Spring 读的是 DB_*，这里做映射后再注入进程环境。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
BE_DIR="$ROOT/backend"
FE_DIR="$ROOT/frontend"

DRY_RUN=0
RUN_BE=1
RUN_FE=1

for arg in "$@"; do
  case "$arg" in
    --dry-run)  DRY_RUN=1 ;;
    --backend)  RUN_FE=0 ;;
    --frontend) RUN_BE=0 ;;
    -h|--help)  sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "[error] 未知参数: $arg" >&2; exit 2 ;;
  esac
done

# ---- 读取 .env（手动解析，避免 source 时值里带空格出错）----
if [ -f "$ENV_FILE" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    trimmed="$(printf '%s' "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    case "$trimmed" in ''|\#*) continue ;; esac
    key="${trimmed%%=*}"
    val="${trimmed#*=}"
    key="$(printf '%s' "$key" | tr -d '[:space:]')"
    [ -z "$key" ] && continue
    case "$val" in
      \"*\") val="${val#\"}"; val="${val%\"}" ;;
      \'*\') val="${val#\'}"; val="${val%\'}" ;;
    esac
    export "$key=$val"
  done < "$ENV_FILE"
else
  echo "[warn] 未找到 $ENV_FILE，将使用 application.yml 默认值" >&2
fi

# ---- 映射：compose 变量 -> Spring 变量 ----
: "${DB_HOST:=localhost}"
: "${DB_PORT:=${MYSQL_PORT:-3306}}"
: "${DB_NAME:=${MYSQL_DATABASE:-lab_select_v2}}"
: "${DB_USER:=root}"
: "${DB_PASSWORD:=${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}}"
: "${SERVER_PORT:=8080}"
: "${WEB_PORT:=8089}"
export DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD SERVER_PORT WEB_PORT

if [ "$DRY_RUN" -eq 1 ]; then
  echo ""
  echo "解析后的运行配置（.env -> 进程环境变量）："
  printf "  %-18s %s\n" DB_HOST "$DB_HOST"
  printf "  %-18s %s\n" DB_PORT "$DB_PORT"
  printf "  %-18s %s\n" DB_NAME "$DB_NAME"
  printf "  %-18s %s\n" DB_USER "$DB_USER"
  printf "  %-18s %s\n" DB_PASSWORD "$([ -n "$DB_PASSWORD" ] && echo '****** (已注入)' || echo '(空)')"
  printf "  %-18s %s\n" SERVER_PORT "$SERVER_PORT"
  printf "  %-18s %s\n" WEB_PORT "$WEB_PORT"
  printf "  %-18s %s\n" JWT_SECRET "$([ -n "${JWT_SECRET:-}" ] && echo '****** (已注入)' || echo '(空)')"
  printf "  %-18s %s\n" JWT_EXPIRE_HOURS "${JWT_EXPIRE_HOURS:-}"
  printf "  %-18s %s\n" DEMO_DATA "${DEMO_DATA:-}"
  echo ""
  echo "文件检查："
  [ -f "$BE_DIR/pom.xml" ]           && echo "  backend/pom.xml        : OK"      || echo "  backend/pom.xml        : 缺失"
  [ -f "$FE_DIR/package.json" ]      && echo "  frontend/package.json  : OK"      || echo "  frontend/package.json  : 缺失"
  [ -d "$FE_DIR/node_modules" ]     && echo "  frontend/node_modules  : OK（无需 npm install）" || echo "  frontend/node_modules  : 缺失（需 npm install）"
  exit 0
fi

# ---- 前置检查 ----
if [ "$RUN_BE" -eq 1 ]; then
  [ -f "$BE_DIR/pom.xml" ] || { echo "[error] 找不到 backend/pom.xml，后端源码不在，无法启动。" >&2; exit 1; }
  command -v mvn >/dev/null 2>&1 || { echo "[error] PATH 里找不到 mvn。" >&2; exit 1; }
fi

if [ "$RUN_FE" -eq 1 ]; then
  [ -f "$FE_DIR/package.json" ] || { echo "[error] 找不到 frontend/package.json，前端源码不在（node_modules 有也不能跑）。" >&2; exit 1; }
  command -v npm >/dev/null 2>&1 || { echo "[error] PATH 里找不到 npm。" >&2; exit 1; }
fi

# ---- 启动（前台并行，Ctrl+C 一起停）----
PIDS=()

if [ "$RUN_BE" -eq 1 ]; then
  echo "[start] 后端 http://localhost:$SERVER_PORT (JVM: ${JAVA_OPTS:-默认})"
  # spring-boot:run 会 fork 独立 JVM，JVM 参数必须走 jvmArguments 而不是 MAVEN_OPTS
  if [ -n "${JAVA_OPTS:-}" ]; then
    ( cd "$BE_DIR" && mvn -f "$BE_DIR/pom.xml" spring-boot:run "-Dspring-boot.run.jvmArguments=${JAVA_OPTS}" ) &
  else
    ( cd "$BE_DIR" && mvn -f "$BE_DIR/pom.xml" spring-boot:run ) &
  fi
  PIDS+=($!)
fi

if [ "$RUN_FE" -eq 1 ]; then
  if [ ! -d "$FE_DIR/node_modules" ]; then
    echo "[install] 首次运行，安装前端依赖..."
    ( cd "$FE_DIR" && npm install ${NPM_REGISTRY:+--registry "$NPM_REGISTRY"} ) || { echo "[error] npm install 失败" >&2; exit 1; }
  fi
  echo "[start] 前端 http://localhost:$WEB_PORT"
  ( cd "$FE_DIR" && npm run dev -- --port "$WEB_PORT" --host ) &
  PIDS+=($!)
fi

cleanup() {
  echo ""
  echo "[stop] 正在停止子进程..."
  for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
}
trap cleanup INT TERM EXIT

wait
