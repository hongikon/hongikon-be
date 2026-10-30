"""문의(feedback) 내보내기 파일에서 개인정보 형태를 가린다 — 챗봇 개선용으로 문의 주제를 볼 때 원문 대신 이 결과만 읽는다.

사용(맥미니, 원문 파일은 git 에 안 올라가는 logs/ 에 둔다):
    python3 -I scripts/mask-feedback-export.py logs/feedback-export.tsv logs/feedback-masked.tsv

가리는 형태는 서버 물어보기 기록과 같다(ask/AskPrivacy.java): 주민번호·이메일·휴대전화·학번·8자리 이상 숫자.
추가로 일반 전화번호와 '이름: 홍길동' 같은 이름 표기를 가린다. 문장 속 사람 이름은 다 잡지 못하니 결과를 사람이 한 번 훑는다.
입력은 mysql -B 출력(탭 구분, 첫 줄 머리글: id, day, status, content). 원문 파일은 다 쓰면 지운다.
"""
import csv
import re
import sys

PATTERNS = [
    (re.compile(r"\d{6}\s*-\s*[1-8]\d{6}"), "[주민번호]"),
    (re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+"), "[이메일]"),
    (re.compile(r"01[016789][\s.-]?\d{3,4}[\s.-]?\d{4}"), "[전화번호]"),
    (re.compile(r"0\d{1,2}[\s.-]?\d{3,4}[\s.-]?\d{4}"), "[전화번호]"),
    (re.compile(r"(?<![A-Za-z0-9])[A-Za-z]\d{6,7}(?![A-Za-z0-9])"), "[학번]"),
    (re.compile(r"(?<!\d)\d{8,}(?!\d)"), "[번호]"),
    (re.compile(r"(이름|성함|본명)\s*(?:은|는|:)?\s*[가-힣]{2,4}"), r"\1 [이름]"),
]


def mask(text: str) -> str:
    for pattern, replacement in PATTERNS:
        text = pattern.sub(replacement, text)
    return text


def main(src: str, dst: str) -> None:
    with open(src, encoding="utf-8", newline="") as fin, open(dst, "w", encoding="utf-8", newline="") as fout:
        reader = csv.DictReader(fin, delimiter="\t", quoting=csv.QUOTE_NONE)
        writer = csv.writer(fout, delimiter="\t", quoting=csv.QUOTE_MINIMAL)
        writer.writerow(["id", "day", "status", "content"])
        count = 0
        for row in reader:
            writer.writerow([row["id"], row["day"], row["status"], mask(row["content"] or "")])
            count += 1
    print(f"{count}건 가림 → {dst}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("사용: python3 -I scripts/mask-feedback-export.py <내보낸 tsv> <가린 결과 tsv>")
    main(sys.argv[1], sys.argv[2])
