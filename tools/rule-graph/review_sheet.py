"""물어보기 규정 그래프 검토표 만들기(docs/ask-rule-graph-plan.md 10절 3).

rule-graph.json 을 읽어 docs/ask-rule-graph-review.md 를 다시 쓴다 — 연결 그림(Mermaid)과 선별 검토 표.
검토는 표에서 하고, 맞은 선은 rule-graph.json 의 reviewed 에 'YYYY-MM-DD 이름'을 적은 뒤 이 스크립트를 다시 돌린다.

실행: python3 -I tools/rule-graph/review_sheet.py   (hongikon-be 에서)
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
GRAPH = ROOT / "src/main/resources/ask/rule-graph.json"
OUT = ROOT / "docs/ask-rule-graph-review.md"
ARROWS = {"결과": "-->", "예외": "-.->", "제한": "==>", "선행조건": "-->", "기간": "-.->"}


def cell(text):
    return str(text).replace("|", "\\|").replace("\n", " ")


def condition_text(condition):
    if not condition:
        return ""
    return ", ".join(key if value is True else f"{key}={value}" for key, value in condition.items())


def main():
    graph = json.loads(GRAPH.read_text(encoding="utf-8"))
    nodes = {node["id"]: node for node in graph["nodes"]}
    edges = graph["edges"]
    done = [e for e in edges if (e.get("reviewed") or "").strip()]

    lines = [
        "# 물어보기 — 규정 그래프 검토표",
        "",
        f"`tools/rule-graph/review_sheet.py` 가 `rule-graph.json`(version {graph['version']})에서 만든다. 손으로 고치지 않는다.",
        f"설계: [`ask-rule-graph-plan.md`](ask-rule-graph-plan.md). 점 {len(nodes)}개, 선 {len(edges)}개 — **확인됨 {len(done)} / 확인 전 {len(edges) - len(done)}**.",
        "",
        "## 검토하는 법",
        "",
        "1. 아래 표에서 선마다 **사실** 이 **근거 원문** 과 맞는지, 학생에게 그대로 말해도 되는지 본다.",
        "2. 맞으면 `rule-graph.json` 의 그 선 `reviewed` 에 `\"YYYY-MM-DD 이름\"` 을 적는다. 고칠 게 있으면 `fact`·`condition` 을 고치고 적는다. 틀리면 선을 지운다.",
        "3. `python3 -I tools/rule-graph/validate.py` 로 검사하고, 이 스크립트로 표를 다시 만든다.",
        "",
        "서버는 `reviewed` 가 있는 선만 쓴다. 확인 전 선은 켜도 쓰이지 않는다.",
        "",
        "## 연결 그림",
        "",
        "실선 = 결과·선행조건, 굵은 선 = 제한, 점선 = 예외·기간. 선 이름 뒤 ✅ 는 확인된 선.",
        "",
        "```mermaid",
        "flowchart LR",
    ]
    for node in graph["nodes"]:
        lines.append(f'  {node["id"].replace("-", "_")}["{node["name"]}"]')
    for edge in edges:
        label = edge["type"] + (f" · {condition_text(edge.get('condition'))}" if edge.get("condition") else "")
        if (edge.get("reviewed") or "").strip():
            label += " ✅"
        arrow = ARROWS.get(edge["type"], "-->")
        lines.append(f'  {edge["from"].replace("-", "_")} {arrow}|"{label}"| {edge["to"].replace("-", "_")}')
    lines += ["```", "", "## 선별 검토", ""]

    by_from = {}
    for edge in edges:
        by_from.setdefault(edge["from"], []).append(edge)
    for node_id, group in by_from.items():
        lines += [f"### {nodes[node_id]['name']}", "",
                  "| 확인 | 선 | 종류 | 조건 | 사실(답 쓰기에 들어가는 문장) | 근거 원문 |",
                  "|---|---|---|---|---|---|"]
        for edge in group:
            mark = "✅ " + cell(edge["reviewed"]) if (edge.get("reviewed") or "").strip() else "☐"
            lines.append(
                f"| {mark} | `{edge['id']}`<br>{cell(nodes[edge['from']]['name'])} → {cell(nodes[edge['to']]['name'])} "
                f"| {edge['type']} | {cell(condition_text(edge.get('condition')))} | {cell(edge['fact'])} "
                f"| `{edge['guide']}`<br>“{cell(edge['quote'])}” |")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)} 를 썼다: 선 {len(edges)}개(확인됨 {len(done)})")


if __name__ == "__main__":
    main()
