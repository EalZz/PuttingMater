#include <Arduino_LSM6DS3.h>
#include <ArduinoBLE.h>

// BLE 서비스 설정
BLEService puttingService("12345678-1234-1234-1234-123456789abc");
BLECharacteristic speedCharacteristic("87654321-4321-4321-4321-cba987654321", BLENotify, 20);
BLECharacteristic statusCharacteristic("11111111-2222-3333-4444-555555555555", BLENotify, 20);
BLECharacteristic resetCharacteristic("22222222-3333-4444-5555-666666666666", BLEWrite, 20);

// 사용자 설정값
const float RADIUS_M = 1.0;
const float SPEED_THRESHOLD = 0.05;
const float ACCEL_THRESHOLD = 0.025;
const float GYRO_FILTER_ALPHA = 0.02;

// 스파이크 보정 설정
const int BUFFER_SIZE = 5;
const float SPIKE_THRESHOLD = 2.0;
const float SPIKE_CONFIRM_RATIO = 0.8;

// 조기 종료 설정
const int PEAK_UPDATE_THRESHOLD = 9;

// 내부 변수
float filtered_gyro_z = 0.0;
bool isMoving = false;
float peakSpeed = 0.0;

// 평균 속도 관련 변수
float speedSum = 0.0;
int speedCount = 0;

// 피크 갱신 추적 변수
int dataCountSincePeakUpdate = 0;
int totalDataCount = 0;

// BLE 상태
bool deviceConnected = false;
bool peakSent = false;

// 입력 가능 상태
bool inputEnabled = true;

// 스파이크 보정 버퍼 구조체
struct SpeedBuffer {
  float values[BUFFER_SIZE];
  int index;
  int count;
  float pendingValue;
  bool hasPending;
  int pendingConfirmCount;
  int totalPendingChecks;

  SpeedBuffer() {
    index = 0;
    count = 0;
    hasPending = false;
    pendingValue = 0.0;
    pendingConfirmCount = 0;
    totalPendingChecks = 0;
    for (int i = 0; i < BUFFER_SIZE; i++) values[i] = 0.0;
  }

  void reset() {
    index = 0;
    count = 0;
    hasPending = false;
    pendingValue = 0.0;
    pendingConfirmCount = 0;
    totalPendingChecks = 0;
    for (int i = 0; i < BUFFER_SIZE; i++) values[i] = 0.0;
  }

  float getAverage() {
    if (count == 0) return 0.0;
    float sum = 0.0;
    for (int i = 0; i < count; i++) sum += values[i];
    return sum / count;
  }

  bool isSpikeValue(float newValue) {
    if (count < 2) return false;
    float avg = getAverage();
    return (newValue > avg * SPIKE_THRESHOLD && avg > 0.01);
  }

  bool isSimilarToPending(float newValue) {
    if (!hasPending) return false;
    float diff = fabs(newValue - pendingValue);
    float threshold = pendingValue * 0.3;
    return (diff <= threshold);
  }

  float processValue(float newValue) {
    if (hasPending) {
      totalPendingChecks++;
      if (isSimilarToPending(newValue)) pendingConfirmCount++;

      if (totalPendingChecks >= 3) {
        float confirmRatio = (float)pendingConfirmCount / totalPendingChecks;
        if (confirmRatio >= SPIKE_CONFIRM_RATIO) {
          addToBuffer(pendingValue);
          addToBuffer(newValue);
          hasPending = false;
          return newValue;
        } else {
          hasPending = false;
          return processValue(newValue);
        }
      } else {
        return getAverage();
      }
    }

    if (isSpikeValue(newValue)) {
      pendingValue = newValue;
      hasPending = true;
      pendingConfirmCount = 0;
      totalPendingChecks = 0;
      return getAverage();
    } else {
      addToBuffer(newValue);
      return getAverage();
    }
  }

private:
  void addToBuffer(float value) {
    values[index] = value;
    index = (index + 1) % BUFFER_SIZE;
    if (count < BUFFER_SIZE) count++;
  }
};

SpeedBuffer speedBuffer;

// setup 함수
void setup() {
  Serial.begin(115200);

  // IMU 초기화
  if (!IMU.begin()) {
    Serial.println("IMU 초기화 실패");
    while (1);
  }

  // BLE 초기화
  if (!BLE.begin()) {
    Serial.println("BLE 시작 실패");
    while (1);
  }

  BLE.setLocalName("Putting Speed Meter");
  BLE.setAdvertisedService(puttingService);
  puttingService.addCharacteristic(speedCharacteristic);
  puttingService.addCharacteristic(statusCharacteristic);
  puttingService.addCharacteristic(resetCharacteristic);
  BLE.addService(puttingService);

  speedCharacteristic.writeValue("0|0");
  statusCharacteristic.writeValue("Ready");

  BLE.advertise();
  Serial.println("BLE 준비 완료 연결 대기중");
}

// loop 함수
void loop() {
  BLEDevice central = BLE.central();

  if (central) {
    if (!deviceConnected) {
      deviceConnected = true;
      Serial.print("중앙 장치 연결됨 ");
      Serial.println(central.address());
      statusCharacteristic.writeValue("Ready");
    }
    if (resetCharacteristic.written()) {
      String cmd = resetCharacteristic.value();
      if (cmd == "RESET") {
        Serial.println("앱에서 RESET 명령 수신!");
        resetAll();
      }
    }

    processSensorData();
  } 
  else {
    if (deviceConnected) {
      deviceConnected = false;
      Serial.println("중앙 장치 연결 해제");
      resetAll();
    }
  }
}

// 모든 상태 초기화
void resetAll() {
  isMoving = false;
  peakSpeed = 0.0;
  peakSent = false;
  speedSum = 0.0;
  speedCount = 0;
  dataCountSincePeakUpdate = 0;
  totalDataCount = 0;
  speedBuffer.reset();
  speedCharacteristic.writeValue("0|0");
  statusCharacteristic.writeValue("Ready");
  inputEnabled = true;
}

// 센서 데이터 처리
void processSensorData() {
  float gx, gy, gz;
  float ax, ay, az;

  if (IMU.gyroscopeAvailable() && IMU.accelerationAvailable()) {
    IMU.readGyroscope(gx, gy, gz);
    IMU.readAcceleration(ax, ay, az);

    float acc_mag = sqrt(ax * ax + ay * ay + az * az);

    // 정지 상태 무시
    if (fabs(acc_mag - 1.0) < ACCEL_THRESHOLD) return;

    float gz_rad = gz * PI / 180.0;
    filtered_gyro_z = (1.0 - GYRO_FILTER_ALPHA) * gz_rad + GYRO_FILTER_ALPHA * filtered_gyro_z;
    float raw_linear_speed = filtered_gyro_z * RADIUS_M;

    float corrected_speed = speedBuffer.processValue(raw_linear_speed);

    // 백스윙 감지 입력 가능 상태 활성화
    if (!isMoving && !inputEnabled && corrected_speed < -SPEED_THRESHOLD) {
      inputEnabled = true;
      peakSent = false;  // 새로운 스윙 준비
      Serial.println("백스윙 감지 입력 가능 상태");
    }

    // 스윙 시작 조건
    if (inputEnabled && !isMoving && corrected_speed >= SPEED_THRESHOLD) {
      isMoving = true;
      inputEnabled = false;
      peakSpeed = corrected_speed;
      peakSent = false;
      speedSum = 0.0;
      speedCount = 0;
      dataCountSincePeakUpdate = 0;
      totalDataCount = 0;

      Serial.print("스윙 시작 속도 ");
      Serial.println(corrected_speed);

      if (deviceConnected) statusCharacteristic.writeValue("Moving");
    }

    // 스윙 진행 중 처리
    if (isMoving) {
      totalDataCount++;
      if (corrected_speed > peakSpeed) {
        peakSpeed = corrected_speed;
        dataCountSincePeakUpdate = 0;
        Serial.print("새 피크 속도 ");
        Serial.print(peakSpeed);
        Serial.print(" 총 데이터 ");
        Serial.println(totalDataCount);
      } else dataCountSincePeakUpdate++;

      speedSum += corrected_speed;
      speedCount++;

      bool earlyTermination = (totalDataCount >= 5 && dataCountSincePeakUpdate >= PEAK_UPDATE_THRESHOLD);

      // 스윙 종료 조건
      if (corrected_speed <= 0.01 || earlyTermination) {
        if (!peakSent) {  // 중복 전송 방지
          int ipeak = (int)(peakSpeed * 1000);
          int iavg  = abs((speedCount > 0) ? (int)((speedSum / speedCount) * 1000) : 0);

          Serial.print("피크 속도 ");
          Serial.println(ipeak);
          Serial.print("평균 속도 ");
          Serial.println(iavg);

          if (deviceConnected) {
            if (iavg < 1000 && iavg > 200) {
              String combined = String(ipeak) + "|" + String(iavg);
              speedCharacteristic.writeValue(combined.c_str());
              Serial.print("BLE 전송 ");
              Serial.println(combined);
            } else {
              Serial.print("평균값 너무 높음 ");
              Serial.println(iavg);
            }
            statusCharacteristic.writeValue("Ready");
          }
          peakSent = true;
        }

        // 스윙 종료 다음 스윙은 백스윙에서만 가능
        isMoving = false;
        peakSpeed = 0.0;
        speedSum = 0.0;
        speedCount = 0;
        dataCountSincePeakUpdate = 0;
        totalDataCount = 0;
        speedBuffer.reset();
      }
    }
  }
}
