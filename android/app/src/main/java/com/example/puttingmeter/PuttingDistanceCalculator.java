package com.example.puttingmeter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 퍼팅 비거리 계산 유틸리티.
 * - 퍼터 헤드 속도와 보정 단계(그린스피드)에 기반하여 공의 예상 비거리를 계산한다.
 */
public class PuttingDistanceCalculator {

    /** 원주율 */
    private static final double M_PI = 3.14159265358979323846;
    /** 경사각도(도) */
    private static final double THETA = 20.0;
    /** 중력가속도 (m/s^2) */
    private static final double GRAVITY = 9.8;
    /** 경사 실험용 질량 (kg) */
    private static final double SLOPE_MASS = 0.09;
    /** 경사면 길이 (m) */
    private static final double SLOPE_LENGTH = 0.76;
    /** 골프공 질량 (kg) */
    private static final double BALL_MASS = 0.045;

    /** 퍼터 가벼운 헤드 질량 (kg) */
    public static final double PUTTER_MASS_LIGHT = 0.36;
    /** 퍼터 무거운 헤드 질량 (kg) */
    public static final double PUTTER_MASS_HEAVY = 0.45;
    /** 반발계수(e) */
    public static final double COEFFICIENT_OF_RESTITUTION = 0.8;

    /** 현재 선택된 퍼터 헤드 질량(kg) */
    private static double selectedPutterMassKg = PUTTER_MASS_LIGHT;

    /** 캘리브레이션: 실험적 거리 보정 배수 (전역) */
    private static double CALIBRATION_FACTOR = 35.0;
    private static final String PREFS_NAME = "putting_prefs";
    private static final String KEY_CALIBRATION = "calibration_factor";

    /**
     * 퍼터 헤드 질량을 설정한다.
     * @param massKg 설정할 퍼터 헤드 질량(kg)
     */
    public static void setSelectedPutterMassKg(double massKg) {
        selectedPutterMassKg = massKg;
    }

    /**
     * 예상 비거리(m)를 계산한다.
     * @param velocityMmPerSec 퍼터 평균 속도(mm/s)
     * @param correctionStep 보정 단계(1~10)
     * @return 비거리(m)
     */
    public static double calculateDistance(float velocityMmPerSec, int correctionStep) {
        double putterVelocityMps = velocityMmPerSec / 1000.0;
        double frictionForce = calculateFrictionForce(correctionStep);
        double velocityBallMps = ((1.0 + COEFFICIENT_OF_RESTITUTION)
                * selectedPutterMassKg / (selectedPutterMassKg + BALL_MASS)) * putterVelocityMps;
        double kineticEnergy = 0.5 * BALL_MASS * velocityBallMps * velocityBallMps;
        double rawDistance = kineticEnergy / frictionForce;
        return rawDistance * CALIBRATION_FACTOR;
    }

    /** 비거리 계산 디버그 로그 출력 */
    public static void debugCalculation(float velocityMmPerSec, int correctionStep) {
        double putterVelocityMps = velocityMmPerSec / 1000.0;
        double frictionForce = calculateFrictionForce(correctionStep);
        double velocityBallMps = ((1.0 + COEFFICIENT_OF_RESTITUTION)
                * selectedPutterMassKg / (selectedPutterMassKg + BALL_MASS)) * putterVelocityMps;
        double kineticEnergy = 0.5 * BALL_MASS * velocityBallMps * velocityBallMps;
        double green = getGreenSpeedForStep(correctionStep);
        double distanceRaw = kineticEnergy / frictionForce;
        double distanceFinal = distanceRaw * CALIBRATION_FACTOR;

        Log.d("비거리디버그", "=== DEBUG ===");
        Log.d("비거리디버그", "Putter velocity: " + putterVelocityMps + " m/s");
        Log.d("비거리디버그", "Ball velocity: " + velocityBallMps + " m/s");
        Log.d("비거리디버그", "Kinetic energy: " + kineticEnergy + " J");
        Log.d("비거리디버그", "Friction force: " + frictionForce + " N");
        Log.d("비거리디버그", "Green speed: " + green + " m");
        Log.d("비거리디버그", "Distance (raw): " + distanceRaw + " m");
        Log.d("비거리디버그", "Calibration factor: " + CALIBRATION_FACTOR);
        Log.d("비거리디버그", "Distance (final): " + distanceFinal + " m");
    }

    /**
     * 보정 단계(그린스피드)로부터 마찰력을 추정한다.
     * @param correctionStep 보정 단계(1~10)
     * @return 마찰력(N)
     */
    private static double calculateFrictionForce(int correctionStep) {
        // 그린스피드에서 마찰계수를 직접 계산
        double greenSpeedMeters = getGreenSpeedForStep(correctionStep);
        double thetaRad = THETA * M_PI / 180.0;

        // 경사면에서의 초기 위치에너지
        double potentialEnergy = SLOPE_MASS * GRAVITY * SLOPE_LENGTH * Math.sin(thetaRad);

        // 마찰력 = 위치에너지 / 실제굴러간거리
        return potentialEnergy / greenSpeedMeters;
    }

    /**
     * 단계→그린스피드(m) 매핑(선형). 단계↑ → 거리↑ (2.0m → 3.5m)
     * @param correctionStep 보정 단계(1~10)
     * @return 그린스피드(m)
     */
    public static double getGreenSpeedForStep(int correctionStep) {
        int step = Math.max(1, Math.min(10, correctionStep));
        double minMeters = 2.0;
        double maxMeters = 3.5;
        double t = (step - 1) / 9.0;
        return minMeters + t * (maxMeters - minMeters);
    }

    /**
     * 단계에 해당하는 그린스피드를 "x.xm" 형식으로 반환한다.
     * @param correctionStep 보정 단계(1~10)
     * @return 포맷 문자열
     */
    public static String getGreenSpeedText(int correctionStep) {
        return String.format(java.util.Locale.getDefault(), "%.1fm", getGreenSpeedForStep(correctionStep));
    }

    /** 현재 캘리브레이션 팩터 반환 */
    public static double getCalibrationFactor() {
        return CALIBRATION_FACTOR;
    }

    /**
     * 캘리브레이션 팩터 설정(메모리만 갱신)
     * @param factor 배수(예: 3.5)
     */
    public static void setCalibrationFactor(double factor) {
        CALIBRATION_FACTOR = factor;
    }

    /**
     * 캘리브레이션 팩터 저장(영구 저장)
     */
    public static void saveCalibrationFactor(Context context, double factor) {
        CALIBRATION_FACTOR = factor;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putFloat(KEY_CALIBRATION, (float) factor).apply();
    }

    /**
     * 캘리브레이션 팩터 로드(없으면 기본값 유지)
     */
    public static void loadCalibrationFactor(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (sp.contains(KEY_CALIBRATION)) {
            CALIBRATION_FACTOR = sp.getFloat(KEY_CALIBRATION, (float) CALIBRATION_FACTOR);
        }
    }

    /**
     * 레거시 호환: 그린스피드 값을 단계로 변환하여 비거리를 계산한다.
     * @param velocityMmPerSec 퍼터 평균 속도(mm/s)
     * @param greenSpeed 레거시 그린스피드 값(1.5~3.5)
     * @return 비거리(m)
     * @deprecated 단계 기반 API 사용 권장
     */
    @Deprecated
    public static double calculateDistance(float velocityMmPerSec, float greenSpeed) {
        int correctionStep = Math.round((greenSpeed - 1.5f) / 0.1f) + 1;
        correctionStep = Math.max(1, Math.min(20, correctionStep));
        return calculateDistance(velocityMmPerSec, correctionStep);
    }

    /**
     * 단계 구간에 따른 설명 텍스트를 반환한다.
     * @param correctionStep 보정 단계
     * @return "느린/보통/빠른 그린"
     */
    public static String getCorrectionDescription(int correctionStep) {
        if (correctionStep <= 2) {return "느린 그린";}
        else if (correctionStep <= 7) {return "보통 그린";}
        else {return "빠른 그린";}
    }

    /**
     * 단계 구간에 따른 색상 리소스 반환.
     * @param correctionStep 보정 단계
     * @return 원형 색상 drawable 리소스 ID
     */
    public static int getCorrectionColor(int correctionStep) {
        if (correctionStep <= 2) {return R.drawable.circle_blue;}
        else if (correctionStep <= 7) {return R.drawable.circle_green;}
        else {return R.drawable.circle_red;}
    }
}