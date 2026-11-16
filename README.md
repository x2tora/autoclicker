<div align="center">
  <img src="assets/icon.png" alt="오토 클리커" width="88" height="88" />

  <h1>오토 클리커</h1>
  <p>단일·다중 지점 자동 터치와 프리셋 백업을 지원하는 안드로이드 전용 오토 클리커</p>

  <p>
    <a href="https://github.com/x2tora/autoclicker/releases/latest">
      <img src="https://img.shields.io/badge/Download_for_Android-Latest_Release-3b82f6?style=for-the-badge&logo=android&logoColor=white" alt="최신 버전 다운로드" />
    </a>
  </p>

  <p>
    <a href="https://github.com/x2tora/autoclicker/releases/latest">
      <img src="https://img.shields.io/github/v/release/x2tora/autoclicker?style=flat-square&label=Version&color=3b82f6" alt="Latest Version" />
    </a>
    <a href="https://github.com/x2tora/autoclicker/releases">
      <img src="https://img.shields.io/github/downloads/x2tora/autoclicker/total?style=flat-square&label=Downloads&color=64748b" alt="Total Downloads" />
    </a>
  </p>

  <p>
    <a href="https://github.com/x2tora/autoclicker/releases">모든 릴리즈</a>
    &nbsp;·&nbsp;
    <a href="https://github.com/x2tora/autoclicker/issues">이슈 제보</a>
  </p>
</div>

---

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| **시나리오 · 조건부 매크로 (v2.0 신규)** | 한 지점을 반복 터치하다가 화면의 **특정 텍스트**(화면 영역 직접 선택 지원)나 **업로드한 이미지**가 감지되면 반복을 멈추고, 지정된 위치 클릭 → 스크롤 → 클릭 순서를 자동으로 이어 실행·반복합니다. |
| **단계별 자유 편집 & 기본 8단계 템플릿** | `반복·감지 → 3회 클릭 → 스크롤 내리기 → 3회 클릭` 기본 8단계 템플릿을 제공하며, 단계 추가·삭제·순서 이동과 스크롤 방향·후대기(`ms`) 즉시 저장을 지원합니다. |
| **홈 화면 무(無)시프트 & 좌우 원탭 카드** | 단일·다중 모드를 좌우 대형 사각형 카드로 나란히 배치하고 상단 상태 바 높이를 고정해, 화면 밀림 없이 한 번의 탭으로 즉시 실행·제어합니다. |
| **단일 · 다중 타겟 자동 클릭** | 조준경(`1`, `2`, `3`...)을 드래그해 한 지점 또는 여러 지점을 순서대로 클릭하며, `ms` 단위 간격·지속시간과 **봇 방지 랜덤 터치**를 설정합니다. |
| **최근 프리셋 바로 실행 & 개별 내보내기·가져오기** | 단일·다중·시나리오 프리셋을 저장·검색·모드별 필터링하고, 전체 백업과 별개로 **원하는 프리셋 1개만 JSON 파일이나 클립보드로 내보내기·가져오기**할 수 있습니다. |
| **맞춤 내비게이션 바 · 가이드 · 터치 테스트** | 설정에서 기본 바 ↔ 플로팅 글래스 바를 전환하고, 내장 테스트 보드 및 단계별 권한 가이드(OneUI 제한 해제 포함)를 제공합니다. |
| **JSON 백업 및 인앱 업데이트** | 모든 설정, 시나리오 구성, 프리셋을 JSON 파일 또는 클립보드로 백업·복원하고, 설정 화면에서 새 릴리즈를 바로 내려받아 업데이트합니다. |

## 시작하기

### 1. 설치

1. 상단의 **Download for Android** 버튼 또는 [최신 릴리즈](https://github.com/x2tora/autoclicker/releases/latest)에서 내 기기에 맞는 APK 파일을 내려받아 실행합니다.

| ABI | 다운로드 파일 | 권장 대상 |
| --- | --- | --- |
| `arm64-v8a` | `autoclicker-v2.2.4-arm64-v8a.apk` | 대부분의 최신 64비트 안드로이드 스마트폰 **(추천)** |
| `armeabi-v7a` | `autoclicker-v2.2.4-armeabi-v7a.apk` | 구형 32비트 안드로이드 기기 |
| `x86` | `autoclicker-v2.2.4-x86.apk` | 32비트 x86 태블릿 및 에뮬레이터 |
| `x86_64` | `autoclicker-v2.2.4-x86_64.apk` | 64비트 x86 태블릿 · 크롬북 · 에뮬레이터 |
| `universal` | `autoclicker-v2.2.4-universal.apk` | 기기 호환성을 구분하기 어려울 때 선택하는 통합본 |

2. 설치 중 **"출처를 알 수 없는 앱 설치 허용"** 안내가 표시되면 허용을 눌러 설치를 완료합니다.

### 2. 접근성 권한 허용 및 실행

1. 앱을 실행한 뒤 상단 안내 카드나 **가이드** 탭의 **접근성 설정 열기**를 눌러 **설치된 앱(또는 다운로드한 앱) → 오토 클리커**의 접근성 권한을 켭니다.
   - **삼성 OneUI 및 Android 13+ 기기 안내**: 접근성 스위치가 비활성화되어 켜지지 않는 경우, 앱 내 **앱 정보 (제한 해제)** 버튼을 누르거나 기기 **설정 → 애플리케이션 → 오토 클리커 → 우측 상단 `⋮` 메뉴 → 제한된 설정 허용**을 먼저 선택한 뒤 다시 접근성 권한을 켜주세요.
2. **홈** 화면이나 하단 중앙 **`+` 빠른 실행 메뉴**에서 **단일 타겟 · 다중 타겟 · 시나리오 매크로** 중 원하는 모드를 실행하면 화면에 컨트롤 패널과 조준경 과녁이 나타납니다.
3. 과녁을 드래그해 위치를 맞추거나 시나리오 `⚙` 설정에서 감지할 텍스트·이미지와 스크롤 거리를 지정한 뒤 패널의 **▶** 버튼을 눌러 시작하세요.

## 데이터 처리 및 보안

- 모든 설정, 타겟 좌표 및 프리셋 데이터는 외부 서버로 전송되지 않고 기기 내부 저장소에만 보관됩니다.
- 접근성 서비스 권한은 사용자가 오버레이 패널에서 직접 시작 버튼을 눌렀을 때 화면 터치를 대신 수행하는 용도로만 사용됩니다.

## 안내

오토 클리커는 독립적인 오픈소스 안드로이드 유틸리티입니다. 사용하는 앱이나 게임의 운영 정책을 확인한 뒤 책임 있게 이용해 주세요.
