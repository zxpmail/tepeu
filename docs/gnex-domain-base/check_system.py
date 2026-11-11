# -*- coding: utf-8 -*-
"""领域应用基座文档自检。在仓库根或本目录执行：python docs/gnex-domain-base/check_system.py"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent
SPEC = ROOT / "specs"

REQUIRED = [
    "00-项目管理/00-任务管控总表.md",
    "00-项目管理/01-任务执行序列.md",
    "00-项目管理/02-审计记录.md",
    "00-项目管理/03-决策日志.md",
    "01-需求分析.md",
    "02-同类产品调研.md",
    "03-场景与流程设计/00-场景分类总纲.md",
    "03-场景与流程设计/01-通用交互控制流程.md",
    "03-场景与流程设计/90-场景功能对账.md",
    "04-领域认知研究/00-认知研究框架.md",
    "04-领域认知研究/01-认知卡-基座.md",
    "04-领域认知研究/08-语义维规则总册.md",
    "04-领域认知研究/09-数据维资产目录.md",
    "04-领域认知研究/10-动作维工具映射册.md",
    "05-工具链审计/00-gnex3对照审计.md",
    "06-总体设计/00-总体设计方案.md",
    "06-总体设计/05-智能组装设计.md",
    "06-总体设计/07-接口设计.md",
    "06-总体设计/08-安装与恢复设计.md",
    "07-分域设计/00-分域设计总纲.md",
    "08-审计/00-审计方案.md",
    "09-网页呈现/index.html",
    "10-场景完备性分析.md",
    "11-细分领域-不适用.md",
    "12-企业私有认知-不适用.md",
    "产品设计说明.html",
    "specs/00-literals.md",
    "specs/00-contracts.md",
    "specs/spec-idn-context.md",
    "specs/spec-idn-grant.md",
    "specs/spec-app-registry.md",
    "specs/spec-app-router.md",
    "specs/spec-svc-catalog.md",
    "specs/spec-ui-schema.md",
    "specs/spec-ui-shell.md",
    "specs/spec-dat-source.md",
    "specs/spec-dat-entity.md",
    "specs/spec-dat-query.md",
    "specs/spec-sem-package.md",
    "specs/spec-sem-assemble.md",
    "specs/spec-pkg-install.md",
    "specs/spec-brg-gnex.md",
]

errors = []

def err(msg):
    errors.append(msg)

for rel in REQUIRED:
    if not (ROOT / rel).exists():
        err(f"缺文件 {rel}")

for p in sorted(SPEC.glob("spec-*.md")):
    text = p.read_text(encoding="utf-8")
    for i in range(1, 11):
        if f"## §{i} " not in text:
            err(f"{p.name} 缺少 §{i}")
    if "复决" not in text:
        err(f"{p.name} 选型没有复决条件")
    if "解耦红线" not in text:
        err(f"{p.name} 没有解耦红线")
    if "mvn " not in text and "npm test" not in text:
        err(f"{p.name} 没有验收命令")

req = (ROOT / "01-需求分析.md").read_text(encoding="utf-8")
frs = set(re.findall(r"FR-[A-Z]+-\d+|NFR-\d+", req))
blob = "\n".join(p.read_text(encoding="utf-8") for p in ROOT.rglob("*.md") if "_source" not in p.parts and "_gen" not in p.name)
for fr in sorted(frs):
    if fr not in blob.replace("01-需求分析.md", ""):
        # counted inside requirements file itself; require a second hit
        if blob.count(fr) < 2:
            err(f"{fr} 没有在需求以外被引用")

outline = (ROOT / "03-场景与流程设计/00-场景分类总纲.md").read_text(encoding="utf-8")
scenes = set(re.findall(r"S-[A-Z]+[0-9]+", outline))
scene_dir = ROOT / "03-场景与流程设计"
used = set()
for p in scene_dir.glob("*.md"):
    if p.name.startswith("00-"):
        continue
    used.update(re.findall(r"S-[A-Z]+[0-9]+", p.read_text(encoding="utf-8")))
extra = used - scenes
if extra:
    err(f"场景册出现总纲没有的编号 {sorted(extra)}")
missing = scenes - used
if missing:
    err(f"总纲场景未在场景册出现 {sorted(missing)}")

dec = (ROOT / "00-项目管理/03-决策日志.md").read_text(encoding="utf-8")
dids = set(re.findall(r"D-\d+", dec))
if len(dids) < 10:
    err("决策日志条目过少")

html = (ROOT / "09-网页呈现/index.html").read_text(encoding="utf-8")
for needle in ["身份", "应用包", "智能组装", "规格"]:
    if needle not in html:
        err(f"网页缺少「{needle}」")

print(f"检查项文件 {len(REQUIRED)} ，需求编号 {len(frs)} ，场景 {len(scenes)} ，决策 {len(dids)}")
if errors:
    print("失败", len(errors))
    for e in errors:
        print("-", e)
    sys.exit(1)
print("通过")
