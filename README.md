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
| **시작 화면 바로 실행** | 앱을 열자마자 하단 메뉴 이동 없이 시작 화면에서 단일·다중 타겟 모드를 원탭으로 바로 실행합니다. |
| **단일 타겟 모드** | 화면의 조준경(`1`)을 원하는 위치로 드래그해 한 지점을 지정된 속도로 정확하게 반복 클릭합니다. |
| **다중 타겟 모드** | 여러 지점(`1`, `2`, `3`...)을 배치해 순서대로 클릭하며, 지점별 간격·지속시간·랜덤 반경을 개별 수정할 수 있습니다. |
| **세부 타이밍 & 반복 제어** | 클릭 간격과 지속시간을 `ms`·`s`·`m`·`h` 단위로 설정하고, 무한 반복·일정 시간 지속·횟수 지정 중 선택합니다. |
| **봇 방지 랜덤 터치** | 선택한 원형 영역 안에서 매번 조금씩 다른 무작위 좌표를 터치해 반복 패턴 감지를 줄입니다. |
| **플로팅 컨트롤 패널** | 다른 앱 위에서도 패널을 드래그해 시작·일시정지, 지점 추가/삭제(`+`/`−`), 실시간 경과·클릭 수 확인 및 설정 변경이 가능합니다. |
| **프리셋 & 터치 테스트** | 자주 쓰는 구성을 프리셋으로 저장해 불러오거나, 내장 터치 테스트 보드에서 실제 클릭 속도와 분산을 시험할 수 있습니다. |
| **JSON 백업 및 인앱 업데이트** | 모든 설정과 프리셋을 JSON 파일 또는 클립보드로 백업·복원하고, 설정 화면에서 새 버전을 바로 내려받아 업데이트합니다. |

## 시작하기

### 1. 설치

1. 상단의 **Download for Android** 버튼 또는 [최신 릴리즈](https://github.com/x2tora/autoclicker/releases/latest)에서 내 기기에 맞는 APK 파일을 내려받아 실행합니다.
   - **`autoclicker-v1.0.2-arm64-v8a.apk`** *(대부분의 최신 안드로이드 스마트폰 — 추천)*
   - `autoclicker-v1.0.2-armeabi-v7a.apk` *(구형 32비트 기기)*
   - `autoclicker-v1.0.2-x86.apk` / `autoclicker-v1.0.2-x86_64.apk` *(일부 태블릿·크롬북·에뮬레이터)*
   - `autoclicker-v1.0.2-universal.apk` *(모든 기기 호환 통합본)*
2. 설치 중 **"출처를 알 수 없는 앱 설치 허용"** 안내가 표시되면 허용을 눌러 설치를 완료합니다.

### 2. 접근성 권한 허용 및 실행

1. 앱을 처음 실행한 뒤 **접근성 설정 열기**를 눌러 **설치된 앱(또는 다운로드한 앱) → 오토 클리커**의 접근성 권한을 켭니다.
2. 시작 화면 상단의 **단일 타겟** 또는 **다중 타겟** 카드에서 **바로 실행**을 누르면 화면에 컨트롤 패널과 조준경 과녁이 나타납니다.
3. 원하는 위치로 과녁을 드래그해 맞춘 뒤 패널의 **▶** 버튼을 눌러 자동 클릭을 시작하세요.

## 데이터 처리 및 보안

- 모든 설정, 타겟 좌표 및 프리셋 데이터는 외부 서버로 전송되지 않고 기기 내부 저장소에만 보관됩니다.
- 접근성 서비스 권한은 사용자가 오버레이 패널에서 직접 시작 버튼을 눌렀을 때 화면 터치를 대신 수행하는 용도로만 사용됩니다.

## 안내

오토 클리커는 독립적인 오픈소스 안드로이드 유틸리티입니다. 사용하는 앱이나 게임의 운영 정책을 확인한 뒤 책임 있게 이용해 주세요.
