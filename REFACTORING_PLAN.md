# PuttingMeter 리팩터링 실행 계획

## 1. 목적

이 문서는 PuttingMeter 프로젝트의 리팩터링을 기능 변경 없이 점진적으로 수행하기 위한 실행 계획입니다.

핵심 목표는 다음과 같습니다.

- 기존 Android 앱과 Arduino 펌웨어의 동작을 보존합니다.
- `MainActivity`에 집중된 책임을 작은 단위로 분리합니다.
- BLE 통신, payload 파싱, 속도 단위 변환, 기록 관리, 설정 상태를 명확히 분리합니다.
- 실제 BLE 장치와 Arduino 센서 동작에 영향을 줄 수 있는 변경은 작게 나누어 검증합니다.
- 깨진 문서, 주석, UI 문자열을 정상 UTF-8 기준으로 복구합니다.

## 2. 현재 구조와 리팩터링 대상

현재 프로젝트는 크게 두 영역으로 나뉩니다.

- `android/`: BLE 장치와 연결하고 퍼팅 속도, 평균 속도, 예상 거리, 기록 UI를 표시하는 Android 앱
- `aduino/`: Arduino Nano 33 BLE에서 IMU 센서를 읽고 BLE characteristic으로 속도 payload를 전송하는 펌웨어

주요 리팩터링 대상은 다음 파일입니다.

- `android/app/src/main/java/com/example/puttingmeter/MainActivity.java`
- `android/app/src/main/java/com/example/puttingmeter/BLEManager.java`
- `android/app/src/main/java/com/example/puttingmeter/RecordAdapter.java`
- `android/app/src/main/java/com/example/puttingmeter/PuttingDistanceCalculator.java`
- `android/app/src/main/res/layout/activity_main.xml`
- `aduino/aduino.ino`

## 3. 주요 문제 요약

### 3.1 `MainActivity`의 책임 과다

`MainActivity`는 현재 화면 초기화, 하단 탭 전환, BLE 콜백 처리, 속도 데이터 처리, 거리 계산 호출, 기록 리스트 관리, 설정 다이얼로그 제어, 권한 요청, 내부 모델 정의를 모두 담당합니다.

이 구조는 기능 추가 시 Activity가 계속 커지고, UI 변경과 도메인 로직 변경이 서로 영향을 주기 쉬운 형태입니다.

### 3.2 속도 단위 변환 중복

속도 단위 변환이 `MainActivity`와 `RecordAdapter`에 각각 존재합니다.

단위나 표시 형식이 바뀌면 여러 파일을 동시에 수정해야 하므로, `SpeedUnit` 또는 `SpeedFormatter`로 단일화하는 것이 좋습니다.

### 3.3 `RecordAdapter`와 `MainActivity`의 강한 결합

`RecordAdapter`가 `MainActivity.PuttingRecord`에 직접 의존합니다.

기록 모델은 Activity의 내부 구현이 아니라 독립적인 도메인 모델이어야 합니다.

### 3.4 `BLEManager`의 책임 혼재

`BLEManager`는 BLE 권한 확인, 스캔, GATT 연결, characteristic 구독, reset/reboot 명령 전송, payload 파싱, 이벤트 전달을 모두 담당합니다.

특히 `"peak|avg"` 문자열 파싱이 BLE 콜백 내부에 있어 통신 계층과 도메인 데이터 해석이 섞여 있습니다.

### 3.5 미사용 화면 구조 공존

실제 화면은 `activity_main.xml` 안의 `layout_home`, `layout_history`, `layout_settings`를 직접 `VISIBLE/GONE`으로 전환합니다.

하지만 `mobile_navigation.xml`, `HomeFragment`, `DashboardFragment`, `NotificationsFragment`, 각 ViewModel도 남아 있습니다. 현재 구조에서는 사용되지 않는 Navigation Fragment 구조가 유지보수 혼선을 만듭니다.

### 3.6 Arduino 센서 처리 함수의 과도한 책임

`aduino.ino`의 `processSensorData()`는 IMU 읽기, 필터링, 속도 계산, 퍼팅 시작/종료 판정, peak/average 누적, BLE 전송 조건 판단을 한 번에 처리합니다.

센서 알고리즘은 실제 하드웨어 동작에 민감하므로, 함수 추출과 상태 구조화는 반드시 작은 단계로 진행해야 합니다.

### 3.7 인코딩 문제

README, Java/Arduino 주석, XML 문자열 일부가 깨져 있습니다.

빌드에는 직접 영향을 주지 않을 수 있지만, 유지보수성과 UI 문구 품질에는 영향을 줍니다.

## 4. 권장 전략

현재 프로젝트에는 대규모 구조 전환보다 점진적 최소 변경 전략을 권장합니다.

권장하지 않는 즉시 작업:

- Kotlin 또는 Jetpack Compose 전환
- 전체 MVVM 재설계
- BLE 라이브러리 교체
- Arduino 알고리즘 파라미터 변경
- Room 등 영속 저장소 도입

이 작업들은 기능 요구사항이 명확해진 뒤 별도 설계로 진행하는 것이 안전합니다.

## 5. 실행 순서 개요

권장 실행 순서는 다음과 같습니다.

1. 문서와 소스 인코딩 상태 확인 및 기준 정리
2. `PuttingRecord` 모델 분리
3. `SpeedUnit`/`SpeedFormatter` 추가
4. `SpeedPayload`/`SpeedPayloadParser` 추가
5. `PuttingSession` 분리
6. 미사용 Fragment/Navigation 구조 제거
7. 설정 상태와 설정 다이얼로그 로직 축소
8. Arduino `processSensorData()` 함수 추출
9. Arduino 상태 구조체 분리
10. README, 주석, UI 문자열 정리

## 6. 단계별 상세 계획

### Phase 0. 인코딩 기준 정리

목표:

- 리팩터링 문서와 소스 파일을 정상적으로 읽고 리뷰할 수 있는 상태로 만듭니다.
- 한글 주석과 UI 문자열이 깨진 상태로 추가 확산되는 것을 막습니다.

작업:

- `REFACTORING_PLAN.md`를 정상 UTF-8 문서로 유지합니다.
- 기존 README, Java, XML, Arduino 파일의 깨진 문자열 범위를 확인합니다.
- 이 단계에서는 소스 의미를 추측해 대량 수정하지 않습니다.

검증:

- Markdown 파일이 에디터와 터미널에서 정상적으로 읽히는지 확인합니다.
- 이후 코드 변경 시 새로 추가하는 문구는 UTF-8 기준으로 작성합니다.

위험도:

- 낮음

### Phase 1. `PuttingRecord` 모델 분리

목표:

- `RecordAdapter`가 `MainActivity` 내부 클래스에 의존하지 않도록 합니다.
- 도메인 모델을 독립 파일로 분리합니다.

작업:

- `MainActivity.PuttingRecord`를 `model/PuttingRecord.java`로 이동합니다.
- 첫 단계에서는 필드 의미를 바꾸지 않습니다.
- 현재의 `String time`, `float peakSpeed`, `float avgSpeed`, `float distance` 구조를 유지합니다.
- `MainActivity`와 `RecordAdapter`의 import와 타입 참조만 변경합니다.

예상 파일:

- `android/app/src/main/java/com/example/puttingmeter/model/PuttingRecord.java`
- `android/app/src/main/java/com/example/puttingmeter/MainActivity.java`
- `android/app/src/main/java/com/example/puttingmeter/RecordAdapter.java`

검증:

- `.\gradlew.bat :app:compileDebugJavaWithJavac`
- 기록 추가와 기록 목록 표시 수동 확인

위험도:

- 낮음

주의:

- 이 단계에서 `timestamp` 타입 변경은 하지 않습니다. 시간 표현 개선은 별도 후속 작업으로 분리합니다.

### Phase 2. 속도 단위 변환 분리

목표:

- `MainActivity`와 `RecordAdapter`에 중복된 단위 변환 로직을 제거합니다.
- 숫자 변환과 문자열 포맷 책임을 명확히 구분합니다.

권장 설계:

- `SpeedUnit` enum은 단위 이름과 변환 배율을 가집니다.
- `SpeedFormatter`는 표시 문자열 생성을 담당합니다.
- 숫자 변환 API와 문자열 포맷 API를 분리합니다.

예시 API:

```java
public enum SpeedUnit {
    MM_PER_SEC("mm/s", 1.0),
    CM_PER_SEC("cm/s", 0.1),
    M_PER_SEC("m/s", 0.001);

    public double convertFromMmPerSec(double value);
    public String getDisplayName();
}
```

```java
public final class SpeedFormatter {
    public static String formatValue(double mmPerSec, SpeedUnit unit);
    public static String formatWithUnit(double mmPerSec, SpeedUnit unit);
}
```

작업:

- `format/SpeedUnit.java`를 추가합니다.
- `format/SpeedFormatter.java`를 추가합니다.
- `MainActivity`의 display speed 계산을 공통 API로 교체합니다.
- `RecordAdapter`의 display speed 계산을 공통 API로 교체합니다.

예상 파일:

- `android/app/src/main/java/com/example/puttingmeter/format/SpeedUnit.java`
- `android/app/src/main/java/com/example/puttingmeter/format/SpeedFormatter.java`
- `android/app/src/main/java/com/example/puttingmeter/MainActivity.java`
- `android/app/src/main/java/com/example/puttingmeter/RecordAdapter.java`

검증:

- `mm/s`, `cm/s`, `m/s` 변환 결과 확인
- 현재값 표시와 기록값 표시가 기존과 동일한지 확인
- `.\gradlew.bat :app:compileDebugJavaWithJavac`

테스트 권장:

- `1000 mm/s -> 100.0 cm/s`
- `1000 mm/s -> 1.0 m/s`
- `1000 mm/s -> 1000.0 mm/s`

위험도:

- 낮음

### Phase 3. BLE payload 파싱 분리

목표:

- BLE 통신 코드와 payload 해석 로직을 분리합니다.
- Arduino 전송 포맷 변경에 대비합니다.

권장 설계:

- `SpeedPayload`는 peak speed와 average speed를 보관합니다.
- `SpeedPayloadParser`는 raw 문자열을 `SpeedPayload`로 변환합니다.
- 파싱 실패 이유를 테스트 가능하게 남깁니다.

반환 타입 선택:

- 단순 구현: `@Nullable SpeedPayload parse(String raw)`
- 권장 구현: `SpeedPayloadParseResult parse(String raw)`

현재 프로젝트에는 `SpeedPayloadParseResult`를 권장합니다. 실패 원인을 로그에만 남기면 테스트가 약해지기 때문입니다.

작업:

- `ble/SpeedPayload.java` 추가
- `ble/SpeedPayloadParseResult.java` 추가
- `ble/SpeedPayloadParser.java` 추가
- `BLEManager.onCharacteristicChanged()`에서 직접 파싱하던 코드를 parser 호출로 교체
- `"100|80"`은 peak 100, average 80으로 처리
- `"100"`은 peak 100, average 100으로 처리
- null, empty, 숫자 오류, 구분자 오류를 실패 결과로 처리

예상 파일:

- `android/app/src/main/java/com/example/puttingmeter/ble/SpeedPayload.java`
- `android/app/src/main/java/com/example/puttingmeter/ble/SpeedPayloadParseResult.java`
- `android/app/src/main/java/com/example/puttingmeter/ble/SpeedPayloadParser.java`
- `android/app/src/main/java/com/example/puttingmeter/BLEManager.java`

검증:

- `"100|80"` 파싱
- `"100"` 파싱
- `""`, `"abc"`, `"100|"`, `"100|abc"` 실패 처리
- `.\gradlew.bat :app:compileDebugJavaWithJavac`

주의:

- 현재 Arduino 코드는 `ipeak`와 `iavg`를 BLE로 보내며, Android는 이를 `mm/s`로 취급합니다. 단위 해석은 변경하지 않습니다.

위험도:

- 낮음에서 중간

### Phase 4. 세션 상태 분리

목표:

- 기록 리스트 관리와 최고 거리 갱신 로직을 `MainActivity`에서 분리합니다.
- UI와 세션 상태를 분리합니다.

작업:

- `session/PuttingSession.java`를 추가합니다.
- `addRecord(PuttingRecord record)`에서 최대 10개 유지 로직을 담당합니다.
- `isNewBestDistance(float distance)` 또는 `updateBestDistance(float distance)`를 제공합니다.
- `clear()`로 reset 시 기록과 최고 거리 상태를 초기화합니다.
- `MainActivity`는 세션 객체를 호출하고 UI 갱신만 담당합니다.

예상 파일:

- `android/app/src/main/java/com/example/puttingmeter/session/PuttingSession.java`
- `android/app/src/main/java/com/example/puttingmeter/MainActivity.java`

검증:

- 기록이 최신순으로 추가되는지 확인
- 기록이 10개를 초과하면 마지막 항목이 제거되는지 확인
- reset 시 기록과 최고 거리 상태가 초기화되는지 확인
- `.\gradlew.bat :app:compileDebugJavaWithJavac`

테스트 권장:

- 11개 기록 추가 시 size가 10인지 확인
- 새 거리 값이 최고 거리일 때만 true를 반환하는지 확인

위험도:

- 중간

### Phase 5. 미사용 Fragment/Navigation 구조 제거

목표:

- 실제 사용되지 않는 화면 구조를 제거해 프로젝트 방향성을 명확히 합니다.

사전 확인:

```powershell
rg -n "HomeFragment|DashboardFragment|NotificationsFragment|mobile_navigation|fragment_home|fragment_dashboard|fragment_notifications" android/app/src/main
```

작업:

- 사용되지 않는 `ui/home`, `ui/dashboard`, `ui/notifications` 패키지를 제거합니다.
- 사용되지 않는 `res/navigation/mobile_navigation.xml`을 제거합니다.
- 사용되지 않는 `fragment_home.xml`, `fragment_dashboard.xml`, `fragment_notifications.xml` 제거 여부를 확인합니다.
- `strings.xml`의 미사용 title 문자열 제거 여부를 확인합니다.

예상 파일:

- `android/app/src/main/java/com/example/puttingmeter/ui/...`
- `android/app/src/main/res/navigation/mobile_navigation.xml`
- `android/app/src/main/res/layout/fragment_*.xml`
- `android/app/src/main/res/values/strings.xml`

검증:

- `.\gradlew.bat :app:processDebugResources`
- `.\gradlew.bat :app:assembleDebug`
- 하단 탭 전환 수동 확인

위험도:

- 낮음에서 중간

주의:

- 삭제 전 `rg`로 간접 참조를 반드시 확인합니다.

### Phase 6. 설정 상태와 설정 다이얼로그 축소

목표:

- 설정 관련 상태와 UI 다이얼로그 로직을 단계적으로 분리합니다.
- 한 번에 `SettingsDialogController`로 크게 옮기지 않습니다.

권장 하위 단계:

#### Phase 6A. 설정 값 모델 분리

작업:

- `settings/PuttingSettings.java` 추가
- `correctionStep`, `speedUnit` 같은 설정 값을 한 객체로 묶습니다.

위험도:

- 낮음

#### Phase 6B. 설정 적용 로직 분리

작업:

- 단위 변경 시 UI와 adapter에 적용하는 로직을 작은 메서드로 정리합니다.
- correction step 변경 시 텍스트와 색상을 갱신하는 로직을 명확히 분리합니다.

위험도:

- 낮음에서 중간

#### Phase 6C. 다이얼로그 controller 분리 검토

작업:

- `SettingsDialogController`는 필요성이 명확할 때만 추가합니다.
- Activity, Dialog, View 참조가 복잡해지는 경우에는 무리하게 분리하지 않습니다.

위험도:

- 중간

검증:

- 설정 다이얼로그 열기/닫기
- correction step 증감
- 단위 변경
- 마지막 평균 속도 기준 거리 재계산
- `.\gradlew.bat :app:assembleDebug`

### Phase 7. Arduino 센서 처리 함수 추출

목표:

- `processSensorData()`의 길이와 책임을 줄입니다.
- 알고리즘 동작은 바꾸지 않습니다.

중요 원칙:

- 임계값과 계산식은 변경하지 않습니다.
- 첫 단계에서는 전역 상태를 유지합니다.
- 함수 추출만 수행하고, 동작 변경은 하지 않습니다.

권장 하위 단계:

#### Phase 7A. 함수 추출만 수행

작업:

- IMU 읽기 가능 여부 확인과 raw 값 읽기를 별도 함수로 분리합니다.
- 속도 계산을 별도 함수로 분리합니다.
- 퍼팅 시작 처리, 진행 중 처리, 종료 처리 함수를 분리합니다.
- BLE 전송 함수만 별도로 추출합니다.

검증:

- Arduino 컴파일
- 기존 serial log와 before/after 비교
- 정상 퍼팅 시 `peak|avg` 전송 확인

위험도:

- 중간

#### Phase 7B. `StrokeState` 구조체 도입

작업:

- `isMoving`, `peakSpeed`, `speedSum`, `speedCount`, `dataCountSincePeakUpdate`, `totalDataCount`, `peakSent`, `inputEnabled`를 구조체로 묶습니다.
- 기존 값 초기화 로직을 구조체 초기화 함수로 통합합니다.

검증:

- RESET 처리 후 상태 초기화 확인
- 연결 해제 후 상태 초기화 확인
- 정상 측정 흐름 확인

위험도:

- 중간에서 높음

#### Phase 7C. 별도 헤더 분리 검토

작업:

- `SpeedBuffer.h` 또는 `StrokeDetector.h` 분리는 필요할 때만 진행합니다.
- Arduino IDE/CLI 빌드 구조가 안정적으로 확인된 뒤 진행합니다.

위험도:

- 높음

### Phase 8. 문서, 주석, UI 문자열 정리

목표:

- 깨진 README, 주석, UI 문자열을 정상적인 문구로 복구합니다.
- Android UI 문자열은 가능하면 `strings.xml`로 이동합니다.

작업:

- `README.md` 재작성
- `aduino/README.md` 재작성
- 깨진 Java/Arduino 주석 정리
- `activity_main.xml`의 버튼/라벨 문자열 정리
- 필요한 문자열을 `strings.xml`로 이동

예상 파일:

- `README.md`
- `aduino/README.md`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/layout/activity_main.xml`
- 주요 Java/Arduino 소스

검증:

- `.\gradlew.bat :app:processDebugResources`
- 앱 화면 문구 육안 확인
- README 렌더링 확인

위험도:

- 낮음

주의:

- 깨진 원문을 정확히 복원할 수 없으면 현재 기능 기준으로 새 문구를 작성합니다.

## 7. 테스트와 검증 계획

### Android 빌드 검증

Java 컴파일:

```powershell
cd D:\PuttingMeter\android
.\gradlew.bat :app:compileDebugJavaWithJavac
```

리소스 검증:

```powershell
cd D:\PuttingMeter\android
.\gradlew.bat :app:processDebugResources
```

전체 디버그 빌드:

```powershell
cd D:\PuttingMeter\android
.\gradlew.bat :app:assembleDebug
```

### 단위 테스트 권장 대상

실제 BLE 장치 없이 검증 가능한 대상입니다.

- `SpeedUnit`
- `SpeedFormatter`
- `SpeedPayloadParser`
- `PuttingSession`

권장 테스트 케이스:

- `SpeedUnit`이 `mm/s`, `cm/s`, `m/s`를 정확히 변환하는지 확인
- `SpeedPayloadParser`가 `"100|80"`, `"100"`, 잘못된 문자열을 정확히 처리하는지 확인
- `PuttingSession`이 기록을 최대 10개로 유지하는지 확인
- `PuttingSession`이 최고 거리 갱신 여부를 정확히 반환하는지 확인

### 수동 검증 대상

- 앱 시작 후 BLE 스캔 시작 여부
- BLE 연결/해제 상태 표시
- peak speed, average speed, distance 표시
- 단위 변경 시 현재값과 기록값 표시
- reset 명령
- reboot 명령
- 하단 탭 전환
- 설정 다이얼로그 동작

### Arduino 검증

가능한 경우 다음을 확인합니다.

- Arduino 컴파일
- 정지 상태에서 잘못된 측정값 미전송
- 정상 퍼팅 동작에서 `peak|avg` payload 전송
- RESET 명령 처리
- REBOOT 명령 처리
- 리팩터링 전후 serial log 비교

## 8. 완료 기준

최소 완료 기준:

- `PuttingRecord`가 독립 모델로 분리됨
- 속도 단위 변환 중복이 제거됨
- BLE payload 파싱이 `BLEManager` 밖으로 분리됨
- Android Java 컴파일이 통과함

권장 완료 기준:

- `PuttingSession`으로 기록과 최고 거리 상태가 분리됨
- 미사용 Fragment/Navigation 구조가 제거됨
- 설정 상태가 명확한 모델로 묶임
- Arduino 센서 처리 함수가 역할별로 나뉨
- README, 주석, UI 문자열의 깨진 문자가 정리됨
- Android 전체 디버그 빌드가 통과함

## 9. 작업 시 주의사항

- 각 Phase는 별도 커밋 단위로 진행하는 것이 좋습니다.
- BLE와 Arduino 관련 변경은 실제 장치 테스트 전까지 동작 확정을 단정하지 않습니다.
- Arduino 알고리즘의 임계값, 계산식, 전송 조건은 리팩터링 중 변경하지 않습니다.
- 대량 삭제 전에는 `rg`로 참조를 확인합니다.
- UI 문자열 정리는 기능 리팩터링과 분리해서 진행합니다.
- 코드 구조 개선과 문구 복구를 같은 커밋에 섞지 않는 것이 좋습니다.
