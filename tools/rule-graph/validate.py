"""물어보기 규정 그래프 검사(docs/ask-rule-graph-plan.md 4-1·6절).

서버(AskRuleGraph)와 같은 규칙으로 rule-graph.json 의 모든 선을 본다 — 확인 전(reviewed 없음) 선도 검사한다.
- 선의 from·to 점이 있고, type·condition 칸 이름이 허용된 것인가
- 근거 안내(guide)가 카탈로그에 있고, quote 가 그 안내 원문에 글자 그대로 있나
- 점의 guides 가 카탈로그에 있나, 선 id 가 겹치지 않나

실행: python3 -I tools/rule-graph/validate.py   (hongikon-be 에서. 문제가 있으면 끝 코드 1)
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASK = ROOT / "src/main/resources/ask"
TYPES = ["결과", "예외", "제한", "선행조건", "기간"]
CONDITION_KEYS = {"학년", "이수학기", "휴학중", "장학생", "분할납부", "군복무", "졸업유예"}


def main():
    graph = json.loads((ASK / "rule-graph.json").read_text(encoding="utf-8"))
    sources_file = json.loads((ASK / "sources.json").read_text(encoding="utf-8"))
    sources = sources_file["sources"]
    problems = []

    if graph.get("catalogVersion") != sources_file.get("version"):
        print(f"참고: catalogVersion {graph.get('catalogVersion')} ≠ 지금 카탈로그 {sources_file.get('version')} "
              "— 서버는 quote 가 원문에 있는 선만 쓴다(아래 quote 검사와 같다).")

    nodes = {}
    for node in graph.get("nodes", []):
        nodes[node["id"]] = node
        for guide in node.get("guides", []):
            if guide not in sources:
                problems.append(f"점 {node['id']}: guides 의 {guide} 가 카탈로그에 없다")

    seen = set()
    for edge in graph.get("edges", []):
        eid = edge.get("id")
        where = f"선 {eid}"
        if not eid or eid in seen:
            problems.append(f"{where}: id 가 없거나 겹친다")
        seen.add(eid)
        for end in ("from", "to"):
            if edge.get(end) not in nodes:
                problems.append(f"{where}: {end} 점 {edge.get(end)} 이 없다")
        if edge.get("type") not in TYPES:
            problems.append(f"{where}: type {edge.get('type')} 은 {TYPES} 가 아니다")
        condition = edge.get("condition") or {}
        if set(condition) - CONDITION_KEYS:
            problems.append(f"{where}: condition 칸 {sorted(set(condition) - CONDITION_KEYS)} 은 허용되지 않는다")
        if not (edge.get("fact") or "").strip():
            problems.append(f"{where}: fact 가 비었다")
        guide, quote = edge.get("guide"), edge.get("quote") or ""
        if guide not in sources:
            problems.append(f"{where}: 근거 안내 {guide} 가 카탈로그에 없다")
        elif not quote.strip() or quote not in sources[guide]:
            problems.append(f"{where}: quote 가 {guide} 원문에 글자 그대로 없다")

    edges = graph.get("edges", [])
    reviewed = sum(1 for e in edges if (e.get("reviewed") or "").strip())
    print(f"점 {len(nodes)}개, 선 {len(edges)}개(확인됨 {reviewed}, 확인 전 {len(edges) - reviewed})")
    for problem in problems:
        print("문제:", problem)
    print("검사 통과" if not problems else f"문제 {len(problems)}개")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
