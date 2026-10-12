# 배포 절차서 — 경로망 서버 이전 (feat/path-network)

앱이 `src/constants/pathNodes.ts` 에 하드코딩하던 경로망(점·간선)을 서버 테이블로 옮기고 `GET /map/data` 의 `paths` 로 내려준다.
관리자 API 는 `docs/admin-api-spec.md` 의 "지도 경로망". 운영 배포는 GitHub Actions `Deploy` 워크플로(`docs/ci-cd.md` 5절)로 한다.

`ddl-auto=validate` 라 **SQL 을 빠뜨리면 서버가 기동하지 않는다.** 새 테이블만 더하므로 SQL 을 먼저 실행해도 지금 서버는 영향이 없다.

| 단계 | 할 일 | 누가 |
|---|---|---|
| 0 | 사전 확인 | 운영자 |
| 1 | RDS 스냅샷 → `db/create_path_network_tables.sql` | RDS 접근 권한자 |
| 2 | dev → main 머지 → Deploy 수동 실행(`sql_applied=true`) → 승인 | 운영자 |
| 3 | 경로망 임포트(dryRun → 적용) | ADMIN |
| 4 | 확인 | 운영자 |
| 5 | 앱 배포(paths 사용, 비어 있으면 번들 데이터) | 프론트 |

---

## 0. 사전 확인

- [ ] 앱이 `GET /map/data` 를 엄격한 스키마(모르는 키 거부)로 검증하지 않는지 — 구버전 앱은 새 키 `paths` 를 무시해야 한다.
- [ ] 프론트가 경로망을 format 1 JSON 으로 내보낼 수 있는지(`docs/admin-api-spec.md` 임포트 절). 가능하면 `entranceRefs[].buildingCode` 까지 채운다.
- [ ] 이미 적용됐는지:

```sql
SHOW TABLES LIKE 'path_%';   -- path_nodes, path_edges 가 있으면 1단계 SQL 은 건너뛰어도 된다(다시 돌려도 안전)
```

## 1. RDS 에 SQL 실행 (배포 전)

1. **RDS 스냅샷**을 만든다.
2. `db/create_path_network_tables.sql` 실행. 마지막 확인 SELECT 가 `path_tables_2 = 2`, `check_constraints_2 = 2` 여야 한다.

## 2. 서버 배포 (Deploy 워크플로)

`db/` 가 바뀐 커밋이라 main 에 머지되면 Deploy 의 guard 가 멈춘다(의도된 동작).

1. dev → main 머지 → `Publish image` 가 `sha-xxxxxxx` 이미지를 올린다.
2. Actions → **Deploy** → Run workflow — `image_tag` = 그 태그, `dry_run` = **해제**, `sql_applied` = **체크**.
3. Environment `production` 승인 → 배포 후 확인(`/reports` 200, `/admin/users` 401)까지 초록이면 끝.

서버 환경변수(.env) 변경은 없다.

## 3. 경로망 임포트

관리자 토큰으로(웹 관리자 로그인 후 받은 accessToken). 실제 경로망 파일은 프론트가 내보낸 JSON 이다.

```bash
# 1) 미리보기 — 아무것도 바꾸지 않는다
curl -s -X POST "$API/admin/map/path-network/import" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @path-network.json | jq '{counts, errors, idConversions, warnings}'
```

- `errors` 가 비어 있어야 한다. `LABEL_NOT_FOUND` 면 `candidates` 를 보고 프론트 데이터나 `buildings.entrances` 를 고친다.
  `BUILDING_NOT_FOUND`·`BUILDING_AMBIGUOUS` 면 `buildingCode` 를 넣어 다시 내보낸다.
- `counts.waypoints` 가 프론트의 점 수(145), `counts.edges` 가 간선 수(175)와 같은지 본다. `entranceNodes` 는 간선이 쓰는 출입구 참조 수다.
- `warnings`(고립된 점·끊긴 덩어리)는 적용을 막지 않는다 — 프론트 데이터의 원래 모양인지 확인한다.

```bash
# 2) 적용 — 오류가 하나라도 있으면 400 으로 거부되고 아무것도 바뀌지 않는다
curl -s -X POST "$API/admin/map/path-network/import?dryRun=false" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @path-network.json | jq '{applied, counts}'
```

임포트는 경로망 전체를 바꾼다(기존 점·간선을 지우고 새로 넣음). 같은 파일을 다시 넣어도 결과(`GET /map/data` version)는 같다.
넣은 원본 JSON 은 다시 넣을 수 있게 보관한다.

## 4. 배포 후 확인

```bash
curl -s "$API/map/data" | jq '.paths | {nodes: (.nodes | length), edges: (.edges | length)}'
curl -s -H "Authorization: Bearer $TOKEN" "$API/admin/map/path-audit" | jq '{summary, broken: (.brokenEntranceRefs | length), isolated: .isolatedNodes, longEdges}'
```

- `paths` 의 점·간선 수가 임포트 `counts` 와 같아야 한다(깨진 출입구 참조가 있으면 그만큼 빠진다).
- `brokenEntranceRefs` 가 0 이어야 한다.

## 이후 운영 — 지도 동기화 SQL 과 함께 쓸 때

`db/sync_map_data_*.sql` 은 `buildings.entrances` 를 통째로 덮어쓴다. 경로가 쓰는 출입구 라벨이 바뀌거나 빠지면 그 점·간선이
`GET /map/data` 에서 조용히 빠진다(서버 로그 WARN). 동기화 SQL 을 돌릴 때는 **COMMIT 전에**
`db/create_path_network_tables.sql` 끝의 확인 쿼리를 실행하고, 결과가 있으면 ROLLBACK 한 뒤 라벨을 되돌리거나 경로를 먼저 고친다.
적용 뒤에는 `GET /admin/map/path-audit` 로 다시 본다. SQL 로 고친 내용은 `GET /map/data` 캐시 TTL(최대 5분)이 지나야 반영된다.

## 되돌리기

- 서버: Deploy 를 이전 `sha-` 태그로 다시 실행하거나 서버에서 `hongikon-be:before-<태그>`(`docs/ci-cd.md` 5.5).
  이전 서버는 `path_*` 테이블을 모르므로 남겨 둬도 동작한다. 앱은 `paths` 가 없으면 번들 데이터로 돌아간다.
- 경로망 데이터만 되돌리기: 이전 원본 JSON 을 다시 임포트한다. 비우려면 `{"format":1}` 을 `dryRun=false` 로 넣는다.
- 테이블까지 지우려면(서버를 이전 버전으로 돌린 뒤): `DROP TABLE path_edges; DROP TABLE path_nodes;`
