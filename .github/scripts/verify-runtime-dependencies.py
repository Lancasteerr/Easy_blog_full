"""检查最终可执行 JAR 的版本收敛，以及两个临时风险例外的前提条件。"""

import json
from pathlib import Path
import re
import sys
from zipfile import ZipFile


def main():
    # 读取真正交付的嵌套依赖，避免只检查 POM 而漏掉版本覆盖失效。
    jar_path = Path(sys.argv[1])
    with ZipFile(jar_path) as archive:
        libraries = sorted(
            Path(name).name for name in archive.namelist()
            if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")
        )
    groups = {
        r"tomcat-embed-.+": "10.1.61",
        r"jackson-(?!annotations-).+": "2.21.7",
        r"jackson-annotations": "2.21",
        r"netty-.+": "4.1.137.Final",
        r"log4j-.+": "2.25.5",
    }
    for pattern, version in groups.items():
        matches = [name for name in libraries if re.fullmatch(pattern + r"-[0-9].*\.jar", name)]
        if not matches or any(not name.endswith(f"-{version}.jar") for name in matches):
            raise SystemExit(f"运行依赖版本未收敛：{pattern}，期望 {version}，实际 {matches}")

    # 这些功能一旦进入源码，原风险评估即失效；流水线要求重新处理，而非继续静默忽略。
    source_root = jar_path.parent.parent / "src/main/java"
    forbidden = re.compile(r"\b(XsltView|SseEmitter|ServerSentEvent|FragmentsRendering)\b")
    for source in source_root.rglob("*.java"):
        if forbidden.search(source.read_text(encoding="utf-8")):
            raise SystemExit(f"Spring 临时例外需要重新评估：{source}")
    print(json.dumps({"jar": jar_path.name, "libraries": libraries}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
