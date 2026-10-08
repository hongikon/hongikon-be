package com.hongmap.hongmapbackend.mapdata;

import java.util.Set;

/**
 * 지도 데이터 관리자 입력에서 허용하는 값. 앱(hongikon-fe) 의 PartnerCategory·PartnerAffiliation·PartnerMapIcon·FacilityKind
 * 타입과 같아야 한다 — 앱이 모르는 값이 들어가면 필터·아이콘이 깨진다. 앱에 새 값을 더할 때 여기도 함께 더한다.
 */
public final class MapDataRules {

    private MapDataRules() {
    }

    /** 영문·숫자·-·_ 1~100자. 첫 글자는 영문·숫자. */
    public static final String CODE_REGEX = "^[A-Za-z0-9][A-Za-z0-9_-]{0,99}$";

    /** 요청 본문 id — 비워 두면(빈 문자열) 서버가 만든다. */
    public static final String OPTIONAL_CODE_REGEX = "^$|" + CODE_REGEX;

    /** https:// 로 시작하고 공백이 없는 주소만. */
    public static final String HTTPS_URL_REGEX = "^https://[^\\s/?#]+[^\\s]*$";

    public static final Set<String> PARTNER_CATEGORIES = Set.of("카페", "주점", "음식", "의료/미용", "문화", "기타", "교육");

    public static final Set<String> AFFILIATIONS = Set.of(
            "총학생회", "기숙사", "미술대학", "공과대학", "문과대학", "경영대학", "건축도시대학", "법과대학", "사범대학",
            "경제학부", "캠퍼스자율전공(서울)", "공연예술학부", "디자인·예술경영학부");

    public static final Set<String> PARTNER_MAP_ICONS = Set.of("병원");

    public static final Set<String> FACILITY_KINDS = Set.of(
            "프린터", "증명서 발급", "열람실", "스터디룸", "정수기", "카페", "식당", "편의점", "라운지", "수면실",
            "학생처", "학과사무실", "행사·전시", "흡연구역", "엘리베이터");
}
