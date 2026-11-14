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
| **홈 상태 대시보드 & 빠른 실행** | 상단 요약 카드로 실행 상태를 한눈에 확인하고, 단일·다중 타겟 카드의 실행·설정 버튼과 2×2 빠른 이동 그리드로 원하는 화면에 즉시 접근합니다. |
| **하단 내비게이션 & `+` 빠른 실행 메뉴** | `홈 · 프리셋 · 테스트 · 가이드` 4탭과 중앙 `+` 버튼으로 오버레이 실행·일시정지·프리셋 저장을 어디서든 바로 조작할 수 있습니다. |
| **맞춤 내비게이션 바 & 화면 스타일** | 설정에서 **기본 바**와 **플로팅 글래스 바** 중 취향에 맞게 전환하고, 라이트/다크/시스템 테마 및 커스텀 포인트 색상을 선택할 수 있습니다. |
| **단일 · 다중 타겟 자동 클릭** | 조준경(`1`, `2`, `3`...)을 드래그해 한 지점 또는 여러 지점을 순서대로 클릭하며, `ms` 단위의 정밀한 클릭 간격·지속시간·반복 조건을 설정합니다. |
| **봇 방지 랜덤 터치** | 선택한 원형 영역 안에서 매번 무작위 오프셋으로 터치해 고정 좌표 반복 패턴 감지를 줄입니다. |
| **플로팅 컨트롤 패널** | 다른 앱 위에서도 패널을 드래그해 시작·일시정지, 지점 추가/삭제(`+`/`−`), 실시간 경과 시간·클릭 수 확인 및 설정 편집이 가능합니다. |
| **프리셋 보관함 · 터치 테스트 · 가이드** | 자주 쓰는 구성을 프리셋으로 저장·실행하고, 내장 테스트 보드로 속도를 점검하며, 가이드 탭에서 권한 체크리스트를 단계별로 확인합니다. |
| **JSON 백업 및 인앱 업데이트** | 설정과 프리셋을 JSON 파일 또는 클립보드로 백업·복원하고, 설정 화면에서 새 릴리즈를 바로 내려받아 업데이트합니다. |

## 시작하기

### 1. 설치

1. 상단의 **Download for Android** 버튼 또는 [최신 릴리즈](https://github.com/x2tora/autoclicker/releases/latest)에서 내 기기에 맞는 APK 파일을 내려받아 실행합니다.

| ABI | 다운로드 파일 | 권장 대상 |
| --- | --- | --- |
| `arm64-v8a` | `autoclicker-v1.1.0-arm64-v8a.apk` | 대부분의 최신 64비트 안드로이드 스마트폰 **(추천)** |
| `armeabi-v7a` | `autoclicker-v1.1.0-armeabi-v7a.apk` | 구형 32비트 안드로이드 기기 |
| `x86` | `autoclicker-v1.1.0-x86.apk` | 32비트 x86 태블릿 및 에뮬레이터 |
| `x86_64` | `autoclicker-v1.1.0-x86_64.apk` | 64비트 x86 태블릿 · 크롬북 · 에뮬레이터 |
| `universal` | `autoclicker-v1.1.0-universal.apk` | 기기 호환성을 구분하기 어려울 때 선택하는 통합본 |

2. 설치 중 **"출처를 알 수 없는 앱 설치 허용"** 안내가 표시되면 허용을 눌러 설치를 완료합니다.

### 2. 접근성 권한 허용 및 실행

1. 앱을 실행한 뒤 상단 안내 카드나 **가이드** 탭의 **접근성 설정 열기**를 눌러 **설치된 앱(또는 다운로드한 앱) → 오토 클리커**의 접근성 권한을 켭니다.
   - **삼성 OneUI 및 Android 13+ 기기 안내**: 접근성 스위치가 비활성화되어 켜지지 않는 경우, 앱 내 **앱 정보 (제한 해제)** 버튼을 누르거나 기기 **설정 → 애플리케이션 → 오토 클리커 → 우측 상단 `⋮` 메뉴 → 제한된 설정 허용**을 먼저 선택한 뒤 다시 접근성 권한을 켜주세요.
2. **홈** 화면이나 하단 중앙 **`+` 빠른 실행 메뉴**에서 **단일 타겟 모드** 또는 **다중 타겟 모드** 실행을 누르면 화면에 컨트롤 패널과 조준경 과녁이 나타납니다.
3. 원하는 위치로 과녁을 드래그해 맞춘 뒤 패널의 **▶** 버튼을 눌러 자동 클릭을 시작하세요.

## 데이터 처리 및 보안

- 모든 설정, 타겟 좌표 및 프리셋 데이터는 외부 서버로 전송되지 않고 기기 내부 저장소에만 보관됩니다.
- 접근성 서비스 권한은 사용자가 오버레이 패널에서 직접 시작 버튼을 눌렀을 때 화면 터치를 대신 수행하는 용도로만 사용됩니다.

## 안내

오토 클리커는 독립적인 오픈소스 안드로이드 유틸리티입니다. 사용하는 앱이나 게임의 운영 정책을 확인한 뒤 책임 있게 이용해 주세요.
